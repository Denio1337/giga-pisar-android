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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.gigapisar.R
import ru.gigapisar.model.ModelManager

@Composable
internal fun ModelCard(
    modelInstalled: Boolean,
    onModelInstalled: () -> Unit,
) {
    val context = LocalContext.current
    val modelManager = remember(context) { ModelManager(context) }
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = context.getString(R.string.model_name),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(text = context.getString(R.string.model_description))
            Text(
                text =
                    context.getString(
                        if (modelInstalled) {
                            R.string.model_installed
                        } else {
                            R.string.model_not_installed
                        },
                    ),
            )
            if (!modelInstalled && !downloading) {
                Button(
                    onClick = {
                        downloading = true
                        progress = 0
                        scope.launch {
                            downloadError = null
                            try {
                                modelManager.download { value -> progress = value }
                                if (!modelManager.isInstalled()) {
                                    throw IllegalStateException(
                                        context.getString(R.string.model_verification_failed),
                                    )
                                }
                                onModelInstalled()
                            } catch (error: Throwable) {
                                downloadError = error.message ?: error.javaClass.simpleName
                            } finally {
                                downloading = false
                            }
                        }
                    },
                ) {
                    Text(context.getString(R.string.download_model))
                }
            }
            if (downloading) {
                Text(text = context.getString(R.string.downloading_model, progress))
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
