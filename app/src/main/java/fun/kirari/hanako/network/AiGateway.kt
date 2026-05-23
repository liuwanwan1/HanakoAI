package `fun`.kirari.hanako.network

import android.graphics.Bitmap
import `fun`.kirari.hanako.data.AssistantPreset
import `fun`.kirari.hanako.data.ModelProviderConfig
import `fun`.kirari.hanako.data.ProviderKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AiGateway(
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val sseClient = SseStreamClient(client)

    suspend fun streamOcrThenChat(
        ocrProvider: ModelProviderConfig,
        ocrModel: String,
        textProvider: ModelProviderConfig,
        textModel: String,
        assistant: AssistantPreset,
        bitmap: Bitmap,
        onOcrDelta: (String) -> Unit,
        onAnswerDelta: (String) -> Unit
    ): Pair<String, String> {
        val ocrText = streamVision(
            provider = ocrProvider,
            model = ocrModel,
            systemPrompt = "请准确提取图片中的全部文字，按原有结构输出，不要解释。",
            userPrompt = "请执行 OCR。",
            bitmap = bitmap,
            onDelta = onOcrDelta
        )
        val answer = streamText(
            provider = textProvider,
            model = textModel,
            systemPrompt = assistant.systemPrompt,
            userPrompt = "以下是 OCR 结果，请完成任务：\n$ocrText",
            onDelta = onAnswerDelta
        )
        return ocrText to answer
    }

    suspend fun streamVisionDirect(
        provider: ModelProviderConfig,
        model: String,
        assistant: AssistantPreset,
        bitmap: Bitmap,
        onAnswerDelta: (String) -> Unit
    ): String {
        return streamVision(
            provider = provider,
            model = model,
            systemPrompt = assistant.systemPrompt,
            userPrompt = "请直接基于图片内容完成任务。",
            bitmap = bitmap,
            onDelta = onAnswerDelta
        )
    }

    private suspend fun streamText(
        provider: ModelProviderConfig,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        onDelta: (String) -> Unit
    ): String {
        return when (provider.kind) {
            ProviderKind.OPENAI_RESPONSES -> streamResponses(
                provider = provider,
                model = model,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                imageBase64 = null,
                onDelta = onDelta
            )

            ProviderKind.ANTHROPIC -> streamAnthropic(
                provider = provider,
                model = model,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                imageBase64 = null,
                onDelta = onDelta
            )

            ProviderKind.GOOGLE -> streamGoogle(
                provider = provider,
                model = model,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                imageBase64 = null,
                onDelta = onDelta
            )

            ProviderKind.OPENAI_COMPATIBLE -> streamOpenAiChat(
                provider = provider,
                model = model,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                imageBase64 = null,
                onDelta = onDelta
            )
        }
    }

    private suspend fun streamVision(
        provider: ModelProviderConfig,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        bitmap: Bitmap,
        onDelta: (String) -> Unit
    ): String {
        val imageBase64 = bitmap.toBase64Jpeg()
        return when (provider.kind) {
            ProviderKind.OPENAI_RESPONSES -> streamResponses(
                provider = provider,
                model = model,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                imageBase64 = imageBase64,
                onDelta = onDelta
            )

            ProviderKind.ANTHROPIC -> streamAnthropic(
                provider = provider,
                model = model,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                imageBase64 = imageBase64,
                onDelta = onDelta
            )

            ProviderKind.GOOGLE -> streamGoogle(
                provider = provider,
                model = model,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                imageBase64 = imageBase64,
                onDelta = onDelta
            )

            ProviderKind.OPENAI_COMPATIBLE -> streamOpenAiChat(
                provider = provider,
                model = model,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                imageBase64 = imageBase64,
                onDelta = onDelta
            )
        }
    }

    private suspend fun streamOpenAiChat(
        provider: ModelProviderConfig,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        imageBase64: String?,
        onDelta: (String) -> Unit
    ): String {
        val payload = buildJsonObject {
            put("model", model)
            put("stream", true)
            put(
                "messages",
                buildJsonArray {
                    add(openAiMessage("system", systemPrompt))
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put(
                                "content",
                                buildJsonArray {
                                    add(buildJsonObject {
                                        put("type", "text")
                                        put("text", userPrompt)
                                    })
                                    imageBase64?.let {
                                        add(buildJsonObject {
                                            put("type", "image_url")
                                            put("image_url", buildJsonObject {
                                                put("url", "data:image/jpeg;base64,$it")
                                                put("detail", "high")
                                            })
                                        })
                                    }
                                }
                            )
                        }
                    )
                }
            )
        }

        return sseClient.stream(
            request = baseRequest(provider, "${provider.baseUrl.trimEnd('/')}/chat/completions", payload),
            onEvent = { _, _, _, data ->
                if (data == "[DONE]") {
                    null
                } else {
                    val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: null
                    root?.let { extractOpenAiChatDelta(it) }
                }
            },
            onDelta = onDelta
        )
    }

    private suspend fun streamResponses(
        provider: ModelProviderConfig,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        imageBase64: String?,
        onDelta: (String) -> Unit
    ): String {
        val payload = buildJsonObject {
            put("model", model)
            put("stream", true)
            put("instructions", systemPrompt)
            put(
                "input",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put(
                                "content",
                                buildJsonArray {
                                    add(buildJsonObject {
                                        put("type", "input_text")
                                        put("text", userPrompt)
                                    })
                                    imageBase64?.let {
                                        add(buildJsonObject {
                                            put("type", "input_image")
                                            put("image_url", "data:image/jpeg;base64,$it")
                                        })
                                    }
                                }
                            )
                        }
                    )
                }
            )
        }

        return sseClient.stream(
            request = baseRequest(provider, "${provider.baseUrl.trimEnd('/')}/responses", payload),
            onEvent = { _, type, _, data ->
                if (data == "[DONE]") {
                    null
                } else {
                    val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: null
                    when (type) {
                        "response.output_text.delta" -> root?.get("delta")?.jsonPrimitive?.contentOrNull
                        else -> null
                    }
                }
            },
            onDelta = onDelta
        )
    }

    private suspend fun streamAnthropic(
        provider: ModelProviderConfig,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        imageBase64: String?,
        onDelta: (String) -> Unit
    ): String {
        val payload = buildJsonObject {
            put("model", model)
            put("stream", true)
            put("max_tokens", 4096)
            put("system", systemPrompt)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put(
                                "content",
                                buildJsonArray {
                                    add(buildJsonObject {
                                        put("type", "text")
                                        put("text", userPrompt)
                                    })
                                    imageBase64?.let {
                                        add(buildJsonObject {
                                            put("type", "image")
                                            put("source", buildJsonObject {
                                                put("type", "base64")
                                                put("media_type", "image/jpeg")
                                                put("data", it)
                                            })
                                        })
                                    }
                                }
                            )
                        }
                    )
                }
            )
        }

        return sseClient.stream(
            request = baseRequest(
                provider,
                "${provider.baseUrl.trimEnd('/')}/messages",
                payload,
                headers = mapOf("anthropic-version" to "2023-06-01")
            ),
            onEvent = { _, type, _, data ->
                if (data == "[DONE]") {
                    null
                } else {
                    val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: null
                    when (type) {
                        "content_block_delta" -> root?.get("delta")?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                        else -> null
                    }
                }
            },
            onDelta = onDelta
        )
    }

    private suspend fun streamGoogle(
        provider: ModelProviderConfig,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        imageBase64: String?,
        onDelta: (String) -> Unit
    ): String {
        val payload = buildJsonObject {
            put(
                "systemInstruction",
                buildJsonObject {
                    put(
                        "parts",
                        buildJsonArray {
                            add(buildJsonObject {
                                put("text", systemPrompt)
                            })
                        }
                    )
                }
            )
            put(
                "contents",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put(
                                "parts",
                                buildJsonArray {
                                    add(buildJsonObject {
                                        put("text", userPrompt)
                                    })
                                    imageBase64?.let {
                                        add(buildJsonObject {
                                            put("inlineData", buildJsonObject {
                                                put("mimeType", "image/jpeg")
                                                put("data", it)
                                            })
                                        })
                                    }
                                }
                            )
                        }
                    )
                }
            )
        }

        val url = "${provider.baseUrl.trimEnd('/')}/models/$model:streamGenerateContent?alt=sse"
        return sseClient.stream(
            request = baseRequest(
                provider,
                url,
                payload,
                googleApiKeyHeader = true
            ),
            onEvent = { _, _, _, data ->
                val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: null
                val candidates = root?.get("candidates")?.jsonArray ?: null
                candidates?.firstOrNull()
                    ?.jsonObject
                    ?.get("content")
                    ?.jsonObject
                    ?.get("parts")
                    ?.jsonArray
                    ?.joinToString(separator = "") { part ->
                        part.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    }
            },
            onDelta = onDelta
        )
    }

    private fun baseRequest(
        provider: ModelProviderConfig,
        url: String,
        payload: JsonObject,
        headers: Map<String, String> = emptyMap(),
        googleApiKeyHeader: Boolean = false
    ): Request {
        val base = provider.baseUrl.trimEnd('/')
        require(base.isNotBlank()) { "请先填写 Base URL" }
        require(provider.apiKey.isNotBlank()) { "请先填写 API Key" }
        val builder = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .post(json.encodeToString(JsonObject.serializer(), payload).toRequestBody(JSON))

        when (provider.kind) {
            ProviderKind.GOOGLE -> {
                builder.header("x-goog-api-key", provider.apiKey)
            }

            ProviderKind.ANTHROPIC -> {
                builder.header("x-api-key", provider.apiKey)
            }

            else -> {
                builder.header("Authorization", "Bearer ${provider.apiKey}")
            }
        }

        headers.forEach { (key, value) ->
            builder.header(key, value)
        }
        return builder.build()
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
