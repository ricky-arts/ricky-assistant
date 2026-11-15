package com.xingjian.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xingjian.assistant.ui.navigation.AppNav
import com.xingjian.assistant.ui.theme.AppBackground
import com.xingjian.assistant.ui.theme.XingJianTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as XjApplication).container
        setContent {
            val themeMode by container.settings.themeMode.collectAsStateWithLifecycle()
            XingJianTheme(themeMode = themeMode) {
                AppBackground(settings = container.settings) {
                    AppNav(container = container)
                }
            }
        }
    }
}
