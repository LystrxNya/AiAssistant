package com.aiassistant.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import com.aiassistant.ui.common.EmphasizedDecelerate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Task
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Task
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiassistant.data.EncryptedPrefsManager
import com.aiassistant.data.EncryptedPrefsManager.Companion.DEFAULT_NAV_ITEMS
import com.aiassistant.ui.chat.ChatScreen
import com.aiassistant.ui.log.LogScreen
import com.aiassistant.ui.ocr.OcrScreen
import com.aiassistant.ui.password.PasswordScreen
import com.aiassistant.ui.roleplay.RoleplayScreen
import com.aiassistant.ui.settings.SettingsScreen
import com.aiassistant.ui.theme.BackgroundLight
import com.aiassistant.ui.theme.PrimaryBlue
import com.aiassistant.ui.theme.SurfaceLight
import com.aiassistant.ui.theme.TextSecondary
import com.aiassistant.ui.todo.TodoScreen
import com.aiassistant.ui.transcription.TranscriptionScreen

private val ROUTE_TITLES = mapOf(
    "chat" to "对话",
    "ocr" to "图文识别",
    "transcription" to "转写",
    "todo" to "待办",
    "roleplay" to "角色扮演",
    "password" to "密码本",
    "log" to "日志",
    "settings" to "设置"
)

private val SELECTED_ICONS = mapOf<String, ImageVector>(
    "chat" to Icons.AutoMirrored.Filled.Chat,
    "ocr" to Icons.Filled.DocumentScanner,
    "transcription" to Icons.Filled.Mic,
    "todo" to Icons.Filled.Task,
    "roleplay" to Icons.Filled.Face,
    "settings" to Icons.Filled.Settings,
    "password" to Icons.Filled.Lock,
    "log" to Icons.AutoMirrored.Filled.List
)

private val UNSELECTED_ICONS = mapOf<String, ImageVector>(
    "chat" to Icons.AutoMirrored.Outlined.Chat,
    "ocr" to Icons.Outlined.DocumentScanner,
    "transcription" to Icons.Outlined.Mic,
    "todo" to Icons.Outlined.Task,
    "roleplay" to Icons.Outlined.Face,
    "settings" to Icons.Outlined.Settings,
    "password" to Icons.Outlined.Lock,
    "log" to Icons.AutoMirrored.Outlined.List
)

private val ALL_ROUTES = listOf("chat", "ocr", "transcription", "todo", "roleplay", "password", "log", "settings")

private const val ROUTE_CHAT = "chat"
private const val MAX_NAV_BAR_ITEMS = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    encryptedPrefsManager: EncryptedPrefsManager,
    startRoute: String = ROUTE_CHAT
) {
    var enabledItems by remember { mutableStateOf(encryptedPrefsManager.getEnabledNavItems()) }

    LaunchedEffect(encryptedPrefsManager) {
        encryptedPrefsManager.enabledNavItemsFlow().collect { enabledItems = it }
    }

    val navBarRoutes = ALL_ROUTES.filter { it in enabledItems }
        .ifEmpty { ALL_ROUTES.filter { it in DEFAULT_NAV_ITEMS } }
        .take(MAX_NAV_BAR_ITEMS)

    val hiddenRoutes = ALL_ROUTES.filter { it !in navBarRoutes }
    val hasHiddenRoutes = hiddenRoutes.isNotEmpty()

    var currentRoute by rememberSaveable { mutableStateOf(startRoute) }
    var previousRoute by rememberSaveable { mutableStateOf(ROUTE_CHAT) }
    var showDrawer by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = BackgroundLight,
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceLight,
                tonalElevation = 2.dp
            ) {
                navBarRoutes.forEach { route ->
                    val selected = currentRoute == route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (currentRoute != route) {
                                previousRoute = currentRoute
                                currentRoute = route
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) (SELECTED_ICONS[route] ?: Icons.AutoMirrored.Filled.Chat)
                                else (UNSELECTED_ICONS[route] ?: Icons.AutoMirrored.Outlined.Chat),
                                contentDescription = ROUTE_TITLES[route] ?: route
                            )
                        },
                        label = { Text(text = ROUTE_TITLES[route] ?: route) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryBlue,
                            selectedTextColor = PrimaryBlue,
                            indicatorColor = PrimaryBlue.copy(alpha = 0.12f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                }
                if (hasHiddenRoutes) {
                    NavigationBarItem(
                        selected = false,
                        onClick = { showDrawer = true },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.MoreHoriz,
                                contentDescription = "更多"
                            )
                        },
                        label = { Text(text = "更多") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryBlue,
                            selectedTextColor = PrimaryBlue,
                            indicatorColor = PrimaryBlue.copy(alpha = 0.12f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        BackHandler(enabled = currentRoute != ROUTE_CHAT) {
            previousRoute = currentRoute
            currentRoute = ROUTE_CHAT
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            ChatScreen()
            if (currentRoute == "ocr") OcrScreen()
            if (currentRoute == "transcription") TranscriptionScreen()
            if (currentRoute == "todo") TodoScreen()
            if (currentRoute == "roleplay") RoleplayScreen()
            if (currentRoute == "settings") SettingsScreen()
            if (currentRoute == "password") PasswordScreen(
                onNavigateBack = { currentRoute = previousRoute }
            )
            if (currentRoute == "log") LogScreen(
                onNavigateBack = { currentRoute = previousRoute }
            )
        }

        if (showDrawer) {
            ModalBottomSheet(
                onDismissRequest = { showDrawer = false },
                sheetState = rememberModalBottomSheetState(),
                containerColor = SurfaceLight
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        text = "更多功能",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryBlue,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    LazyColumn {
                        items(hiddenRoutes) { route ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        previousRoute = currentRoute
                                        currentRoute = route
                                        showDrawer = false
                                    }
                                    .padding(vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = UNSELECTED_ICONS[route] ?: Icons.AutoMirrored.Outlined.Chat,
                                    contentDescription = ROUTE_TITLES[route],
                                    tint = TextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = ROUTE_TITLES[route] ?: route,
                                    fontSize = 16.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
