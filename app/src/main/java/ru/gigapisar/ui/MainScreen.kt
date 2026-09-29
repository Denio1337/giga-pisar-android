package ru.gigapisar.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import ru.gigapisar.R
import ru.gigapisar.model.ModelManager
import ru.gigapisar.settings.InsertionMode
import ru.gigapisar.settings.SettingsRepository

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
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            microphoneGranted = isGranted
        }
    val requestMicrophone = {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    val openAccessibilitySettings = {
        activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

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

    LifecycleResumeEffect(context) {
        accessibilityEnabled = isAccessibilityServiceEnabled(context)
        microphoneGranted = isMicrophonePermissionGranted(context)
        modelInstalled = ModelManager(context).isInstalled()

        onPauseOrDispose {}
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
                if (!(microphoneGranted && accessibilityEnabled && modelInstalled)) {
                    SetupWizard(
                        microphoneGranted = microphoneGranted,
                        accessibilityEnabled = accessibilityEnabled,
                        onRequestMicrophone = requestMicrophone,
                        onOpenAccessibilitySettings = openAccessibilitySettings,
                        onModelInstalled = { modelInstalled = true },
                    )
                    return@Column
                }
                ReadyCard()
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
                    onRequestMicrophone = requestMicrophone,
                    onOpenAccessibilitySettings = openAccessibilitySettings,
                )
                ModelCard(
                    modelInstalled = modelInstalled,
                    onModelInstalled = { modelInstalled = true },
                )
            }
        }
    }
}
