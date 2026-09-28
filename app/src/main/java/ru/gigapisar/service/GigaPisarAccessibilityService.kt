package ru.gigapisar.service

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
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

    private val audioManager: AudioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

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

    private var virtualButtonEnabled = true

    private var focusedNode:
        AccessibilityNodeInfo? = null

    private var recordingJob: Job? = null

    private var volumeKeyPressed = false
    private var volumeKeyHoldTriggered = false
    private var volumeKeyStartedRecording = false

    @Volatile
    private var recording = false

    private val mainHandler =
        Handler(Looper.getMainLooper())

    private val volumeKeyHoldRunnable =
        Runnable {
            if (!volumeKeyPressed) {
                return@Runnable
            }

            volumeKeyHoldTriggered = true

            if (!recording && recordingJob?.isActive != true) {
                handleRecordingStart()
                volumeKeyStartedRecording = recording
            }
        }

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

        serviceScope.launch {
            SettingsRepository
                .insertionMode(this@GigaPisarAccessibilityService)
                .collectLatest { mode ->

                    insertionMode = mode

                    if (mode == InsertionMode.TEXT_FIELD) {
                        updateFocusedNode()
                    } else {
                        updateButtonVisibility()
                    }
                }
        }

        serviceScope.launch {
            SettingsRepository
                .virtualButtonVisible(this@GigaPisarAccessibilityService)
                .collectLatest { visible ->
                    virtualButtonEnabled = visible
                    updateButtonVisibility()
                }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (volumeKeyPressed) {
                    true
                } else if (event.repeatCount != 0) {
                    false
                } else {
                    volumeKeyPressed = true
                    volumeKeyHoldTriggered = false
                    volumeKeyStartedRecording = false
                    mainHandler.postDelayed(
                        volumeKeyHoldRunnable,
                        VOLUME_RECORDING_HOLD_DELAY_MS,
                    )
                    true
                }
            }

            KeyEvent.ACTION_UP -> {
                if (!volumeKeyPressed) {
                    return false
                }

                mainHandler.removeCallbacks(volumeKeyHoldRunnable)
                volumeKeyPressed = false

                if (volumeKeyHoldTriggered) {
                    if (volumeKeyStartedRecording) {
                        handleRecordingStop()
                    }
                } else {
                    // A short press is an ordinary volume-down: let Android pick the active
                    // stream (music, call, ring), as it does without us.
                    audioManager.adjustSuggestedStreamVolume(
                        AudioManager.ADJUST_LOWER,
                        AudioManager.USE_DEFAULT_STREAM_TYPE,
                        AudioManager.FLAG_SHOW_UI,
                    )
                }

                volumeKeyHoldTriggered = false
                volumeKeyStartedRecording = false
                true
            }

            else -> volumeKeyPressed
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
            -> {
                if (insertionMode == InsertionMode.TEXT_FIELD) {
                    updateFocusedNode(event)
                }
            }
        }
    }

    private fun updateFocusedNode(event: AccessibilityEvent? = null) {
        val node =
            findFocusedEditable(event?.source)
                ?: findFocusedEditable(rootInActiveWindow)

        if (node == null) {
            focusedNode = null
            updateButtonVisibility()
            return
        }

        focusedNode = node
        updateButtonVisibility()
    }

    private fun updateButtonVisibility() {
        val shouldShow =
            virtualButtonEnabled &&
                (
                    insertionMode == InsertionMode.CLIPBOARD ||
                        focusedNode != null
                )

        overlay.setButtonVisible(shouldShow)
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
        cancelVolumeKeyGesture()
        recording = false
        audioRecorder.cancel()
        overlay.setIdle()
    }

    override fun onDestroy() {
        cancelVolumeKeyGesture()
        recording = false

        audioRecorder.cancel()

        recordingJob?.cancel()

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

    private fun cancelVolumeKeyGesture() {
        mainHandler.removeCallbacks(volumeKeyHoldRunnable)
        volumeKeyPressed = false
        volumeKeyHoldTriggered = false
        volumeKeyStartedRecording = false
    }

    companion object {
        // An ordinary tap lasts 100-200 ms; recording starts only on a deliberate hold.
        private const val VOLUME_RECORDING_HOLD_DELAY_MS = 350L
    }
}
