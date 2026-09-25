package ru.gigapisar.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread

class AudioRecorder {
    companion object {
        const val SAMPLE_RATE = 16_000
        private const val MAX_DURATION_MS = 25_000L
    }

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    @Volatile
    private var recording = false

    private val output =
        ByteArrayOutputStream()

    fun start(): Boolean {
        if (recording) {
            return false
        }

        val minBufferSize =
            AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )

        if (minBufferSize <= 0) {
            return false
        }

        val bufferSize =
            (minBufferSize * 2)
                .coerceAtLeast(SAMPLE_RATE / 2)

        return try {
            output.reset()

            val record =
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                )

            if (
                record.state !=
                AudioRecord.STATE_INITIALIZED
            ) {
                record.release()
                return false
            }

            audioRecord = record
            recording = true

            record.startRecording()

            recordingThread =
                thread(
                    name = "GigaPisarAudio",
                ) {
                    val buffer =
                        ByteArray(bufferSize)

                    val startedAt =
                        System.currentTimeMillis()

                    try {
                        while (recording) {
                            if (
                                System.currentTimeMillis() -
                                startedAt >
                                MAX_DURATION_MS
                            ) {
                                break
                            }

                            val count =
                                record.read(
                                    buffer,
                                    0,
                                    buffer.size,
                                )

                            if (count > 0) {
                                synchronized(output) {
                                    output.write(
                                        buffer,
                                        0,
                                        count,
                                    )
                                }
                            } else if (
                                count ==
                                AudioRecord.ERROR_DEAD_OBJECT
                            ) {
                                break
                            }
                        }
                    } catch (_: Throwable) {
                        // stop() will perform cleanup
                    }
                }

            true
        } catch (_: Throwable) {
            cleanup()
            false
        }
    }

    fun stop(): ShortArray {
        recording = false

        val record = audioRecord

        try {
            if (
                record != null &&
                record.recordingState ==
                AudioRecord.RECORDSTATE_RECORDING
            ) {
                record.stop()
            }
        } catch (_: Throwable) {
        }

        try {
            recordingThread?.join(1_000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        cleanup()

        val bytes =
            synchronized(output) {
                output.toByteArray()
            }

        if (bytes.size < 2) {
            return ShortArray(0)
        }

        val samples =
            ShortArray(bytes.size / 2)

        var i = 0

        while (i < samples.size) {
            val low =
                bytes[i * 2].toInt() and 0xFF

            val high =
                bytes[i * 2 + 1].toInt()

            samples[i] =
                ((high shl 8) or low).toShort()

            i++
        }

        return samples
    }

    fun cancel() {
        recording = false

        try {
            audioRecord?.stop()
        } catch (_: Throwable) {
        }

        try {
            recordingThread?.join(500)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        cleanup()
        output.reset()
    }

    private fun cleanup() {
        try {
            audioRecord?.release()
        } catch (_: Throwable) {
        }

        audioRecord = null
        recordingThread = null
    }
}
