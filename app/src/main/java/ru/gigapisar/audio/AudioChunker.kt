package ru.gigapisar.audio

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Cuts a long take into pieces the model can hear (it takes at most 25 seconds at once),
 * like the desktop apps: each cut falls into the last pause before the limit, so words
 * are not split. Phone microphones are quiet, so "pause" is judged against this take's
 * own noise floor, not a fixed level.
 */
object AudioChunker {
    private const val WINDOW_MS = 20
    private const val MIN_PAUSE_MS = 150
    private const val MIN_CHUNK_SECONDS = 3.0

    // A window counts as pause when it is within this many dB of the take's quiet level.
    private const val PAUSE_ABOVE_FLOOR_DB = 8.0
    private const val MIN_DYNAMICS_DB = 12.0

    // Among pauses in the last stretch before the limit the longest wins: usually a sentence end, not a comma.
    private const val PREFER_WINDOW_SECONDS = 8.0

    /** A pause: its middle and its length, in samples. */
    class Pause(
        val middle: Int,
        val length: Int,
    )

    /** Sample ranges [start, end) no longer than [maxSeconds], cut at pauses where possible. */
    fun bounds(
        pcm: ShortArray,
        sampleRate: Int,
        maxSeconds: Double,
    ): List<IntRange> {
        val max = (maxSeconds * sampleRate).toInt()
        if (pcm.size <= max) return listOf(0 until pcm.size)
        val pauses = pauses(pcm, sampleRate)
        val minChunk = (MIN_CHUNK_SECONDS * sampleRate).toInt()
        val prefer = (PREFER_WINDOW_SECONDS * sampleRate).toInt()
        val result = mutableListOf<IntRange>()
        var pos = 0
        while (pcm.size - pos > max) {
            val fitting = pauses.filter { it.middle > pos + minChunk && it.middle <= pos + max }
            val late = fitting.filter { it.middle > pos + max - prefer }
            val cut = (late.maxByOrNull { it.length } ?: fitting.lastOrNull())?.middle ?: (pos + max)
            result += pos until cut
            pos = cut
        }
        result += pos until pcm.size
        return result
    }

    /** Pauses: runs of quiet windows of at least [MIN_PAUSE_MS]. */
    fun pauses(
        pcm: ShortArray,
        sampleRate: Int,
    ): List<Pause> {
        val window = sampleRate * WINDOW_MS / 1000
        val count = pcm.size / window
        if (count == 0) return emptyList()
        val db =
            DoubleArray(count) { w ->
                var sum = 0.0
                for (i in w * window until (w + 1) * window) {
                    val s = pcm[i] / 32768.0
                    sum += s * s
                }
                20 * log10(max(sqrt(sum / window), 1e-7))
            }
        // The take's quiet level: the 5th percentile of window loudness (pauses are often few).
        val sorted = db.sorted()
        val floor = sorted[min(count - 1, count * 5 / 100)]
        val loud = sorted[min(count - 1, count * 90 / 100)]
        // No real difference between quiet and loud: there are no pauses to find.
        if (loud - floor < MIN_DYNAMICS_DB) return emptyList()
        val threshold = floor + PAUSE_ABOVE_FLOOR_DB
        val minRun = MIN_PAUSE_MS / WINDOW_MS
        val points = mutableListOf<Pause>()
        var start = -1
        for (w in 0..count) {
            val quiet = w < count && db[w] < threshold
            if (quiet) {
                if (start < 0) start = w
            } else if (start >= 0) {
                if (w - start >= minRun) points += Pause((start + w) / 2 * window, (w - start) * window)
                start = -1
            }
        }
        return points
    }
}
