package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import com.example.ui.NotepadScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.tts.SpeechManager

class MainActivity : ComponentActivity() {

    private lateinit var speechManager: SpeechManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize SpeechManager
        speechManager = SpeechManager(applicationContext)
        
        // Open preferences to load system setting or user choice for dark mode
        val sharedPrefs = getSharedPreferences("notepad_prefs", Context.MODE_PRIVATE)

        enableEdgeToEdge()
        
        setContent {
            // Read dark theme preference (default to true to provide high-end look, or system)
            val isSystemDark = isSystemInDarkTheme()
            var isDarkTheme by remember { 
                mutableStateOf(sharedPrefs.getBoolean("dark_theme_enabled", isSystemDark)) 
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                NotepadScreen(
                    speechManager = speechManager,
                    isDarkTheme = isDarkTheme,
                    onDarkThemeChange = { isDark ->
                        isDarkTheme = isDark
                        sharedPrefs.edit().putBoolean("dark_theme_enabled", isDark).apply()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechManager.shutdown()
    }
}
