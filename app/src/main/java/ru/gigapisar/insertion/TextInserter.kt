package ru.gigapisar.insertion

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import ru.gigapisar.R

class TextInserter(
    private val context: Context,
) {
    private val mainHandler =
        Handler(Looper.getMainLooper())

    fun putToClipboard(text: String) {
        val clipboard =
            context.getSystemService(
                Context.CLIPBOARD_SERVICE,
            ) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                context.getString(R.string.clipboard_label),
                text,
            ),
        )
    }

    fun pasteIntoFocusedField(
        fallbackNode: AccessibilityNodeInfo?,
        text: String,
    ): Boolean {
        putToClipboard(text)

        val node = findFocusedNode() ?: fallbackNode

        node ?: return false

        return try {
            if (node.isEditable &&
                node.isEnabled
            ) {
                val pasted =
                    node.performAction(
                        AccessibilityNodeInfo.ACTION_PASTE,
                    )

                if (pasted) {
                    mainHandler.postDelayed(
                        ::clearClipboard,
                        CLIPBOARD_CLEAR_DELAY_MS,
                    )
                }

                pasted
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun clearClipboard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.clearPrimaryClip()
        }
    }

    private companion object {
        const val CLIPBOARD_CLEAR_DELAY_MS = 250L
    }

    private fun findFocusedNode(): AccessibilityNodeInfo? {
        val service = context as? android.accessibilityservice.AccessibilityService ?: return null
        return try {
            val focused =
                service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    ?: service.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

            if (focused != null && focused.isEditable && focused.isEnabled) {
                focused
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }
}
