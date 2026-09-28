package ru.gigapisar.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread

class AudioRecorder {
    companion object {
        const val SAMPLE_RATE = 16_000
        private const val MAX_DURATION_MS = 25_000L

        private const val CHANNEL_CONFIG =
            AudioFormat.CHANNEL_IN_MONO

        private const val AUDIO_FORMAT =
            AudioFormat.ENCODING_PCM_16BIT
    }

    private val lock = Any()

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var output = ByteArrayOutputStream()

    @Volatile
    private var recording = false

    /**
     * Loudness of the latest audio chunk, 0..1 (peak of the chunk). The recording pill
     * reads it every frame to draw the wave; it is 0 when nothing is being recorded.
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
        val buffer = ByteArray(bufferSize)
        val startedAt = SystemClock.elapsedRealtime()

        try {
            while (recording) {
                if (
                    SystemClock.elapsedRealtime() -
                    startedAt >= MAX_DURATION_MS
                ) {
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
                        level = peakOf(buffer, count)
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

    /** Peak of little-endian 16-bit PCM, scaled to 0..1. */
    private fun peakOf(
        bytes: ByteArray,
        count: Int,
    ): Float {
        var peak = 0
        var i = 0
        while (i + 1 < count) {
            val sample = (bytes[i].toInt() and 0xFF) or (bytes[i + 1].toInt() shl 8)
            val magnitude = if (sample < 0) -sample else sample
            if (magnitude > peak) peak = magnitude
            i += 2
        }
        return (peak / 32768f).coerceIn(0f, 1f)
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
            thread.join()
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
