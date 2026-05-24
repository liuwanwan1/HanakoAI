package `fun`.kirari.hanako.network

import `fun`.kirari.hanako.data.ModelProviderConfig
import `fun`.kirari.hanako.data.ProviderKind
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal suspend fun AiGateway.streamText(
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

internal suspend fun AiGateway.streamVision(
    provider: ModelProviderConfig,
    model: String,
    systemPrompt: String,
    userPrompt: String,
    imageBase64: String,
    onDelta: (String) -> Unit
): String {
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

internal suspend fun AiGateway.streamOpenAiChat(
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

internal suspend fun AiGateway.streamResponses(
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

internal suspend fun AiGateway.streamAnthropic(
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

internal suspend fun AiGateway.streamGoogle(
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
