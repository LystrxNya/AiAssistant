package com.aiassistant

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.aiassistant.data.EncryptedPrefsManager
import com.aiassistant.navigation.AppNavigation
import com.aiassistant.ui.theme.AiAssistantTheme
import com.aiassistant.ui.theme.BackgroundLight
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var encryptedPrefsManager: EncryptedPrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiAssistantTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BackgroundLight
                ) {
                    AppNavigation(encryptedPrefsManager = encryptedPrefsManager)
                }
            }
        }
    }
}
