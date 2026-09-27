package ru.gigapisar.speech

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import ru.gigapisar.audio.AudioRecorder
import ru.gigapisar.model.ModelConfig
import ru.gigapisar.model.ModelManager
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin

class GigaAmOnnxRecognizer(
    context: Context,
    private val modelManager: ModelManager =
        ModelManager(context),
) {
    private val environment =
        OrtEnvironment.getEnvironment()

    private var session: OrtSession? = null
    private var vocabulary: List<String>? = null

    @Synchronized
    fun transcribe(pcm: ShortArray): String {
        require(pcm.isNotEmpty()) {
            "Empty audio"
        }

        require(
            pcm.size <=
                AudioRecorder.SAMPLE_RATE * 25,
        ) {
            "Audio is longer than 25 seconds"
        }

        val currentSession =
            getSession()

        val currentVocabulary =
            getVocabulary()

        val extracted =
            FeatureExtractor.extract(pcm)

        val features =
            extracted.features

        val frameCount =
            extracted.frameCount

        val featureTensor =
            OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(features),
                longArrayOf(
                    1L,
                    FeatureExtractor.N_MELS.toLong(),
                    frameCount.toLong(),
                ),
            )

        val lengthTensor =
            OnnxTensor.createTensor(
                environment,
                LongBuffer.wrap(
                    longArrayOf(frameCount.toLong()),
                ),
                longArrayOf(1L),
            )

        try {
            val result =
                currentSession.run(
                    mapOf(
                        "features" to featureTensor,
                        "feature_lengths" to lengthTensor,
                    ),
                )

            result.use { result ->
                @Suppress("UNCHECKED_CAST")
                val output =
                    result[0].value
                        as Array<Array<FloatArray>>

                return decodeCtc(
                    output = output[0],
                    vocabulary = currentVocabulary,
                )
            }
        } finally {
            featureTensor.close()
            lengthTensor.close()
        }
    }

    @Synchronized
    private fun getSession(): OrtSession {
        session?.let { return it }

        check(modelManager.isInstalled()) {
            "GigaAM model is not installed"
        }

        val options =
            OrtSession.SessionOptions()

        options.setIntraOpNumThreads(
            max(
                2,
                Runtime
                    .getRuntime()
                    .availableProcessors() - 1,
            ),
        )

        val created =
            environment.createSession(
                modelManager.modelFile.absolutePath,
                options,
            )

        options.close()

        session = created

        return created
    }

    private fun getVocabulary(): List<String> {
        vocabulary?.let { return it }

        val tokens =
            modelManager.vocabFile
                .readLines(Charsets.UTF_8)
                .filter { it.isNotBlank() }
                .sortedBy { line ->
                    line
                        .substringAfterLast(' ')
                        .trim()
                        .toInt()
                }.map { line ->
                    val separator =
                        line.lastIndexOf(' ')

                    val token =
                        line.substring(
                            0,
                            separator,
                        )

                    token
                }

        check(tokens.size == ModelConfig.VOCAB_SIZE) {
            "Unexpected vocabulary size"
        }

        vocabulary = tokens

        return tokens
    }

    private fun decodeCtc(
        output: Array<FloatArray>,
        vocabulary: List<String>,
    ): String {
        val blankId =
            vocabulary.indexOf("<blk>")

        require(blankId >= 0) {
            "В vocabulary отсутствует <blk>"
        }

        val pieces =
            StringBuilder()

        var previousId = -1

        for (time in output.indices) {
            val logits =
                output[time]

            var bestId = 0
            var bestValue =
                Float.NEGATIVE_INFINITY

            for (id in logits.indices) {
                val value =
                    logits[id]

                if (value > bestValue) {
                    bestValue = value
                    bestId = id
                }
            }

            // CTC blank
            if (bestId == blankId) {
                previousId = -1
                continue
            }

            // CTC collapse repeated token
            if (bestId == previousId) {
                continue
            }

            previousId = bestId

            if (bestId >= vocabulary.size) {
                continue
            }

            val token =
                vocabulary[bestId]

            if (token == "<unk>") {
                continue
            }

            pieces.append(
                token.replace(
                    "▁",
                    " ",
                ),
            )
        }

        return pieces
            .toString()
            .replace(
                Regex("\\s+"),
                " ",
            ).trim()
    }

    @Synchronized
    fun close() {
        session?.close()
        session = null
    }

    private object FeatureExtractor {
        const val N_MELS = 64

        private const val SAMPLE_RATE = 16_000
        private const val N_FFT = 320
        private const val WIN_LENGTH = 320
        private const val HOP_LENGTH = 160
        private const val N_FREQS = N_FFT / 2 + 1
        private const val EPSILON = 1e-9f

        private val window =
            FloatArray(WIN_LENGTH) { n ->
                (
                    0.5 -
                        0.5 *
                        cos(
                            2.0 *
                                PI *
                                n /
                                WIN_LENGTH,
                        )
                ).toFloat()
            }

        private val cosTable =
            FloatArray(
                N_FREQS * N_FFT,
            )

        private val sinTable =
            FloatArray(
                N_FREQS * N_FFT,
            )

        private val melFilters =
            createMelFilters()

        init {
            for (k in 0 until N_FREQS) {
                for (n in 0 until N_FFT) {

                    val angle =
                        2.0 *
                            PI *
                            k *
                            n /
                            N_FFT

                    val index =
                        k * N_FFT + n

                    cosTable[index] =
                        cos(angle).toFloat()

                    sinTable[index] =
                        sin(angle).toFloat()
                }
            }
        }

        data class Result(
            val features: FloatArray,
            val frameCount: Int,
        ) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Result

                if (frameCount != other.frameCount) return false
                if (!features.contentEquals(other.features)) return false

                return true
            }

            override fun hashCode(): Int {
                var result = frameCount
                result = 31 * result + features.contentHashCode()
                return result
            }
        }

        fun extract(pcm: ShortArray): Result {
            require(
                pcm.size >= WIN_LENGTH,
            ) {
                "Audio is too short"
            }

            val frameCount =
                (pcm.size - WIN_LENGTH) /
                    HOP_LENGTH + 1

            val features =
                FloatArray(
                    N_MELS * frameCount,
                )

            val power =
                FloatArray(N_FREQS)

            for (frame in 0 until frameCount) {
                val start =
                    frame * HOP_LENGTH

                for (k in 0 until N_FREQS) {
                    var real = 0.0
                    var imag = 0.0

                    val tableStart =
                        k * N_FFT

                    for (n in 0 until N_FFT) {
                        val sample =
                            (
                                pcm[start + n]
                                    .toFloat() /
                                    32768.0f
                            ) *
                                window[n]

                        real +=
                            sample *
                            cosTable[
                                tableStart + n,
                            ]

                        imag -=
                            sample *
                            sinTable[
                                tableStart + n,
                            ]
                    }

                    power[k] =
                        (
                            real * real +
                                imag * imag
                        ).toFloat()
                }

                for (mel in 0 until N_MELS) {
                    var energy = 0.0

                    val filterStart =
                        mel * N_FREQS

                    for (k in 0 until N_FREQS) {
                        energy +=
                            power[k] *
                            melFilters[
                                filterStart + k,
                            ]
                    }

                    val value =
                        ln(
                            max(
                                energy,
                                EPSILON.toDouble(),
                            ),
                        ).toFloat()

                    features[
                        mel * frameCount +
                            frame,
                    ] = value
                }
            }

            return Result(
                features = features,
                frameCount = frameCount,
            )
        }

        private fun createMelFilters(): FloatArray {
            val filters =
                FloatArray(
                    N_MELS * N_FREQS,
                )

            fun hzToMel(hz: Double): Double =
                2595.0 *
                    kotlin.math.log10(
                        1.0 + hz / 700.0,
                    )

            fun melToHz(mel: Double): Double =
                700.0 *
                    (
                        kotlin.math.exp(
                            (mel / 2595.0) * ln(10.0),
                        ) - 1.0
                    )

            val minMel =
                hzToMel(0.0)

            val maxMel =
                hzToMel(
                    SAMPLE_RATE / 2.0,
                )

            val points =
                DoubleArray(
                    N_MELS + 2,
                )

            for (i in points.indices) {
                points[i] =
                    melToHz(
                        minMel +
                            (
                                maxMel -
                                    minMel
                            ) *
                            i /
                            (N_MELS + 1),
                    )
            }

            val frequencies =
                DoubleArray(N_FREQS) { k ->
                    k.toDouble() *
                        (
                            SAMPLE_RATE / 2.0
                        ) /
                        (N_FREQS - 1)
                }

            for (mel in 0 until N_MELS) {
                val left =
                    points[mel]

                val center =
                    points[mel + 1]

                val right =
                    points[mel + 2]

                for (k in 0 until N_FREQS) {
                    val frequency =
                        frequencies[k]

                    val value =
                        when {
                            frequency < left ->
                                0.0

                            frequency <= center ->
                                (
                                    frequency -
                                        left
                                ) /
                                    (
                                        center -
                                            left
                                    )

                            frequency <= right ->
                                (
                                    right -
                                        frequency
                                ) /
                                    (
                                        right -
                                            center
                                    )

                            else ->
                                0.0
                        }

                    filters[
                        mel * N_FREQS + k,
                    ] =
                        value
                            .coerceIn(0.0, 1.0)
                            .toFloat()
                }
            }

            return filters
        }
    }
}
