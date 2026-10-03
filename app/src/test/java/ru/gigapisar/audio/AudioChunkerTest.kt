package ru.gigapisar.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AudioChunkerTest {
    private val rate = 16_000

    /** "Speech" (a quiet tone, like a phone mic) with pauses of [pause] seconds every [phrase] seconds. */
    private fun take(
        seconds: Double,
        phrase: Double,
        pause: Double,
    ): ShortArray {
        val n = (seconds * rate).toInt()
        return ShortArray(n) { i ->
            val t = i.toDouble() / rate
            val inPause = t % (phrase + pause) >= phrase
            if (inPause) (if (i % 7 == 0) 3 else -2).toShort() else (300 * sin(2 * PI * 220 * t)).toInt().toShort()
        }
    }

    @Test
    fun shortTakeIsOnePiece() {
        val pcm = take(20.0, 4.0, 0.5)
        assertEquals(listOf(0 until pcm.size), AudioChunker.bounds(pcm, rate, 24.0))
    }

    @Test
    fun longTakeIsCutInPausesWithinLimit() {
        val pcm = take(70.0, 4.0, 0.6)
        val pieces = AudioChunker.bounds(pcm, rate, 24.0)
        assertTrue("several pieces: $pieces", pieces.size >= 3)
        assertEquals(0, pieces.first().first)
        assertEquals(pcm.size - 1, pieces.last().last)
        for (i in 1 until pieces.size) assertEquals(pieces[i - 1].last + 1, pieces[i].first)
        for (p in pieces) assertTrue("piece within 24 s: $p", p.last - p.first + 1 <= 24 * rate)
        // Every cut falls inside a pause, not in a phrase.
        for (p in pieces.dropLast(1)) {
            val t = (p.last + 1).toDouble() / rate
            assertTrue("cut at $t s is in a pause", t % 4.6 >= 4.0)
        }
    }

    @Test
    fun noPausesStillFitsTheModel() {
        val pcm = take(60.0, 1000.0, 0.0)
        val pieces = AudioChunker.bounds(pcm, rate, 24.0)
        assertEquals(3, pieces.size)
        for (p in pieces) assertTrue(p.last - p.first + 1 <= 24 * rate)
    }
}
