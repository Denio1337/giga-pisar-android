package ru.gigapisar.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.gigapisar.R
import ru.gigapisar.brain.BrainClient
import ru.gigapisar.brain.BrainException
import ru.gigapisar.brain.BrainProviders
import ru.gigapisar.brain.KeyVault
import ru.gigapisar.settings.SettingsRepository

/**
 * The cloud Brain, as on the computer: a key for a service, a switch for voice commands
 * ("…Писарь, сделай короче") and a switch to edit every take. Without a checked key the
 * switches stay off: there is nothing to send the text to.
 */
@Composable
internal fun BrainSection(
    brain: SettingsRepository.BrainSettings,
    keySaved: Boolean,
    onKeyChanged: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf(false) }
    val provider = BrainProviders.byId(brain.providerId)
    val ready = keySaved && provider != null && brain.model != null

    SectionTitle(R.string.section_brain)
    ListItem(
        headlineContent = { Text(stringResource(R.string.brain_key)) },
        supportingContent = {
            Text(
                if (ready) {
                    stringResource(R.string.brain_key_ready, provider!!.name, brain.model!!)
                } else {
                    stringResource(R.string.brain_key_none)
                },
            )
        },
        leadingContent = { Icon(Icons.Outlined.Key, contentDescription = null) },
        modifier = Modifier.clickable { dialog = true },
    )
    BrainSwitch(
        icon = Icons.Outlined.Psychology,
        title = R.string.brain_switch,
        subtitle = R.string.brain_switch_hint,
        checked = ready && brain.enabled,
        enabled = ready,
        onChange = { on -> scope.launch { SettingsRepository.setBrainEnabled(context, on) } },
    )
    BrainSwitch(
        icon = Icons.Outlined.AutoFixHigh,
        title = R.string.brain_every_take,
        subtitle = R.string.brain_every_take_hint,
        checked = ready && brain.enabled && brain.everyTake,
        enabled = ready && brain.enabled,
        onChange = { on -> scope.launch { SettingsRepository.setBrainEveryTake(context, on) } },
    )
    Text(
        text = stringResource(R.string.brain_privacy),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 56.dp, end = 16.dp, top = 4.dp),
    )

    if (dialog) {
        BrainKeyDialog(
            brain = brain,
            keySaved = keySaved,
            onDismiss = { dialog = false },
            onChanged = onKeyChanged,
        )
    }
}

@Composable
private fun BrainSwitch(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: Int,
    subtitle: Int,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(subtitle)) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        modifier = Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
    )
}

/** Key entry: the service is guessed from the key, a model is picked, a real request checks it all. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrainKeyDialog(
    brain: SettingsRepository.BrainSettings,
    keySaved: Boolean,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf(BrainProviders.byId(brain.providerId)) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var model by remember { mutableStateOf(brain.model) }
    var modelMenu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    val guessed = BrainProviders.fromKey(key)
    val provider = if (key.isNotBlank()) guessed ?: chosen else chosen

    fun check() {
        val p = provider ?: return
        val k = key.trim().ifEmpty { KeyVault.load(context) ?: "" }
        if (k.isEmpty()) return
        busy = true
        result = null
        scope.launch {
            try {
                val (list, picked) =
                    withContext(Dispatchers.IO) {
                        val list =
                            try {
                                BrainProviders.chatModels(BrainClient.listModels(p, k))
                            } catch (_: BrainException) {
                                // Some services do not list models; the preferred one may still answer.
                                emptyList()
                            }
                        val picked =
                            model?.takeIf { it in list || (list.isEmpty() && p.id == brain.providerId) }
                                ?: BrainProviders.pickDefault(p, list)
                                ?: throw BrainException("не нашлась подходящая модель")
                        BrainClient.probe(p, k, picked)
                        list to picked
                    }
                KeyVault.save(context, k)
                SettingsRepository.setBrainService(context, p.id, picked)
                models = list
                model = picked
                failed = false
                result = context.getString(R.string.brain_check_ok, p.name, picked)
                onChanged()
            } catch (e: BrainException) {
                failed = true
                result = e.message
            } catch (e: Exception) {
                failed = true
                result = e.message ?: e.javaClass.simpleName
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.brain_key)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.brain_key_hint), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = key,
                    onValueChange = {
                        key = it
                        result = null
                    },
                    placeholder = {
                        Text(stringResource(if (keySaved) R.string.brain_key_saved_placeholder else R.string.brain_key_placeholder))
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (key.isNotBlank() && guessed != null) {
                    Text(
                        stringResource(R.string.brain_guessed, guessed.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (key.isNotBlank() || !keySaved) {
                    Text(stringResource(R.string.brain_pick_service), style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (p in BrainProviders.all) {
                            FilterChip(
                                selected = provider == p,
                                onClick = {
                                    chosen = p
                                    result = null
                                },
                                label = { Text(p.name) },
                            )
                        }
                    }
                }
                provider?.let { p ->
                    Text(
                        text = stringResource(R.string.brain_get_key, p.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { openUrl(context, p.keysUrl) },
                    )
                }
                if (models.size > 1 && model != null) {
                    Column {
                        Text(
                            text = stringResource(R.string.brain_model, model!!),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clickable { modelMenu = true }.padding(vertical = 4.dp),
                        )
                        DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                            for (m in models.take(40)) {
                                DropdownMenuItem(
                                    text = { Text(m) },
                                    onClick = {
                                        modelMenu = false
                                        model = m
                                        check()
                                    },
                                )
                            }
                        }
                    }
                }
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
                result?.let {
                    Text(
                        text = it,
                        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { check() },
                enabled = !busy && provider != null && (key.isNotBlank() || keySaved),
            ) { Text(stringResource(R.string.brain_check)) }
        },
        dismissButton = {
            Column {
                if (keySaved && !busy) {
                    TextButton(onClick = {
                        KeyVault.clear(context)
                        scope.launch {
                            SettingsRepository.setBrainEnabled(context, false)
                            SettingsRepository.setBrainService(context, null, null)
                        }
                        onChanged()
                        onDismiss()
                    }) { Text(stringResource(R.string.brain_forget), color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.brain_close)) }
            }
        },
    )
}

private fun openUrl(
    context: Context,
    url: String,
) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: Exception) {
        // No browser: nothing to open it with.
    }
}
