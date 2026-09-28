package ru.gigapisar.service

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import ru.gigapisar.MainActivity
import ru.gigapisar.R
import ru.gigapisar.audio.AudioRecorder
import ru.gigapisar.insertion.TextInserter
import ru.gigapisar.model.ModelManager
import ru.gigapisar.overlay.OverlayManager
import ru.gigapisar.overlay.RecordingPill
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

    private lateinit var pill:
        RecordingPill

    /** Checked at most every few seconds: validating the model reads its files. */
    private var modelReady = false
    private var modelCheckedAt = 0L

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

        pill =
            RecordingPill(this)

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

        val now = SystemClock.elapsedRealtime()
        if (now - modelCheckedAt > 3000) {
            modelCheckedAt = now
            modelReady = modelManager.isInstalled()
        }
        overlay.setAvailable(modelReady)
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
            notifyUser(
                getString(
                    R.string.microphone_required,
                ),
            )

            overlay.setIdle()
            return
        }

        if (!modelManager.isInstalled()) {
            notifyUser(
                getString(
                    R.string.model_required,
                ),
            )

            overlay.setIdle()
            openAppForSetup()
            return
        }

        if (!audioRecorder.start()) {
            notifyUser(
                getString(
                    R.string.audio_record_error,
                ),
            )

            overlay.setIdle()
            return
        }

        recording = true
        overlay.setLevelSource { audioRecorder.level }
        overlay.setRecording()
        pill.showListening(focusedFieldBounds()) { audioRecorder.level }
        buzz()
    }

    private fun handleRecordingStop() {
        if (!recording) {
            overlay.setIdle()
            return
        }

        recording = false

        overlay.setProcessing()
        pill.showProcessing()
        buzz()

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
                        // A quick tap is not a mistake worth a message: just go back to idle.
                        withContext(Dispatchers.Main) {
                            pill.hide()
                            overlay.setIdle()
                        }
                        return@launch
                    }

                    val text =
                        recognizer.transcribe(
                            audio,
                        )

                    withContext(
                        Dispatchers.Main,
                    ) {
                        if (text.isBlank()) {
                            notifyUser(
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
                                        notifyUser(
                                            getString(
                                                R.string.paste_failed,
                                            ),
                                        )
                                    } else {
                                        pill.hide()
                                    }
                                }
                            }
                        }

                        if (insertionMode == InsertionMode.CLIPBOARD && text.isNotBlank()) {
                            notifyUser(getString(R.string.copied_to_clipboard))
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

                        notifyUser(
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
        pill.hide()
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

    /** Errors and hints of the recording flow: on the pill, next to where the text goes. */
    private fun notifyUser(text: String) {
        mainHandler.post { pill.showMessage(text, focusedFieldBounds()) }
    }

    private fun focusedFieldBounds(): Rect? =
        focusedNode?.let { node ->
            try {
                Rect().also(node::getBoundsInScreen)
            } catch (_: Exception) {
                null
            }
        }

    /** A short tick at the start and the end of a recording: dictation without looking at the screen. */
    private fun buzz() {
        try {
            val vibrator =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    getSystemService(VibratorManager::class.java).defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(Vibrator::class.java)
                }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {
            // No vibrator: nothing to do.
        }
    }

    /** Opens the app on its setup steps (e.g. the model is missing). */
    private fun openAppForSetup() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        } catch (_: Exception) {
            // The pill already said what to do.
        }
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
