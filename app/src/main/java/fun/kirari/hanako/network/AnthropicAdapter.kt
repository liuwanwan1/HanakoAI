package `fun`.kirari.hanako.network

import `fun`.kirari.hanako.debug.AppDebugLogStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType

internal class AnthropicAdapter(
    private val sseClient: SseStreamClient,
    private val json: Json
) : ProviderAdapter {
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun stream(request: StreamRequest): Flow<LlmEvent> = callbackFlow {
        val payload = buildJsonObject {
            put("model", request.model)
            put("stream", true)
            put("max_tokens", 4096)
            put("system", request.systemPrompt)
            request.tools?.let {
                put("tool_choice", buildJsonObject { put("type", "any") })
                put("tools", ToolRegistry.formatForProvider(it, request.provider.kind))
            }
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", request.userPrompt)
                        })
                        request.imagesBase64.forEach { imageBase64 ->
                            add(buildJsonObject {
                                put("type", "image")
                                put("source", buildJsonObject {
                                    put("type", "base64")
                                    put("media_type", "image/jpeg")
                                    put("data", imageBase64)
                                })
                            })
                        }
                    })
                })
            })
        }

        val toolCallsByIndex = linkedMapOf<Int, PendingToolCall>()

        sseClient.stream(
            request = baseRequest(
                request.provider,
                "${request.provider.baseUrl.trimEnd('/')}/messages",
                payload,
                json,
                mediaType,
                headers = mapOf("anthropic-version" to "2023-06-01")
            ),
            firstDeltaTimeoutMillis = request.firstDeltaTimeoutMillis,
            onEvent = { _, type, _, data ->
                if (data == "[DONE]") return@stream SseStreamClient.StreamEventResult(done = true)
                val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull()
                    ?: return@stream null
                when (type) {
                    "content_block_start" -> {
                        val index = root["index"]?.jsonPrimitive?.intOrNull ?: return@stream null
                        val block = root["content_block"]?.jsonObject ?: return@stream null
                        if (block["type"]?.jsonPrimitive?.contentOrNull == "tool_use") {
                            val tc = toolCallsByIndex.getOrPut(index) { PendingToolCall() }
                            tc.name = block["name"]?.jsonPrimitive?.contentOrNull
                            block["input"]?.jsonObject?.takeIf { it.isNotEmpty() }?.let {
                                tc.arguments.clear()
                                tc.arguments.append(json.encodeToString(JsonObject.serializer(), it))
                            }
                        }
                        null
                    }

                    "content_block_delta" -> {
                        val index = root["index"]?.jsonPrimitive?.intOrNull ?: return@stream null
                        val delta = root["delta"]?.jsonObject ?: return@stream null
                        when (delta["type"]?.jsonPrimitive?.contentOrNull) {
                            "text_delta" -> {
                                val text = delta["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                if (text.isNotBlank()) {
                                    trySend(LlmEvent.TextDelta(text))
                                }
                                SseStreamClient.StreamEventResult(delta = text)
                            }

                            "input_json_delta" -> {
                                val tc = toolCallsByIndex.getOrPut(index) { PendingToolCall() }
                                tc.arguments.append(delta["partial_json"]?.jsonPrimitive?.contentOrNull.orEmpty())
                                null
                            }

                            else -> null
                        }
                    }

                    else -> null
                }
            },
            onDelta = {}
        )

        for (tc in toolCallsByIndex.values) {
            val name = tc.name ?: continue
            val args = runCatching { json.parseToJsonElement(tc.arguments.toString()).jsonObject }.getOrNull()
                ?: continue
            trySend(LlmEvent.ToolCall(name, args))
        }
        trySend(LlmEvent.Done)
        close()

        awaitClose()
    }
}
