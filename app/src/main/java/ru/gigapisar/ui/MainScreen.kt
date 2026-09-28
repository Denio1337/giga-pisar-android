package ru.gigapisar.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.gigapisar.R
import ru.gigapisar.model.ModelManager
import ru.gigapisar.settings.InsertionMode
import ru.gigapisar.settings.SettingsRepository
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun MainScreen(activity: ComponentActivity) {
    val context = LocalContext.current
    var insertionMode by remember { mutableStateOf(InsertionMode.TEXT_FIELD) }
    var virtualButtonVisible by remember { mutableStateOf(true) }
    var modelInstalled by remember { mutableStateOf(ModelManager(context).isInstalled()) }
    var accessibilityEnabled by remember {
        mutableStateOf(isAccessibilityServiceEnabled(context))
    }
    var microphoneGranted by remember { mutableStateOf(isMicrophonePermissionGranted(context)) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(context) {
        SettingsRepository.insertionMode(context).collect { mode ->
            insertionMode = mode
        }
    }

    LaunchedEffect(context) {
        SettingsRepository.virtualButtonVisible(context).collect { visible ->
            virtualButtonVisible = visible
        }
    }

    LaunchedEffect(context) {
        while (true) {
            accessibilityEnabled = isAccessibilityServiceEnabled(context)
            microphoneGranted = isMicrophonePermissionGranted(context)
            modelInstalled = ModelManager(context).isInstalled()
            delay(1000.milliseconds)
        }
    }

    GigaPisarTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .statusBarsPadding()
                        .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    text = context.getString(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                )
                InsertionModeCard(
                    selectedMode = insertionMode,
                    onModeSelected = { mode ->
                        scope.launch {
                            SettingsRepository.setInsertionMode(context, mode)
                        }
                    },
                )
                VirtualButtonVisibilityCard(
                    visible = virtualButtonVisible,
                    showTextFieldHint = insertionMode == InsertionMode.TEXT_FIELD,
                    onVisibilityChanged = { visible ->
                        scope.launch {
                            SettingsRepository.setVirtualButtonVisible(context, visible)
                        }
                    },
                )
                PermissionsCard(
                    microphoneGranted = microphoneGranted,
                    accessibilityEnabled = accessibilityEnabled,
                    onRequestMicrophone = {
                        activity.requestPermissions(
                            arrayOf(Manifest.permission.RECORD_AUDIO),
                            100,
                        )
                    },
                    onOpenAccessibilitySettings = {
                        activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                )
                ModelCard(
                    modelInstalled = modelInstalled,
                    onModelInstalled = { modelInstalled = true },
                )
            }
        }
    }
}
