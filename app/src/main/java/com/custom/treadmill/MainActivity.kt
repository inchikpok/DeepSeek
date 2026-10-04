package com.custom.treadmill

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.custom.treadmill.ui.AppNavHost
import com.custom.treadmill.ui.TreadmillTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TreadmillTheme {
                AppNavHost()
            }
        }
    }
}
