package ru.gigapisar.brain

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/** A failure the person can act on, already in plain words. */
class BrainException(
    message: String,
) : Exception(message)

/**
 * OpenAI-compatible chat API, the same requests the desktop apps send. Blocking: call it
 * off the main thread.
 */
object BrainClient {
    /**
     * reasoning_effort is not part of every OpenAI-compatible API; some servers reject it
     * with 400. We send it until a server refuses once, then stop for the rest of the run.
     */
    @Volatile
    private var sendReasoningEffort = true

    fun complete(
        provider: BrainProvider,
        key: String,
        model: String,
        prompt: String,
        text: String,
        timeoutMs: Int,
    ): String {
        val withReasoning = sendReasoningEffort
        val (status, body) = post(provider, key, model, prompt, text, withReasoning, timeoutMs)
        if (withReasoning && (status == 400 || status == 422) && body.contains("reasoning", ignoreCase = true)) {
            // Only drop the parameter when the server complains about it; a wrong model name is a real error.
            sendReasoningEffort = false
            val (retryStatus, retryBody) = post(provider, key, model, prompt, text, false, timeoutMs)
            return readContent(retryStatus, retryBody)
        }
        return readContent(status, body)
    }

    /** A tiny real request: a key that lists models may still be unable to chat (no balance, no access). */
    fun probe(
        provider: BrainProvider,
        key: String,
        model: String,
    ) {
        complete(provider, key, model, "Ответь одним словом: ок", "ок", 20_000)
    }

    fun listModels(
        provider: BrainProvider,
        key: String,
    ): List<String> {
        val (status, body) = request("GET", "${provider.baseUrl}/models", key, null, 20_000)
        if (status !in 200..299) throw BrainException(describeFailure(status, body))
        val data = JSONObject(body).getJSONArray("data")
        return (0 until data.length()).mapNotNull { data.getJSONObject(it).optString("id").takeIf { id -> id.isNotBlank() } }
    }

    private fun post(
        provider: BrainProvider,
        key: String,
        model: String,
        prompt: String,
        text: String,
        withReasoning: Boolean,
        timeoutMs: Int,
    ): Pair<Int, String> {
        val payload =
            JSONObject()
                .put("model", model)
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", prompt))
                        .put(JSONObject().put("role", "user").put("content", text)),
                )
        if (withReasoning) payload.put("reasoning_effort", "none")
        return request("POST", "${provider.baseUrl}/chat/completions", key, payload.toString(), timeoutMs)
    }

    private fun request(
        method: String,
        url: String,
        key: String,
        body: String?,
        timeoutMs: Int,
    ): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = minOf(timeoutMs, 10_000)
            connection.readTimeout = timeoutMs
            connection.setRequestProperty("Authorization", "Bearer ${key.trim()}")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                val bytes = body.toByteArray(Charsets.UTF_8)
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                // Fixed length, not chunked: some small servers do not accept chunked bodies.
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return status to text
        } catch (_: SocketTimeoutException) {
            throw BrainException("сервис не ответил за ${timeoutMs / 1000} секунд")
        } catch (_: IOException) {
            throw BrainException("нет связи с сервисом, проверьте интернет")
        } finally {
            connection.disconnect()
        }
    }

    private fun readContent(
        status: Int,
        body: String,
    ): String {
        if (status !in 200..299) throw BrainException(describeFailure(status, body))
        var answer =
            try {
                JSONObject(body)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            } catch (_: Exception) {
                throw BrainException("сервис ответил непонятно")
            }
        // A model that still thought aloud: keep only the answer.
        val think = answer.indexOf("</think>")
        if (think >= 0) answer = answer.substring(think + "</think>".length)
        return answer.trim()
    }

    /** The error text an OpenAI-style server puts in {"error":{"message":…}} (or {"message":…}). */
    private fun serverMessage(body: String): String =
        try {
            val trimmed = body.trim()
            val root = if (trimmed.startsWith("[")) JSONArray(trimmed).getJSONObject(0) else JSONObject(trimmed)
            val error = root.opt("error")
            when {
                error is String -> error
                error is JSONObject -> error.optString("message")
                else -> root.optString("message")
            }
        } catch (_: Exception) {
            body.take(200)
        }

    /** What went wrong, in words a person can act on. Same wording as the desktop apps. */
    fun describeFailure(
        status: Int,
        body: String,
    ): String {
        val m = (serverMessage(body) + " " + body).lowercase()
        return when {
            status == 402 || listOf("insufficient_quota", "quota", "billing", "balance", "credit", "payment").any { it in m } ->
                "на счету сервиса нет денег или не подключена оплата API (это отдельно от подписки вроде ChatGPT Plus)"
            listOf("country", "region", "territory", "location").any { it in m } -> "сервис недоступен из вашей страны"
            status == 401 -> "сервис не принял ключ"
            status == 403 -> "у ключа нет доступа к этой модели или сервису"
            status == 404 || ("model" in m && ("not found" in m || "not exist" in m)) ->
                "модель недоступна для этого ключа, выберите другую"
            status == 429 -> "слишком много запросов, попробуйте через минуту"
            status >= 500 -> "у сервиса сбой (ошибка $status), попробуйте позже"
            else -> "сервис ответил ошибкой $status"
        }
    }
}
