package ru.gigapisar

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.gigapisar.model.ModelManager
import ru.gigapisar.settings.InsertionMode
import ru.gigapisar.settings.SettingsRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val context = this

            var insertionMode
                by remember {
                    mutableStateOf(
                        InsertionMode.TEXT_FIELD,
                    )
                }

            var modelInstalled
                by remember {
                    mutableStateOf(
                        ModelManager(context)
                            .isInstalled(),
                    )
                }

            var downloading
                by remember {
                    mutableStateOf(false)
                }

            var progress
                by remember {
                    mutableIntStateOf(0)
                }

            var downloadError
                by remember {
                    mutableStateOf<String?>(null)
                }

            var accessibilityEnabled
                by remember {
                    mutableStateOf(
                        isAccessibilityEnabled(),
                    )
                }

            var microphoneGranted
                by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) ==
                            PackageManager.PERMISSION_GRANTED,
                    )
                }

            val scope =
                rememberCoroutineScope()

            LaunchedEffect(Unit) {
                SettingsRepository
                    .insertionMode(context)
                    .collect { mode ->
                        insertionMode = mode
                    }
            }

            LaunchedEffect(Unit) {
                while (true) {
                    accessibilityEnabled =
                        isAccessibilityEnabled()

                    microphoneGranted =
                        ContextCompat
                            .checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO,
                            ) ==
                        PackageManager.PERMISSION_GRANTED

                    modelInstalled =
                        ModelManager(context)
                            .isInstalled()

                    delay(1000)
                }
            }

            MaterialTheme(
                colorScheme =
                    darkColorScheme(
                        primary =
                            Color(0xFF4CAF50),
                        secondary =
                            Color(0xFF81C784),
                    ),
            ) {
                Surface(
                    modifier =
                        Modifier.fillMaxSize(),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .padding(20.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(18.dp),
                    ) {
                        Text(
                            text = context.getString(R.string.app_name),
                            style =
                                MaterialTheme.typography
                                    .headlineMedium,
                        )

                        Card(
                            modifier =
                                Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier =
                                    Modifier.padding(16.dp),
                                verticalArrangement =
                                    Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = context.getString(R.string.insertion_mode),
                                    style =
                                        MaterialTheme
                                            .typography
                                            .titleMedium,
                                )

                                ModeRow(
                                    title = context.getString(R.string.mode_clipboard),
                                    selected =
                                        insertionMode ==
                                            InsertionMode.CLIPBOARD,
                                    onClick = {
                                        scope.launch {
                                            SettingsRepository
                                                .setInsertionMode(
                                                    context,
                                                    InsertionMode.CLIPBOARD,
                                                )
                                        }
                                    },
                                )

                                ModeRow(
                                    title = context.getString(R.string.mode_text_field),
                                    selected =
                                        insertionMode ==
                                            InsertionMode.TEXT_FIELD,
                                    onClick = {
                                        scope.launch {
                                            SettingsRepository
                                                .setInsertionMode(
                                                    context,
                                                    InsertionMode.TEXT_FIELD,
                                                )
                                        }
                                    },
                                )
                            }
                        }

                        Card(
                            modifier =
                                Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier =
                                    Modifier.padding(16.dp),
                                verticalArrangement =
                                    Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text =
                                        context.getString(R.string.permissions),
                                    style =
                                        MaterialTheme
                                            .typography
                                            .titleMedium,
                                )

                                Text(
                                    text =
                                        context.getString(
                                            R.string.microphone_status,
                                            context.getString(R.string.microphone),
                                            context.getString(
                                                if (microphoneGranted) {
                                                    R.string.permission_granted
                                                } else {
                                                    R.string.permission_denied
                                                },
                                            ),
                                        ),
                                )

                                if (!microphoneGranted) {
                                    Button(
                                        onClick = {
                                            requestPermissions(
                                                arrayOf(
                                                    Manifest.permission.RECORD_AUDIO,
                                                ),
                                                100,
                                            )
                                        },
                                    ) {
                                        Text(
                                            context.getString(R.string.grant_microphone),
                                        )
                                    }
                                }

                                Text(
                                    text =
                                        context.getString(
                                            R.string.accessibility_status,
                                            context.getString(R.string.accessibility_service),
                                            context.getString(
                                                if (accessibilityEnabled) {
                                                    R.string.service_enabled
                                                } else {
                                                    R.string.service_disabled
                                                },
                                            ),
                                        ),
                                )

                                Button(
                                    onClick = {
                                        startActivity(
                                            Intent(
                                                Settings.ACTION_ACCESSIBILITY_SETTINGS,
                                            ),
                                        )
                                    },
                                ) {
                                    Text(
                                        context.getString(R.string.open_accessibility_settings),
                                    )
                                }
                            }
                        }

                        Card(
                            modifier =
                                Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier =
                                    Modifier.padding(16.dp),
                                verticalArrangement =
                                    Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text =
                                        context.getString(R.string.model_name),
                                    style =
                                        MaterialTheme
                                            .typography
                                            .titleMedium,
                                )

                                Text(
                                    text =
                                        context.getString(R.string.model_description),
                                )

                                Text(
                                    text =
                                        if (modelInstalled) {
                                            context.getString(R.string.model_installed)
                                        } else {
                                            context.getString(R.string.model_not_installed)
                                        },
                                )

                                if (!modelInstalled &&
                                    !downloading
                                ) {
                                    Button(
                                        onClick = {
                                            downloading =
                                                true
                                            progress =
                                                0

                                            scope.launch {
                                                downloadError = null

                                                try {
                                                    ModelManager(context).download { value ->
                                                        progress = value
                                                    }

                                                    modelInstalled =
                                                        ModelManager(context).isInstalled()

                                                    if (!modelInstalled) {
                                                        throw IllegalStateException(
                                                            context.getString(R.string.model_verification_failed),
                                                        )
                                                    }
                                                } catch (error: Throwable) {
                                                    downloadError =
                                                        error.message
                                                            ?: error.javaClass.simpleName
                                                } finally {
                                                    downloading = false
                                                }
                                            }
                                        },
                                    ) {
                                        Text(
                                            context.getString(R.string.download_model),
                                        )
                                    }
                                }

                                if (downloading) {
                                    Text(
                                        context.getString(R.string.downloading_model, progress),
                                    )
                                }

                                downloadError?.let { error ->
                                    Text(
                                        text = context.getString(R.string.download_error, error),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val manager =
            getSystemService(
                Context.ACCESSIBILITY_SERVICE,
            ) as AccessibilityManager

        return manager
            .getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
            ).any { info ->
                info.resolveInfo.serviceInfo.packageName ==
                    packageName &&
                    info.resolveInfo.serviceInfo.name ==
                    "ru.gigapisar.service.GigaPisarAccessibilityService"
            }
    }
}

@androidx.compose.runtime.Composable
private fun ModeRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    onClick = onClick,
                ).padding(vertical = 4.dp),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )

        Text(
            text = title,
            modifier =
                Modifier.padding(
                    start = 8.dp,
                ),
        )
    }
}
