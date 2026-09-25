package ru.gigapisar.service

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.gigapisar.R
import ru.gigapisar.audio.AudioRecorder
import ru.gigapisar.insertion.TextInserter
import ru.gigapisar.model.ModelManager
import ru.gigapisar.overlay.OverlayManager
import ru.gigapisar.settings.InsertionMode
import ru.gigapisar.settings.SettingsRepository
import ru.gigapisar.speech.GigaAmOnnxRecognizer

class GigaPisarAccessibilityService : AccessibilityService() {
    private val serviceScope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.Main.immediate,
        )

    private val audioRecorder =
        AudioRecorder()

    private lateinit var modelManager:
        ModelManager

    private lateinit var recognizer:
        GigaAmOnnxRecognizer

    private lateinit var inserter:
        TextInserter

    private lateinit var overlay:
        OverlayManager

    private var insertionMode =
        InsertionMode.TEXT_FIELD

    private var focusedNode:
        AccessibilityNodeInfo? = null

    private var recordingJob: Job? = null

    @Volatile
    private var recording = false

    private val mainHandler =
        Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()

        modelManager =
            ModelManager(this)

        recognizer =
            GigaAmOnnxRecognizer(
                this,
                modelManager,
            )

        inserter =
            TextInserter(this)

        overlay =
            OverlayManager(
                this,
                onRecordingStart = {
                    handleRecordingStart()
                },
                onRecordingStop = {
                    handleRecordingStop()
                },
            )

        overlay.attach()

        serviceScope.launch {
            SettingsRepository
                .insertionMode(this@GigaPisarAccessibilityService)
                .collectLatest { mode ->

                    insertionMode = mode

                    when (mode) {
                        InsertionMode.CLIPBOARD -> {
                            overlay.setClipboardMode()
                        }

                        InsertionMode.TEXT_FIELD -> {
                            updateTextFieldOverlay()
                        }
                    }
                }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        if (
            insertionMode ==
            InsertionMode.CLIPBOARD
        ) {
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
            -> {
                updateTextFieldOverlay(event)
            }
        }
    }

    private fun updateTextFieldOverlay(event: AccessibilityEvent? = null) {
        val node =
            findFocusedEditable(
                event?.source,
            )
                ?: findFocusedEditable(
                    rootInActiveWindow,
                )

        if (node == null) {
            focusedNode = null

            mainHandler.post {
                overlay.hide()
            }

            return
        }

        try {
            focusedNode?.recycle()
        } catch (_: Throwable) {
        }

        focusedNode =
            AccessibilityNodeInfo.obtain(node)

        val bounds =
            Rect()

        node.getBoundsInScreen(bounds)

        mainHandler.post {
            overlay.setTextFieldMode(
                bounds,
            )
        }
    }

    private fun updateTextFieldOverlay() {
        if (
            insertionMode !=
            InsertionMode.TEXT_FIELD
        ) {
            return
        }

        val node =
            findFocusedEditable(
                rootInActiveWindow,
            )

        if (node == null) {
            overlay.hide()
            return
        }

        try {
            focusedNode?.recycle()
        } catch (_: Throwable) {
        }

        focusedNode =
            AccessibilityNodeInfo.obtain(node)

        val bounds =
            Rect()

        node.getBoundsInScreen(bounds)

        overlay.setTextFieldMode(
            bounds,
        )
    }

    private fun findFocusedEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        node ?: return null

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

    private fun handleRecordingStart() {
        if (recording) {
            return
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            showToast(
                getString(
                    R.string.microphone_required,
                ),
            )

            overlay.setIdle()
            return
        }

        if (!modelManager.isInstalled()) {
            showToast(
                getString(
                    R.string.model_required,
                ),
            )

            overlay.setIdle()
            return
        }

        if (!audioRecorder.start()) {
            showToast(
                getString(
                    R.string.audio_record_error,
                ),
            )

            overlay.setIdle()
            return
        }

        recording = true
        overlay.setRecording()
    }

    private fun handleRecordingStop() {
        if (!recording) {
            overlay.setIdle()
            return
        }

        recording = false

        overlay.setProcessing()

        recordingJob?.cancel()

        recordingJob =
            serviceScope.launch(
                Dispatchers.IO,
            ) {
                try {
                    val audio =
                        audioRecorder.stop()

                    if (
                        audio.size <
                        AudioRecorder.SAMPLE_RATE / 5
                    ) {
                        throw IllegalArgumentException(
                            getString(
                                R.string.recording_too_short,
                            ),
                        )
                    }

                    val text =
                        recognizer.transcribe(
                            audio,
                        )

                    withContext(
                        Dispatchers.Main,
                    ) {
                        if (text.isBlank()) {
                            showToast(
                                getString(
                                    R.string.empty_transcription,
                                ),
                            )
                        } else {
                            when (
                                insertionMode
                            ) {
                                InsertionMode.CLIPBOARD -> {
                                    inserter.putToClipboard(
                                        text,
                                    )
                                }

                                InsertionMode.TEXT_FIELD -> {
                                    val success =
                                        inserter
                                            .pasteIntoFocusedField(
                                                focusedNode,
                                                text,
                                            )

                                    if (!success) {
                                        showToast(
                                            getString(
                                                R.string.paste_failed,
                                            ),
                                        )
                                    }
                                }
                            }
                        }

                        overlay.setIdle()
                    }
                } catch (error: Throwable) {
                    withContext(
                        Dispatchers.Main,
                    ) {
                        val message =
                            error.message
                                ?: error.javaClass.simpleName

                        showToast(
                            getString(
                                R.string.transcription_error,
                                message,
                            ),
                        )

                        overlay.setIdle()
                    }
                }
            }
    }

    override fun onInterrupt() {
        recording = false
        audioRecorder.cancel()
        overlay.setIdle()
    }

    override fun onDestroy() {
        recording = false

        audioRecorder.cancel()

        recordingJob?.cancel()

        try {
            focusedNode?.recycle()
        } catch (_: Throwable) {
        }

        focusedNode = null

        overlay.remove()

        recognizer.close()

        serviceScope.cancel()

        super.onDestroy()
    }

    private fun showToast(text: String) {
        mainHandler.post {
            Toast
                .makeText(
                    this,
                    text,
                    Toast.LENGTH_SHORT,
                ).show()
        }
    }
}
