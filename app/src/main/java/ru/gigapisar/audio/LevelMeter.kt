package ru.gigapisar.audio

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Loudness for the wave, 0..1, that adapts to the microphone. Phones hand over raw audio
 * without gain: on a Samsung ordinary speech sits far below what a Mac microphone gives,
 * and a fixed dB scale left the wave lying flat while the person talked. So the meter
 * learns the room's silence and the recent loudest sound and stretches the wave between
 * them. A quiet microphone still moves it; a loud one does not clip it.
 */
class LevelMeter(
    private val sampleRate: Int,
) {
    private var noiseDb = START_NOISE_DB
    private var peakDb = START_PEAK_DB

    /** Level of one chunk of little-endian 16-bit PCM: the loudest 10 ms window. */
    fun levelOf(
        bytes: ByteArray,
        count: Int,
    ): Float {
        val samples = count / 2
        if (samples == 0) return 0f
        val window = sampleRate / 100
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
        return levelOfDb(20 * log10(max(loudest, 1e-7)))
    }

    /** Called about 50 times a second, one chunk each. */
    fun levelOfDb(db: Double): Float {
        // Silence: follows quiet chunks down at once, creeps up slowly (speech must not become "silence").
        noiseDb = if (db < noiseDb) db else min(noiseDb + NOISE_RISE_DB, db)
        // Loudest recent sound: jumps up at once, sinks slowly, never closer to silence than MIN_RANGE_DB.
        peakDb = max(if (db > peakDb) db else peakDb - PEAK_FALL_DB, noiseDb + MIN_RANGE_DB)
        val floor = noiseDb + GATE_DB
        val norm = ((db - floor) / (peakDb - floor)).coerceIn(0.0, 1.0)
        return norm.pow(CURVE).toFloat()
    }

    private companion object {
        const val START_NOISE_DB = -65.0
        const val START_PEAK_DB = -35.0

        // Per 20 ms chunk: silence may rise 2.5 dB a second, the peak falls 6 dB a second.
        const val NOISE_RISE_DB = 0.05
        const val PEAK_FALL_DB = 0.12

        // Room hiss stays flat: the wave starts this far above the learned silence.
        const val GATE_DB = 6.0
        const val MIN_RANGE_DB = 20.0

        // Lifts the middle so ordinary speech does not hang in the lower third.
        const val CURVE = 0.6
    }
}
