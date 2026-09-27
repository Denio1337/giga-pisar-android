package ru.gigapisar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.gigapisar.R
import ru.gigapisar.settings.InsertionMode

@Composable
internal fun InsertionModeCard(
    selectedMode: InsertionMode,
    onModeSelected: (InsertionMode) -> Unit,
) {
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = context.getString(R.string.insertion_mode),
                style = MaterialTheme.typography.titleMedium,
            )
            ModeRow(
                title = context.getString(R.string.mode_clipboard),
                selected = selectedMode == InsertionMode.CLIPBOARD,
                onClick = { onModeSelected(InsertionMode.CLIPBOARD) },
            )
            ModeRow(
                title = context.getString(R.string.mode_text_field),
                selected = selectedMode == InsertionMode.TEXT_FIELD,
                onClick = { onModeSelected(InsertionMode.TEXT_FIELD) },
            )
        }
    }
}
