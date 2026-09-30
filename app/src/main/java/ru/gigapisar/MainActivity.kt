package ru.gigapisar

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.gigapisar.brain.brainRussian
import ru.gigapisar.settings.AppLanguage
import ru.gigapisar.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        // The interface language picked in the settings, or the phone's own.
        super.attachBaseContext(AppLanguage.wrap(newBase))
        brainRussian = AppLanguage.isRussian(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Status bar icons follow the phone theme: dark on the light screen, light on the dark one.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen(this)
        }
    }
}
