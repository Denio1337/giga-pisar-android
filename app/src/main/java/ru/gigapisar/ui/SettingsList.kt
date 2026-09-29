package ru.gigapisar.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import ru.gigapisar.R
import ru.gigapisar.settings.InsertionMode

private const val SITE_URL = "https://gigapisar.github.io"
private const val SOURCE_URL = "https://github.com/moznoazachem/giga-pisar-android"
private const val DOWNLOAD_URL = "https://github.com/moznoazachem/giga-pisar-android/releases/latest"

/** The settings as one list in the style of the system Settings app. */
@Composable
internal fun SettingsList(
    insertionMode: InsertionMode,
    virtualButtonVisible: Boolean,
    volumeKeyEnabled: Boolean,
    vibrationEnabled: Boolean,
    microphoneGranted: Boolean,
    accessibilityEnabled: Boolean,
    onInsertionMode: (InsertionMode) -> Unit,
    onVirtualButton: (Boolean) -> Unit,
    onVolumeKey: (Boolean) -> Unit,
    onVibration: (Boolean) -> Unit,
    onRequestMicrophone: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    brainSection: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    Column {
        SectionTitle(R.string.section_dictation)
        SwitchRow(
            icon = Icons.Outlined.RadioButtonChecked,
            title = R.string.floating_button,
            subtitle =
                if (insertionMode == InsertionMode.TEXT_FIELD) {
                    R.string.floating_button_text_field
                } else {
                    R.string.floating_button_always
                },
            checked = virtualButtonVisible,
            onChange = onVirtualButton,
        )
        SwitchRow(
            icon = Icons.AutoMirrored.Outlined.VolumeDown,
            title = R.string.volume_key,
            subtitle = R.string.volume_key_hint,
            checked = volumeKeyEnabled,
            onChange = onVolumeKey,
        )
        SwitchRow(
            icon = Icons.Outlined.Vibration,
            title = R.string.vibration,
            subtitle = R.string.vibration_hint,
            checked = vibrationEnabled,
            onChange = onVibration,
        )

        SectionTitle(R.string.section_insertion)
        RadioRow(
            icon = Icons.Outlined.EditNote,
            title = R.string.mode_text_field,
            subtitle = R.string.mode_text_field_hint,
            selected = insertionMode == InsertionMode.TEXT_FIELD,
            onClick = { onInsertionMode(InsertionMode.TEXT_FIELD) },
        )
        RadioRow(
            icon = Icons.Outlined.ContentPaste,
            title = R.string.mode_clipboard,
            subtitle = R.string.mode_clipboard_hint,
            selected = insertionMode == InsertionMode.CLIPBOARD,
            onClick = { onInsertionMode(InsertionMode.CLIPBOARD) },
        )

        brainSection()

        SectionTitle(R.string.section_permissions)
        StatusRow(
            icon = Icons.Outlined.Mic,
            title = R.string.microphone,
            ok = microphoneGranted,
            okText = R.string.permission_granted,
            badText = R.string.permission_denied,
            onClick = if (microphoneGranted) null else onRequestMicrophone,
        )
        StatusRow(
            icon = Icons.Outlined.AccessibilityNew,
            title = R.string.accessibility_service,
            ok = accessibilityEnabled,
            okText = R.string.service_enabled,
            badText = R.string.service_disabled,
            onClick = onOpenAccessibilitySettings,
            chevron = true,
        )

        SectionTitle(R.string.section_recognition)
        InfoRow(
            icon = Icons.Outlined.GraphicEq,
            title = stringResource(R.string.model_name),
            subtitle = stringResource(R.string.model_installed_hint),
        )

        SectionTitle(R.string.section_about)
        InfoRow(
            icon = Icons.Outlined.Language,
            title = stringResource(R.string.about_site),
            subtitle = SITE_URL.removePrefix("https://"),
            onClick = { openUrl(context, SITE_URL) },
        )
        InfoRow(
            icon = Icons.Outlined.Code,
            title = stringResource(R.string.about_source),
            subtitle = stringResource(R.string.about_source_hint),
            onClick = { openUrl(context, SOURCE_URL) },
        )
        InfoRow(
            icon = Icons.Outlined.Share,
            title = stringResource(R.string.about_share),
            onClick = { share(context) },
        )
    }
}

@Composable
internal fun SectionTitle(text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        // Lines up with the row titles: 16 dp padding + 24 dp icon + 16 dp gap.
        modifier = Modifier.padding(start = 56.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: Int,
    subtitle: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(subtitle)) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

@Composable
private fun RadioRow(
    icon: ImageVector,
    title: Int,
    subtitle: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(subtitle)) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    )
}

@Composable
private fun StatusRow(
    icon: ImageVector,
    title: Int,
    ok: Boolean,
    okText: Int,
    badText: Int,
    onClick: (() -> Unit)?,
    chevron: Boolean = false,
) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = {
            Text(
                text = stringResource(if (ok) okText else badText),
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent =
            if (chevron) {
                { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) }
            } else {
                null
            },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
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

private fun share(context: Context) {
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text, DOWNLOAD_URL))
        }
    try {
        context.startActivity(Intent.createChooser(send, null))
    } catch (_: Exception) {
        // Nothing can share text: nothing to do.
    }
}
