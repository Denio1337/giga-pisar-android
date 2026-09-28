package ru.gigapisar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ru.gigapisar.R

@Composable
internal fun VirtualButtonVisibilityCard(
    visible: Boolean,
    showTextFieldHint: Boolean,
    onVisibilityChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = context.getString(R.string.recording_button),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = visible,
                            role = Role.Switch,
                            onValueChange = onVisibilityChanged,
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = context.getString(R.string.show_recording_button),
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = visible,
                    onCheckedChange = null,
                )
            }
            if (showTextFieldHint) {
                Text(
                    text = context.getString(R.string.text_field_button_visibility_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
