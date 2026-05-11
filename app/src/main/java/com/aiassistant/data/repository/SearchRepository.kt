package com.aiassistant.data.repository

import com.aiassistant.data.EncryptedPrefsManager
import com.aiassistant.data.network.ApiClient
import com.aiassistant.data.network.api.ChatApiService
import com.aiassistant.data.network.model.ChatMessage
import com.aiassistant.data.network.model.ChatRequest
import com.aiassistant.data.network.model.PromptTemplates
import com.aiassistant.data.preset.BailianPresetManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class SearchResultItem(
    val title: String,
    val url: String,
    val content: String?
)

@Singleton
class SearchRepository @Inject constructor(
    private val gson: Gson,
    private val encryptedPrefsManager: EncryptedPrefsManager,
    private val apiClient: ApiClient
) {

    private val searchClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    @Volatile
    private var deepSeekService: ChatApiService? = null

    suspend fun search(query: String): Result<String> {
        return when (encryptedPrefsManager.getSearchMode()) {
            EncryptedPrefsManager.SEARCH_MODE_DEEPSEEK -> searchWithDeepSeek(query)
            EncryptedPrefsManager.SEARCH_MODE_THIRD_PARTY -> searchWithThirdParty(query)
            else -> searchWithDeepSeek(query)
        }
    }

    private suspend fun searchWithDeepSeek(query: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = encryptedPrefsManager.getBailianApiKey()
            if (apiKey.isNullOrBlank()) {
                return@withContext Result.failure(Exception("未配置百炼 API Key，无法使用 DeepSeek 搜索"))
            }

            val service = getDeepSeekService(apiKey)

            val messages = listOf(
                ChatMessage(role = "system", content = PromptTemplates.DEEPSEEK_SEARCH),
                ChatMessage(role = "user", content = query)
            )
            val request = ChatRequest(
                model = "deepseek-v4-flash",
                messages = messages,
                stream = false,
                extraBody = mapOf(
                    "enable_search" to true,
                    "reasoning_effort" to "none"
                )
            )

            val response = service.chatCompletions(request)
            val content = response.choices?.firstOrNull()?.message?.content
            if (content is String && content.isNotBlank()) {
                Result.success(content)
            } else {
                Result.failure(Exception("DeepSeek 搜索未返回结果"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("DeepSeek 搜索异常: ${e.message}"))
        }
    }

    private fun getDeepSeekService(apiKey: String): ChatApiService {
        deepSeekService?.let { return it }
        synchronized(this) {
            deepSeekService?.let { return it }
            val service = apiClient.getModelApiService(
                com.aiassistant.data.ModelConfigItem(
                    baseUrl = BailianPresetManager.BAILIAN_BASE_URL,
                    apiKey = apiKey
                )
            )
            deepSeekService = service
            return service
        }
    }

    private suspend fun searchWithThirdParty(query: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val searchResults = callSearchApi(query)
            if (searchResults.isEmpty()) {
                return@withContext Result.failure(Exception("搜索无结果"))
            }

            val contents = mutableListOf<String>()
            for (result in searchResults.take(5)) {
                val content = if (!result.content.isNullOrBlank() && result.content.length > 80) {
                    result.content
                } else {
                    scrapeUrl(result.url) ?: continue
                }
                if (content.isNotBlank()) {
                    contents.add("【${result.title}】(${result.url})\n${content.take(1500)}")
                }
            }

            if (contents.isEmpty()) {
                return@withContext Result.failure(Exception("无法获取搜索结果内容"))
            }

            val concatenated = contents.joinToString("\n\n---\n\n").take(5000)

            val compressed = compressWithAuxModel(concatenated)
                ?: return@withContext Result.failure(Exception("搜索结果压缩失败"))

            Result.success(compressed)
        } catch (e: Exception) {
            Result.failure(Exception("联网搜索异常: ${e.message}"))
        }
    }

    private fun callSearchApi(query: String): List<SearchResultItem> {
        val baseUrl = encryptedPrefsManager.getSearchApiBaseUrl().trimEnd('/')
        val apiKey = encryptedPrefsManager.getSearchApiKey()

        if (baseUrl.isBlank()) return emptyList()

        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl/search?q=$encodedQuery&format=json"

        val requestBuilder = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")

        if (!apiKey.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        val request = requestBuilder.build()
        val rawResponse = searchClient.newCall(request).execute()
        val response = followRedirectsSafely(rawResponse, request)
        response.use { resp ->
            if (!resp.isSuccessful) return emptyList()

            val body = resp.body?.string() ?: return emptyList()

            return try {
                val json = gson.fromJson(body, com.google.gson.JsonObject::class.java)
                val results = json.getAsJsonArray("results") ?: return emptyList()
                results.mapNotNull { element ->
                    val obj = element.asJsonObject
                    val title = obj.get("title")?.asString ?: ""
                    val resultUrl = obj.get("url")?.asString ?: ""
                    val content = obj.get("content")?.asString
                        ?: obj.get("snippet")?.asString
                        ?: obj.get("description")?.asString
                    if (resultUrl.isNotBlank()) {
                        SearchResultItem(title, resultUrl, content)
                    } else null
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    private fun scrapeUrl(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .build()

            val rawResponse = searchClient.newCall(request).execute()
            val response = followRedirectsSafely(rawResponse, request)
            response.use { resp ->
                if (!resp.isSuccessful) return null

                val html = resp.body?.string() ?: return null
                val doc = Jsoup.parse(html)

                doc.select("script, style, nav, header, footer, aside, [class*=ad], [id*=ad], noscript, iframe").remove()

                val body = doc.body() ?: return null
                val text = body.text()?.trim() ?: return null

                if (text.length < 20) null else text.take(2000)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun followRedirectsSafely(
        response: Response,
        originalRequest: Request,
        maxRedirects: Int = 5
    ): Response {
        var currentResponse = response
        var currentRequest = originalRequest
        var redirectsLeft = maxRedirects

        while (redirectsLeft-- > 0) {
            val code = currentResponse.code
            if (code !in 301..308) break

            val location = currentResponse.header("Location") ?: break
            currentResponse.close()

            val originalUrl = currentRequest.url
            val redirectUrl = originalUrl.resolve(location) ?: break
            val sameOrigin = redirectUrl.host == originalUrl.host &&
                redirectUrl.port == originalUrl.port &&
                redirectUrl.scheme == originalUrl.scheme

            val requestBuilder = Request.Builder().url(redirectUrl).get()
            if (sameOrigin) {
                currentRequest.headers.forEach { (name, value) ->
                    if (!name.equals("Host", ignoreCase = true)) {
                        requestBuilder.header(name, value)
                    }
                }
            } else {
                requestBuilder.header("Accept", currentRequest.header("Accept") ?: "*/*")
            }

            currentRequest = requestBuilder.build()
            currentResponse = searchClient.newCall(currentRequest).execute()
        }

        return currentResponse
    }

    private suspend fun compressWithAuxModel(text: String): String? {
        if (!encryptedPrefsManager.isAuxModelConfigured()) return text

        return try {
            val messages = listOf(
                ChatMessage(role = "system", content = PromptTemplates.SEARCH_COMPRESS),
                ChatMessage(role = "user", content = text)
            )
            val request = ChatRequest(
                model = encryptedPrefsManager.getAuxModelName(),
                messages = messages,
                stream = false
            )
            val response = apiClient.sendAuxChatMessage(request)
            (response.choices?.firstOrNull()?.message?.content as? String) ?: text
        } catch (_: Exception) {
            text
        }
    }
}
