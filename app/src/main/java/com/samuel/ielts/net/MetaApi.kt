package com.samuel.ielts.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Endpoint + credential config. Key lives in [com.samuel.ielts.sec.ApiKeyStore]. */
data class MetaConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val reasoningEffort: String = "medium",
    val temperature: Double = 0.2
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.meta.ai/v1"
        const val DEFAULT_MODEL = "muse-spark-1.3-contributor"
    }
}

sealed interface MetaResult {
    data class Success(val content: String) : MetaResult
    data class HttpError(val code: Int, val message: String) : MetaResult
    data class Failure(val message: String) : MetaResult
}

/**
 * Minimal Muse Spark chat client. Pure JVM (no android.* imports) so it
 * runs in local unit tests against MockWebServer.
 *
 * The API key is sent only as an Authorization header and is never
 * included in result messages, exceptions, or logs.
 */
class MetaApi(
    private val config: MetaConfig,
    private val client: OkHttpClient = defaultClient()
) {
    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()

        /** Default strict schema for Writing marker output. */
        const val WRITING_SCHEMA = """{"name":"writing_mark","strict":true,"schema":{""" +
            """"type":"object","properties":{""" +
            """"taskAchieve":{"type":"number"},"coherence":{"type":"number"},""" +
            """"lexical":{"type":"number"},"grammar":{"type":"number"},""" +
            """"overall":{"type":"number"},"feedback":{"type":"string"},""" +
            """"modelAnswer":{"type":"string"}},""" +
            """"required":["taskAchieve","coherence","lexical","grammar","overall"],""" +
            """"additionalProperties":false}}"""

        internal fun escape(s: String): String {
            val b = StringBuilder(s.length + 16)
            for (c in s) {
                when (c) {
                    '"' -> b.append("\\\"")
                    '\\' -> b.append("\\\\")
                    '\n' -> b.append("\\n")
                    '\r' -> b.append("\\r")
                    '\t' -> b.append("\\t")
                    else -> if (c < ' ') b.append("\\u%04x".format(c.code))
                    else b.append(c)
                }
            }
            return b.toString()
        }

        internal fun requestJson(
            model: String,
            effort: String,
            temperature: Double,
            system: String,
            user: String,
            schema: String
        ): String =
            """{"model":"${escape(model)}","reasoning_effort":"${escape(effort)}",""" +
                """"messages":[{"role":"developer","content":"${escape(system)}"},""" +
                """{"role":"user","content":"${escape(user)}"}],""" +
                """"temperature":$temperature,""" +
                """"response_format":{"type":"json_schema","json_schema":$schema}}"""

        /** Decode one JSON string literal starting at the opening quote. */
        internal fun readString(src: String, openQuote: Int): String? {
            if (openQuote < 0 || openQuote >= src.length || src[openQuote] != '"') {
                return null
            }
            val b = StringBuilder()
            var i = openQuote + 1
            while (i < src.length) {
                val c = src[i]
                if (c == '"') return b.toString()
                if (c == '\\' && i + 1 < src.length) {
                    when (src[i + 1]) {
                        '"', '\\', '/' -> b.append(src[i + 1])
                        'n' -> b.append('\n')
                        'r' -> b.append('\r')
                        't' -> b.append('\t')
                        'u' -> {
                            val hex = src.substring(i + 2, (i + 6).coerceAtMost(src.length))
                            b.append(hex.toIntOrNull(16)?.toChar() ?: '?')
                            i += 4
                        }
                        else -> b.append(src[i + 1])
                    }
                    i += 2
                } else {
                    b.append(c)
                    i++
                }
            }
            return null
        }

        /** First string value for [key] at any depth. Null when absent. */
        internal fun firstString(body: String, key: String): String? {
            var from = 0
            val needle = "\"$key\""
            while (true) {
                val k = body.indexOf(needle, from)
                if (k < 0) return null
                var i = k + needle.length
                while (i < body.length && body[i].isWhitespace()) i++
                if (i < body.length && body[i] == ':') {
                    i++
                    while (i < body.length && body[i].isWhitespace()) i++
                    if (i < body.length && body[i] == '"') {
                        return readString(body, i)
                    }
                    from = i
                } else {
                    from = i
                }
            }
        }
    }

    fun mark(
        systemPrompt: String,
        userPrompt: String,
        responseSchema: String = WRITING_SCHEMA
    ): MetaResult {
        if (config.apiKey.isBlank()) {
            return MetaResult.Failure("Meta API key is not set")
        }
        val url = config.baseUrl.trimEnd('/') + "/chat/completions"
        val body = requestJson(
            config.model, config.reasoningEffort, config.temperature,
            systemPrompt, userPrompt, responseSchema
        ).toRequestBody(JSON)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.apiKey}")
            .post(body)
            .build()
        return try {
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = firstString(text, "message") ?: resp.message
                    return MetaResult.HttpError(resp.code, msg)
                }
                val content = firstString(text, "content")
                    ?: return MetaResult.Failure("empty content in Meta response")
                MetaResult.Success(content)
            }
        } catch (e: Exception) {
            MetaResult.Failure(e.message ?: e.javaClass.simpleName)
        }
    }
}
