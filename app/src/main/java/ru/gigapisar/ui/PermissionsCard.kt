package ru.gigapisar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.gigapisar.R

@Composable
internal fun PermissionsCard(
    microphoneGranted: Boolean,
    accessibilityEnabled: Boolean,
    onRequestMicrophone: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = context.getString(R.string.permissions),
                style = MaterialTheme.typography.titleMedium,
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
                Button(onClick = onRequestMicrophone) {
                    Text(context.getString(R.string.grant_microphone))
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
            Button(onClick = onOpenAccessibilitySettings) {
                Text(context.getString(R.string.open_accessibility_settings))
            }
        }
    }
}
