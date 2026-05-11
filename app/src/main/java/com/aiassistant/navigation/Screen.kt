package com.aiassistant.navigation

sealed class Screen(val route: String, val title: String) {
    data object Chat : Screen(route = "chat", title = "对话")
    data object Ocr : Screen(route = "ocr", title = "图文识别")
    data object Transcription : Screen(route = "transcription", title = "录音转文字")
    data object Todo : Screen(route = "todo", title = "待办")
    data object Roleplay : Screen(route = "roleplay", title = "角色扮演")
    data object Settings : Screen(route = "settings", title = "设置")
    data object Password : Screen(route = "password", title = "密码本")
    data object Log : Screen(route = "log", title = "日志")
    companion object {
        val bottomNavItems = listOf(Chat, Ocr, Transcription, Todo, Password, Log, Settings)

        val configurableNavItems = listOf(
            "chat" to "对话",
            "ocr" to "图文识别",
            "transcription" to "录音转文字",
            "todo" to "待办",
            "roleplay" to "角色扮演",
            "password" to "密码本",
            "log" to "日志",
            "settings" to "设置"
        )
    }
}
