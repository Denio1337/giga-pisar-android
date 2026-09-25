package ru.gigapisar.insertion

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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

        val root =
            findRootNode()

        val focused =
            root?.let {
                findFocusedEditable(it)
            }

        val node =
            focused ?: fallbackNode

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
        val clipboard =
            context.getSystemService(
                Context.CLIPBOARD_SERVICE,
            ) as ClipboardManager

        clipboard.clearPrimaryClip()
    }

    private companion object {
        const val CLIPBOARD_CLEAR_DELAY_MS = 250L
    }

    private fun findRootNode(): AccessibilityNodeInfo? =
        try {
            val service =
                context as? android.accessibilityservice.AccessibilityService

            service?.rootInActiveWindow
        } catch (_: Throwable) {
            null
        }

    private fun findFocusedEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (
            node.isEditable &&
            node.isEnabled &&
            node.isFocused
        ) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child =
                try {
                    node.getChild(i)
                } catch (_: Throwable) {
                    null
                } ?: continue

            val result =
                findFocusedEditable(child)

            if (result != null) {
                return result
            }
        }

        return null
    }
}
