package com.aiassistant.di

import com.aiassistant.data.network.ApiClient
import com.aiassistant.data.network.model.ChatMessage
import com.aiassistant.data.network.model.ChatMessageSerializer
import com.aiassistant.data.network.model.ChatRequest
import com.aiassistant.data.network.model.ChatRequestSerializer
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    @Suppress("DEPRECATION")
    fun provideGson(): Gson = GsonBuilder()
        .setLenient()
        .registerTypeAdapter(ChatRequest::class.java, ChatRequestSerializer())
        .registerTypeAdapter(ChatMessage::class.java, ChatMessageSerializer())
        .create()
}
