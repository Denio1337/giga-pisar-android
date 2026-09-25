package ru.gigapisar.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class OverlayManager(
    private val service: AccessibilityService,
    onRecordingStart: () -> Unit,
    onRecordingStop: () -> Unit,
) {
    private val density =
        service.resources.displayMetrics.density

    private val buttonSize =
        (64 * density).toInt()

    private val margin =
        (8 * density).toInt()

    private val button =
        RecordingButton(service)

    private val windowManager =
        service.getSystemService(
            WindowManager::class.java,
        )

    private val params =
        WindowManager
            .LayoutParams(
                buttonSize,
                buttonSize,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity =
                    Gravity.TOP or Gravity.START
            }

    private var attached = false

    private var clipboardMode = false

    init {
        button.onRecordingStart =
            onRecordingStart

        button.onRecordingStop =
            onRecordingStop
    }

    fun attach() {
        if (attached) {
            return
        }

        try {
            windowManager.addView(
                button,
                params,
            )

            attached = true
        } catch (_: Throwable) {
            attached = false
        }
    }

    fun setClipboardMode() {
        clipboardMode = true

        if (!attached) {
            attach()
        }

        val metrics =
            service.resources.displayMetrics

        params.x =
            metrics.widthPixels -
            buttonSize -
            margin

        params.y =
            metrics.heightPixels / 2 -
            buttonSize / 2

        button.visibility =
            View.VISIBLE

        updateLayout()
    }

    fun setTextFieldMode(bounds: Rect?) {
        clipboardMode = false

        if (!attached) {
            attach()
        }

        if (bounds == null) {
            button.visibility =
                View.GONE
            return
        }

        val metrics =
            service.resources.displayMetrics

        val centerX =
            bounds.left +
                bounds.width() / 2

        params.x =
            (
                centerX -
                    buttonSize / 2
            ).coerceIn(
                margin,
                metrics.widthPixels -
                    buttonSize -
                    margin,
            )

        val above =
            bounds.top -
                buttonSize -
                margin

        val below =
            bounds.bottom +
                margin

        params.y =
            if (above >= margin) {
                above
            } else {
                below
            }.coerceIn(
                margin,
                metrics.heightPixels -
                    buttonSize -
                    margin,
            )

        button.visibility =
            View.VISIBLE

        updateLayout()
    }

    fun hide() {
        if (clipboardMode) {
            return
        }

        button.visibility =
            View.GONE
    }

    fun setIdle() {
        button.setState(
            RecordingButton.State.IDLE,
        )
    }

    fun setRecording() {
        button.setState(
            RecordingButton.State.RECORDING,
        )
    }

    fun setProcessing() {
        button.setState(
            RecordingButton.State.PROCESSING,
        )
    }

    fun remove() {
        if (!attached) {
            return
        }

        try {
            windowManager.removeView(button)
        } catch (_: Throwable) {
        }

        attached = false
    }

    private fun updateLayout() {
        if (!attached) {
            return
        }

        try {
            windowManager.updateViewLayout(
                button,
                params,
            )
        } catch (_: Throwable) {
        }
    }
}
