package ru.gigapisar.audio

import org.junit.Assert.assertTrue
import org.junit.Test

class LevelMeterTest {
    /** Levels over [seconds] of chunks at 50 per second, alternating between the given dB values. */
    private fun LevelMeter.feed(
        seconds: Double,
        vararg db: Double,
    ): List<Float> = (0 until (seconds * 50).toInt()).map { levelOfDb(db[it % db.size]) }

    @Test
    fun quietPhoneMicrophoneStillMovesTheWave() {
        // A Samsung without gain: the room near -62 dB, speech peaks only -45..-38 dB.
        val meter = LevelMeter(16_000)
        val silence = meter.feed(1.0, -62.0, -61.0)
        val speech = meter.feed(2.0, -45.0, -38.0, -50.0, -41.0)
        assertTrue("silence must lie flat: ${silence.takeLast(10)}", silence.takeLast(25).all { it < 0.1f })
        assertTrue("speech must move the wave: ${speech.takeLast(8)}", speech.takeLast(50).maxOrNull()!! > 0.8f)
        assertTrue("and not stick at the top", speech.takeLast(50).minOrNull()!! < 0.7f)
    }

    @Test
    fun loudMicrophoneDoesNotClipEverything() {
        // A Mac-like level: speech at -25..-12 dB.
        val meter = LevelMeter(16_000)
        meter.feed(1.0, -55.0)
        val speech = meter.feed(2.0, -25.0, -12.0, -30.0, -18.0)
        assertTrue(speech.takeLast(50).minOrNull()!! < 0.8f)
        assertTrue(speech.takeLast(50).maxOrNull()!! > 0.9f)
    }

    @Test
    fun pauseInSpeechGoesQuiet() {
        val meter = LevelMeter(16_000)
        meter.feed(1.0, -62.0)
        meter.feed(2.0, -40.0, -45.0)
        val pause = meter.feed(0.6, -61.0)
        assertTrue("pause: ${pause.takeLast(5)}", pause.takeLast(10).all { it < 0.1f })
    }
}
