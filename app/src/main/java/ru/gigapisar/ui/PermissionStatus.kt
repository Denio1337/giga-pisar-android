package ru.gigapisar.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import ru.gigapisar.service.GigaPisarAccessibilityService

internal fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val manager =
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

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
