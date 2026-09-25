package ru.gigapisar.model

object ModelConfig {
    const val DIRECTORY_NAME = "gigaam"
    const val MODEL_FILE_NAME = "v3_e2e_ctc.int8.onnx"
    const val VOCAB_FILE_NAME = "v3_e2e_ctc_vocab.txt"
    const val READY_FILE_NAME = ".ready"

    const val MODEL_URL =
        "https://huggingface.co/istupakov/gigaam-v3-onnx/" +
            "resolve/main/$MODEL_FILE_NAME?download=true"

    const val VOCAB_URL =
        "https://huggingface.co/istupakov/gigaam-v3-onnx/" +
            "resolve/main/$VOCAB_FILE_NAME?download=true"

    const val EXPECTED_MODEL_SIZE = 224_893_347L
    const val MODEL_SHA256 =
        "2e3fcb7a7b66030336fd10c2fcfb033bd1dc7e1bf238fe5cfd83b1d0cfc9d28e"
    const val VOCAB_SIZE = 257
    const val BLANK_ID = 256
}
