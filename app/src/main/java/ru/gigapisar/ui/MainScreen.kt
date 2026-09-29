package ru.gigapisar.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
    var volumeKeyEnabled by remember { mutableStateOf(true) }
    var vibrationEnabled by remember { mutableStateOf(true) }
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
        SettingsRepository.insertionMode(context).collect { insertionMode = it }
    }
    LaunchedEffect(context) {
        SettingsRepository.virtualButtonVisible(context).collect { virtualButtonVisible = it }
    }
    LaunchedEffect(context) {
        SettingsRepository.volumeKeyEnabled(context).collect { volumeKeyEnabled = it }
    }
    LaunchedEffect(context) {
        SettingsRepository.vibrationEnabled(context).collect { vibrationEnabled = it }
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
                        // Before the scroll, so the list slides under a fixed edge, not under the clock.
                        .statusBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp),
            ) {
                Header()
                if (!(microphoneGranted && accessibilityEnabled && modelInstalled)) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SetupWizard(
                            microphoneGranted = microphoneGranted,
                            accessibilityEnabled = accessibilityEnabled,
                            onRequestMicrophone = requestMicrophone,
                            onOpenAccessibilitySettings = openAccessibilitySettings,
                            onModelInstalled = { modelInstalled = true },
                        )
                    }
                    return@Column
                }
                ReadyCard(
                    volumeKeyEnabled = volumeKeyEnabled,
                    buttonEnabled = virtualButtonVisible,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                SettingsList(
                    insertionMode = insertionMode,
                    virtualButtonVisible = virtualButtonVisible,
                    volumeKeyEnabled = volumeKeyEnabled,
                    vibrationEnabled = vibrationEnabled,
                    microphoneGranted = microphoneGranted,
                    accessibilityEnabled = accessibilityEnabled,
                    onInsertionMode = { scope.launch { SettingsRepository.setInsertionMode(context, it) } },
                    onVirtualButton = { scope.launch { SettingsRepository.setVirtualButtonVisible(context, it) } },
                    onVolumeKey = { scope.launch { SettingsRepository.setVolumeKeyEnabled(context, it) } },
                    onVibration = { scope.launch { SettingsRepository.setVibrationEnabled(context, it) } },
                    onRequestMicrophone = requestMicrophone,
                    onOpenAccessibilitySettings = openAccessibilitySettings,
                )
            }
        }
    }
}

/** App name with the version next to it, small and grey: handy when testers report bugs. */
@Composable
private fun Header() {
    val context = LocalContext.current
    val version =
        remember(context) {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (_: Exception) {
                null
            }
        }
    Row(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.alignByBaseline(),
        )
        version?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}
