package ru.gigapisar.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import ru.gigapisar.brain.BrainProvider
import ru.gigapisar.brain.BrainProviders
import ru.gigapisar.brain.KeyVault
import ru.gigapisar.settings.SettingsRepository

/** The Brain row in the main settings list: its state at a glance, opens the Brain page. */
@Composable
internal fun BrainRow(
    brain: SettingsRepository.BrainSettings,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    val provider = BrainProviders.byId(brain.providerId)
    val ready = provider != null && brain.model != null && KeyVault.load(context, provider.id) != null
    SectionTitle(R.string.section_brain)
    ListItem(
        headlineContent = { Text(stringResource(R.string.brain_switch)) },
        supportingContent = {
            Text(
                when {
                    !ready -> stringResource(R.string.brain_row_none)
                    brain.enabled -> stringResource(R.string.brain_row_on, provider!!.name, brain.model!!)
                    else -> stringResource(R.string.brain_row_off, provider!!.name)
                },
                color = if (ready && brain.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = { Icon(Icons.Outlined.Psychology, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onOpen),
    )
}

private sealed interface Status {
    data object Idle : Status

    data object Checking : Status

    data class Ok(
        val text: String,
    ) : Status

    data class Failed(
        val text: String,
    ) : Status
}

/**
 * The Brain page, like a page of the system Settings: switches on top, then the saved
 * services (one key each, switch between them with a tap), the models of the active one,
 * and a field to add a key. Every change is checked with a real one-word request.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BrainScreen(
    brain: SettingsRepository.BrainSettings,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf(KeyVault.savedProviders(context)) }
    val active = BrainProviders.byId(brain.providerId)?.takeIf { it in saved }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var status by remember { mutableStateOf<Status>(Status.Idle) }
    var newKey by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf<BrainProvider?>(null) }
    val ready = active != null && brain.model != null

    // Models of the active service, fetched when the page opens or the service changes.
    LaunchedEffect(active?.id) {
        models = emptyList()
        val p = active ?: return@LaunchedEffect
        val key = KeyVault.load(context, p.id) ?: return@LaunchedEffect
        models =
            withContext(Dispatchers.IO) {
                try {
                    BrainProviders.chatModels(BrainClient.listModels(p, key))
                } catch (_: Exception) {
                    emptyList()
                }
            }
    }

    /** Checks [provider] with [model] (or a sensible one) and makes it the active service. */
    fun activate(
        provider: BrainProvider,
        key: String,
        model: String?,
        saveKey: Boolean,
    ) {
        status = Status.Checking
        scope.launch {
            try {
                val picked =
                    withContext(Dispatchers.IO) {
                        val want =
                            model ?: run {
                                val list =
                                    try {
                                        BrainProviders.chatModels(BrainClient.listModels(provider, key))
                                    } catch (_: BrainException) {
                                        // Some services do not list models; the preferred one may still answer.
                                        emptyList()
                                    }
                                BrainProviders.pickDefault(provider, list)
                            } ?: throw BrainException(context.getString(R.string.brain_no_model))
                        BrainClient.probe(provider, key, want)
                        want
                    }
                if (saveKey) {
                    KeyVault.save(context, provider.id, key)
                    saved = KeyVault.savedProviders(context)
                    newKey = ""
                    chosen = null
                }
                SettingsRepository.setBrainService(context, provider.id, picked)
                status = Status.Ok(context.getString(R.string.brain_check_ok, provider.name, picked))
            } catch (e: BrainException) {
                status = Status.Failed(e.message ?: "")
            } catch (e: Exception) {
                status = Status.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun forget(provider: BrainProvider) {
        KeyVault.clear(context, provider.id)
        saved = KeyVault.savedProviders(context)
        scope.launch {
            SettingsRepository.forgetBrainService(context, provider.id)
            if (provider == active) {
                val next = saved.firstOrNull()
                if (next == null) {
                    SettingsRepository.setBrainEnabled(context, false)
                    SettingsRepository.setBrainService(context, null, null)
                } else {
                    SettingsRepository.setBrainService(context, next.id, brain.modelsByProvider[next.id])
                }
            }
        }
        status = Status.Idle
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(stringResource(R.string.brain_switch), style = MaterialTheme.typography.titleLarge)
        }

        StatusCard(status = status, ready = ready, active = active, model = brain.model)

        SwitchItem(
            icon = Icons.Outlined.Psychology,
            title = R.string.brain_switch,
            subtitle = R.string.brain_switch_hint,
            checked = ready && brain.enabled,
            enabled = ready,
            onChange = { on -> scope.launch { SettingsRepository.setBrainEnabled(context, on) } },
        )
        SwitchItem(
            icon = Icons.Outlined.AutoFixHigh,
            title = R.string.brain_every_take,
            subtitle = R.string.brain_every_take_hint,
            checked = ready && brain.enabled && brain.everyTake,
            enabled = ready && brain.enabled,
            onChange = { on -> scope.launch { SettingsRepository.setBrainEveryTake(context, on) } },
        )

        if (saved.isNotEmpty()) {
            SectionTitle(R.string.brain_services)
            for (p in saved) {
                ListItem(
                    headlineContent = { Text(p.name) },
                    supportingContent = {
                        Text(brain.modelsByProvider[p.id] ?: stringResource(R.string.brain_key_saved))
                    },
                    leadingContent = { RadioButton(selected = p == active, onClick = null) },
                    trailingContent = {
                        IconButton(onClick = { forget(p) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.brain_forget))
                        }
                    },
                    modifier =
                        Modifier.selectable(selected = p == active, role = Role.RadioButton) {
                            if (p != active && status != Status.Checking) {
                                val key = KeyVault.load(context, p.id) ?: return@selectable
                                activate(p, key, brain.modelsByProvider[p.id], saveKey = false)
                            }
                        },
                )
            }
        }

        if (active != null && models.size > 1) {
            SectionTitle(R.string.brain_models)
            for (m in models.take(30)) {
                ListItem(
                    headlineContent = { Text(m) },
                    leadingContent = { Icon(Icons.Outlined.Memory, contentDescription = null) },
                    trailingContent = { RadioButton(selected = m == brain.model, onClick = null) },
                    modifier =
                        Modifier.selectable(selected = m == brain.model, role = Role.RadioButton) {
                            if (m != brain.model && status != Status.Checking) {
                                val key = KeyVault.load(context, active.id) ?: return@selectable
                                activate(active, key, m, saveKey = false)
                            }
                        },
                )
            }
        }

        SectionTitle(if (saved.isEmpty()) R.string.brain_key else R.string.brain_add_key)
        val guessed = BrainProviders.fromKey(newKey)
        val target = if (newKey.isNotBlank()) guessed ?: chosen else null
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = newKey,
                onValueChange = {
                    newKey = it
                    if (status is Status.Failed) status = Status.Idle
                },
                label = { Text(stringResource(R.string.brain_key_placeholder)) },
                leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                newKey.isBlank() ->
                    Text(
                        stringResource(R.string.brain_key_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                guessed != null ->
                    Text(
                        stringResource(R.string.brain_guessed, guessed.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                else -> {
                    Text(stringResource(R.string.brain_pick_service), style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (p in BrainProviders.all) {
                            FilterChip(selected = chosen == p, onClick = { chosen = p }, label = { Text(p.name) })
                        }
                    }
                }
            }
            Button(
                onClick = { target?.let { activate(it, newKey.trim(), null, saveKey = true) } },
                enabled = target != null && status != Status.Checking,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(stringResource(R.string.brain_check)) }
            Text(
                text = stringResource(R.string.brain_where_keys),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier.clickable {
                        openUrl(context, (target ?: active ?: BrainProviders.DeepSeek).keysUrl)
                    },
            )
        }

        Text(
            text = stringResource(R.string.brain_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp),
        )
    }
}

@Composable
private fun StatusCard(
    status: Status,
    ready: Boolean,
    active: BrainProvider?,
    model: String?,
) {
    val (icon, title, text, error) =
        when (status) {
            is Status.Failed -> Quad(Icons.Outlined.ErrorOutline, stringResource(R.string.brain_status_failed), status.text, true)
            is Status.Ok -> Quad(Icons.Outlined.CheckCircle, stringResource(R.string.brain_status_ok), status.text, false)
            Status.Checking -> Quad(null, stringResource(R.string.brain_status_checking), "", false)
            Status.Idle ->
                if (ready) {
                    Quad(Icons.Outlined.CheckCircle, stringResource(R.string.brain_status_ready), "${active!!.name}, $model", false)
                } else {
                    Quad(
                        Icons.Outlined.Key,
                        stringResource(R.string.brain_status_none),
                        stringResource(R.string.brain_status_none_text),
                        false,
                    )
                }
        }
    // Green only when something works; red on failure; neutral while there is nothing to say yet.
    val neutral = status == Status.Checking || (status == Status.Idle && !ready)
    val container =
        when {
            error -> MaterialTheme.colorScheme.errorContainer
            neutral -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.primaryContainer
        }
    val content =
        when {
            error -> MaterialTheme.colorScheme.onErrorContainer
            neutral -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null)
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = content)
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private data class Quad(
    val icon: ImageVector?,
    val title: String,
    val text: String,
    val error: Boolean,
)

@Composable
private fun SwitchItem(
    icon: ImageVector,
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
