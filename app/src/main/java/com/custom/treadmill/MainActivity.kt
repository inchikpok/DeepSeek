package com.custom.treadmill

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.custom.treadmill.data.repository.ThemeMode
import com.custom.treadmill.ui.AppNavHost
import com.custom.treadmill.ui.TreadmillTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by TreadmillApp.instance.settingsStore.settings.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            TreadmillTheme(darkTheme = darkTheme) {
                AppNavHost()
            }
        }
    }
}
