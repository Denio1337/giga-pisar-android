package ru.gigapisar.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import ru.gigapisar.service.GigaPisarAccessibilityService

/**
 * Whether the user switched the service on in Settings. The list of running services is
 * not enough: right after an app update Android restarts the service, and for a moment it
 * is missing from that list, so the setup wizard flashed although everything was on.
 */
internal fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val ours = ComponentName(context, GigaPisarAccessibilityService::class.java)
    val enabled =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: ""
    if (enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }) return true

    // Some firmwares keep the setting elsewhere; the running list still counts.
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    return manager
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { info ->
            info.resolveInfo.serviceInfo.packageName == context.packageName &&
                info.resolveInfo.serviceInfo.name == GigaPisarAccessibilityService::class.java.name
        }
}

internal fun isMicrophonePermissionGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED
