package ru.gigapisar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.gigapisar.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Status bar icons follow the phone theme: dark on the light screen, light on the dark one.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen(this)
        }
    }
}
