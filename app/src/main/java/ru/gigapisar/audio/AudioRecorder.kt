package ru.gigapisar.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread
import kotlin.math.min

class AudioRecorder(
    var onTimeout: (() -> Unit)? = null,
) {
    companion object {
        const val SAMPLE_RATE = 16_000

        // Long takes are cut at pauses before recognition (see AudioChunker); five minutes
        // keeps memory small (about 10 MB) and recognition well under a minute.
        private const val MAX_DURATION_MS = 300_000L

        private const val CHANNEL_CONFIG =
            AudioFormat.CHANNEL_IN_MONO

        private const val AUDIO_FORMAT =
            AudioFormat.ENCODING_PCM_16BIT

        /** 20 ms of 16-bit mono audio. */
        private const val READ_CHUNK_BYTES = SAMPLE_RATE / 50 * 2
    }

    private val lock = Any()
    private val meter = LevelMeter(SAMPLE_RATE)

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var output = ByteArrayOutputStream()

    @Volatile
    private var recording = false

    /**
     * Loudness of the latest 20 ms chunk, 0..1, adapted to the microphone (see [LevelMeter]). The
     * recording pill and the floating button read it every frame; it is 0 when nothing
     * is being recorded.
     */
    @Volatile
    var level: Float = 0f
        private set

    fun start(): Boolean {
        synchronized(lock) {
            if (recording) {
                return false
            }

            val minBufferSize =
                AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                )

            if (minBufferSize <= 0) {
                return false
            }

            val bufferSize =
                (minBufferSize * 2)
                    .coerceAtLeast(SAMPLE_RATE / 2)

            val record =
                try {
                    AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        bufferSize,
                    )
                } catch (_: SecurityException) {
                    return false
                } catch (_: IllegalArgumentException) {
                    return false
                }

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return false
            }

            output = ByteArrayOutputStream()

            audioRecord = record
            recording = true

            try {
                record.startRecording()
            } catch (_: IllegalStateException) {
                recording = false
                record.release()
                audioRecord = null
                return false
            }

            if (
                record.recordingState !=
                AudioRecord.RECORDSTATE_RECORDING
            ) {
                recording = false
                record.release()
                audioRecord = null
                return false
            }

            recordingThread =
                thread(
                    name = "GigaPisarAudio",
                    start = true,
                ) {
                    recordAudio(
                        record = record,
                        bufferSize = bufferSize,
                    )
                }

            return true
        }
    }

    private fun recordAudio(
        record: AudioRecord,
        bufferSize: Int,
    ) {
        // The system buffer stays large; we read it in 20 ms steps so the wave keeps up with
        // the voice. One read of the whole buffer is a quarter of a second, and the wave lagged.
        val buffer = ByteArray(min(bufferSize, READ_CHUNK_BYTES))
        val startedAt = SystemClock.elapsedRealtime()

        try {
            while (recording) {
                if (
                    SystemClock.elapsedRealtime() -
                    startedAt >= MAX_DURATION_MS
                ) {
                    onTimeout?.let { callback ->
                        Handler(Looper.getMainLooper()).post(callback)
                    }
                    break
                }

                val count =
                    record.read(
                        buffer,
                        0,
                        buffer.size,
                        AudioRecord.READ_BLOCKING,
                    )

                when {
                    count > 0 -> {
                        synchronized(lock) {
                            output.write(
                                buffer,
                                0,
                                count,
                            )
                        }
                        level = meter.levelOf(buffer, count)
                    }

                    count == AudioRecord.ERROR_DEAD_OBJECT -> {
                        break
                    }

                    count == AudioRecord.ERROR_INVALID_OPERATION -> {
                        break
                    }

                    count == AudioRecord.ERROR_BAD_VALUE -> {
                        break
                    }
                }
            }
        } finally {
            recording = false
            level = 0f
        }
    }

    fun stop(): ShortArray {
        val record: AudioRecord?
        val thread: Thread?

        synchronized(lock) {
            if (!recording && recordingThread == null) {
                return ShortArray(0)
            }

            recording = false

            record = audioRecord
            thread = recordingThread
        }

        stopRecord(record)

        waitForThread(thread)

        val bytes =
            synchronized(lock) {
                output.toByteArray()
            }

        cleanup()

        return pcm16ToShortArray(bytes)
    }

    fun cancel() {
        val record: AudioRecord?
        val thread: Thread?

        synchronized(lock) {
            recording = false

            record = audioRecord
            thread = recordingThread
        }

        stopRecord(record)
        waitForThread(thread)

        synchronized(lock) {
            output.reset()
        }

        cleanup()
    }

    private fun stopRecord(record: AudioRecord?) {
        if (record == null) {
            return
        }

        try {
            if (
                record.recordingState ==
                AudioRecord.RECORDSTATE_RECORDING
            ) {
                record.stop()
            }
        } catch (_: IllegalStateException) {
            // Already stopped.
        }
    }

    private fun waitForThread(thread: Thread?) {
        if (thread == null) {
            return
        }

        try {
            thread.join(500L)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun cleanup() {
        synchronized(lock) {
            try {
                audioRecord?.release()
            } catch (_: Exception) {
                // Ignore cleanup errors.
            }

            audioRecord = null
            recordingThread = null
        }
    }

    private fun pcm16ToShortArray(bytes: ByteArray): ShortArray {
        val sampleCount = bytes.size / 2

        if (sampleCount == 0) {
            return ShortArray(0)
        }

        val samples = ShortArray(sampleCount)

        for (i in 0 until sampleCount) {
            val offset = i * 2

            samples[i] =
                (
                    (bytes[offset].toInt() and 0xFF) or
                        (bytes[offset + 1].toInt() shl 8)
                ).toShort()
        }

        return samples
    }
}
