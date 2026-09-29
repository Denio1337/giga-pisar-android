package ru.gigapisar.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

class AudioRecorder(
    var onTimeout: (() -> Unit)? = null,
) {
    companion object {
        const val SAMPLE_RATE = 16_000
        private const val MAX_DURATION_MS = 25_000L

        private const val CHANNEL_CONFIG =
            AudioFormat.CHANNEL_IN_MONO

        private const val AUDIO_FORMAT =
            AudioFormat.ENCODING_PCM_16BIT

        /** 20 ms of 16-bit mono audio. */
        private const val READ_CHUNK_BYTES = SAMPLE_RATE / 50 * 2

        // Wave scale in dB below full scale: a quiet room sits near -55, speech peaks at -35..-12.
        private const val DB_FLOOR = -50.0
        private const val DB_CEIL = -15.0

        // Lifts the middle so ordinary speech does not hang in the lower third.
        private const val LEVEL_CURVE = 0.6
    }

    private val lock = Any()

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var output = ByteArrayOutputStream()

    @Volatile
    private var recording = false

    /**
     * Loudness of the latest 20 ms chunk, 0..1 on the same dB scale as the Mac app. The
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
                        level = levelOf(buffer, count)
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

    /**
     * Loudness of little-endian 16-bit PCM for the wave: the loudest 10 ms window, in dB,
     * mapped so a quiet room lies flat and ordinary speech fills most of the height. The
     * same floor, ceiling and curve as the Mac app.
     */
    private fun levelOf(
        bytes: ByteArray,
        count: Int,
    ): Float {
        val samples = count / 2
        if (samples == 0) return 0f
        val window = SAMPLE_RATE / 100
        var loudest = 0.0
        var start = 0
        while (start < samples) {
            val end = min(start + window, samples)
            var sum = 0.0
            for (n in start until end) {
                val i = n * 2
                val sample = ((bytes[i].toInt() and 0xFF) or (bytes[i + 1].toInt() shl 8)) / 32768.0
                sum += sample * sample
            }
            loudest = max(loudest, sqrt(sum / (end - start)))
            start = end
        }
        val db = 20 * log10(max(loudest, 1e-7))
        val norm = ((db - DB_FLOOR) / (DB_CEIL - DB_FLOOR)).coerceIn(0.0, 1.0)
        return norm.pow(LEVEL_CURVE).toFloat()
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
