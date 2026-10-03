package ru.gigapisar.model

object ModelConfig {
    const val DIRECTORY_NAME = "gigaam"
    const val MODEL_FILE_NAME = "v3_e2e_ctc.int8.onnx"
    const val VOCAB_FILE_NAME = "v3_e2e_ctc_vocab.txt"
    const val READY_FILE_NAME = ".ready"

    // Our GitHub copy first (Hugging Face is often slow or blocked in Russia), then the original.
    private const val GITHUB = "https://github.com/moznoazachem/giga-pisar-android/releases/download/model-v3/"
    private const val HUGGING_FACE = "https://huggingface.co/istupakov/gigaam-v3-onnx/resolve/main/"

    val MODEL_URLS =
        listOf(
            GITHUB + MODEL_FILE_NAME,
            "$HUGGING_FACE$MODEL_FILE_NAME?download=true",
        )

    val VOCAB_URLS =
        listOf(
            GITHUB + VOCAB_FILE_NAME,
            "$HUGGING_FACE$VOCAB_FILE_NAME?download=true",
        )

    const val EXPECTED_MODEL_SIZE = 224_893_347L
    const val MODEL_SHA256 =
        "2e3fcb7a7b66030336fd10c2fcfb033bd1dc7e1bf238fe5cfd83b1d0cfc9d28e"
    const val VOCAB_SHA256 =
        "142de7570b3de5b3035ce111a89c228e80e6085273731d944093ddf24fa539cd"
    const val VOCAB_SIZE = 257
    const val BLANK_ID = 256
}
