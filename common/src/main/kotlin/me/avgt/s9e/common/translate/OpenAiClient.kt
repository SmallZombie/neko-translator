package me.avgt.s9e.common.translate

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.ConnectException
import java.net.URI
import java.net.UnknownHostException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.charset.StandardCharsets
import java.time.Duration
import me.avgt.s9e.common.lang.LanguageNames
import me.avgt.s9e.common.log.NekoLog

/**
 * Minimal OpenAI-compatible HTTP client. Only used by the local client; the API key is
 * taken from {@link me.avgt.s9e.client.config.NekoConfig} and never leaves this process
 * except towards the configured base URL.
 */
class OpenAiClient {
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun translate(
        baseUrl: String,
        apiKey: String,
        model: String,
        text: String,
        languageCode: String,
        reasoningEffort: String = REASONING_OFF,
    ): TranslationResult {
        if (baseUrl.isBlank()) return TranslationResult.Err("API 地址为空，请使用 /t config 配置")
        if (model.isBlank()) return TranslationResult.Err("未选择模型，请使用 /t config 配置")
        if (apiKey.isBlank()) return TranslationResult.Err("API 密钥为空，请使用 /t config 配置")

        val languageName = LanguageNames.promptName(languageCode)
        val payload = JsonObject()
        payload.addProperty("model", model)
        payload.addProperty("stream", false)
        payload.addProperty("temperature", 0.2)

        if (reasoningEffort != REASONING_OFF) {
            payload.addProperty("reasoning_effort", reasoningEffort)
        }

        val messages = JsonArray()
        messages.add(message("system", systemPrompt(languageName, languageCode)))
        messages.add(message("user", text))
        payload.add("messages", messages)

        val endpoint = chatEndpoint(baseUrl)
        val request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
            .build()

        return try {
            val response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            val body = response.body() ?: ""
            if (response.statusCode() !in 200..299) {
                TranslationResult.Err("翻译服务返回 HTTP ${response.statusCode()}: ${apiError(body)}")
            } else {
                val content = extractContent(body)
                if (content.isNullOrBlank()) {
                    TranslationResult.Err("翻译服务返回了空结果")
                } else {
                    val trimmed = content.trim()
                    if (trimmed.startsWith(SKIP_TOKEN)) {
                        // Already in the target language: use the original text as the result.
                        TranslationResult.Ok(text)
                    } else {
                        TranslationResult.Ok(trimmed)
                    }
                }
            }
        } catch (t: HttpTimeoutException) {
            TranslationResult.Err("请求翻译服务超时")
        } catch (t: ConnectException) {
            TranslationResult.Err("无法连接翻译服务，请检查 API 地址")
        } catch (t: UnknownHostException) {
            TranslationResult.Err("无法解析 API 地址")
        } catch (t: InterruptedException) {
            Thread.currentThread().interrupt()
            TranslationResult.Err("翻译请求被中断")
        } catch (t: Throwable) {
            NekoLog.warn("Translation request failed: ${t.javaClass.simpleName}: ${t.message}")
            TranslationResult.Err("请求翻译服务失败（${t.javaClass.simpleName}）")
        }
    }

    /** Queries the `/models` endpoint. Throws [TranslationException] with a user-facing message. */
    fun listModels(baseUrl: String, apiKey: String): List<String> {
        if (baseUrl.isBlank()) throw TranslationException("API 地址为空")
        val endpoint = modelsEndpoint(baseUrl)
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .GET()
        if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer $apiKey")

        val response = try {
            http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        } catch (t: InterruptedException) {
            Thread.currentThread().interrupt()
            throw TranslationException("请求被中断")
        } catch (t: Throwable) {
            throw TranslationException("无法获取模型列表（${t.javaClass.simpleName}）")
        }

        val body = response.body() ?: ""
        if (response.statusCode() !in 200..299) {
            throw TranslationException("模型列表返回 HTTP ${response.statusCode()}: ${apiError(body)}")
        }

        val root = try {
            JsonParser.parseString(body).asJsonObject
        } catch (t: Throwable) {
            throw TranslationException("模型列表不是有效的 JSON")
        }

        val data = root.getAsJsonArray("data") ?: throw TranslationException("模型列表缺少 data 字段")
        val models = ArrayList<String>()
        for (element in data) {
            if (!element.isJsonObject) continue
            val id = element.asJsonObject.get("id")
            if (id != null && id.isJsonPrimitive) models.add(id.asString)
        }
        models.sort()
        return models
    }

    private fun message(role: String, content: String): JsonObject {
        val obj = JsonObject()
        obj.addProperty("role", role)
        obj.addProperty("content", content)
        return obj
    }

    private fun extractContent(body: String): String? {
        val root = try {
            JsonParser.parseString(body).asJsonObject
        } catch (t: Throwable) {
            return null
        }

        val choices = root.getAsJsonArray("choices") ?: return null
        if (choices.size() == 0 || !choices.get(0).isJsonObject) return null
        val message = choices.get(0).asJsonObject.getAsJsonObject("message") ?: return null
        val content = message.get("content") ?: return null
        if (content.isJsonPrimitive) return content.asString
        if (content.isJsonArray) {
            val builder = StringBuilder()
            for (part in content.asJsonArray) {
                if (part.isJsonObject) {
                    val text = part.asJsonObject.get("text")
                    if (text != null && text.isJsonPrimitive) builder.append(text.asString)
                }
            }
            return builder.toString()
        }
        return null
    }

    private fun apiError(body: String): String {
        try {
            val root = JsonParser.parseString(body)
            if (root.isJsonObject) {
                val error = root.asJsonObject.get("error")
                if (error != null && error.isJsonObject) {
                    val message = error.asJsonObject.get("message")
                    if (message != null && message.isJsonPrimitive) return message.asString.take(200)
                }
            }
        } catch (ignored: Throwable) {
            // fall through to the raw body
        }
        return body.trim().take(200).ifEmpty { "无响应内容" }
    }

    private fun systemPrompt(languageName: String, languageCode: String): String = buildString {
        append("You are a translation engine embedded in a Minecraft chat window. ")
        append("Translate the user's chat message into ")
        append(languageName)
        append(" (locale: ")
        append(languageCode)
        append("). ")
        append("Rules: ")
        append("reply with the translated text only, without quotes, explanations or extra commentary; ")
        append("keep names, @mentions, numbers, URLs, emoji, command syntax and everything inside < > or [ ] exactly as written; ")
        append("keep the original line structure and punctuation layout; ")
        append("the text may contain segment markers such as \u27E60\u27E7 - keep every marker exactly where it is, do not translate or move it, and translate the text that follows it; ")
        append("if the message already matches the target language and needs no translation, reply with exactly ")
        append(SKIP_TOKEN)
        append(" and nothing else.")
    }

    companion object {
        /** Sentinel the model is instructed to return when no translation is needed. */
        const val SKIP_TOKEN = "<|skip|>"

        /** Value of the "thinking level" setting that disables reasoning entirely. */
        const val REASONING_OFF = "off"

        fun normalizeBase(baseUrl: String): String {
            var base = baseUrl.trim()
            if (base.isEmpty()) return base
            if (!base.contains("://")) base = "https://$base"
            while (base.endsWith("/")) base = base.substring(0, base.length - 1)
            return base
        }

        fun chatEndpoint(baseUrl: String): String {
            val base = normalizeBase(baseUrl)
            return when {
                base.endsWith("/chat/completions") -> base
                base.endsWith("/v1") || base.endsWith("/v1beta") -> "$base/chat/completions"
                else -> "$base/v1/chat/completions"
            }
        }

        fun modelsEndpoint(baseUrl: String): String {
            var base = normalizeBase(baseUrl)
            if (base.endsWith("/chat/completions")) {
                base = base.substring(0, base.length - "/chat/completions".length)
            }
            return when {
                base.endsWith("/v1") || base.endsWith("/v1beta") -> "$base/models"
                else -> "$base/v1/models"
            }
        }
    }
}

class TranslationException(message: String) : Exception(message)
