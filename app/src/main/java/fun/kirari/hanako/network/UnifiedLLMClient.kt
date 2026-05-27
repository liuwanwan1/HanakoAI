package `fun`.kirari.hanako.network

import `fun`.kirari.hanako.data.ModelProviderConfig
import `fun`.kirari.hanako.data.ProviderKind
import `fun`.kirari.hanako.debug.AppDebugLogStore
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

internal class UnifiedLLMClient(
    client: OkHttpClient,
    json: Json = Json { ignoreUnknownKeys = true }
) {
    private val tag = "HanakoUnifiedLLM"
    private val sseClient = SseStreamClient(client)
    private val adapters = mapOf<ProviderKind, ProviderAdapter>(
        ProviderKind.OPENAI_COMPATIBLE to OpenAiChatAdapter(sseClient, json),
        ProviderKind.OPENAI_RESPONSES to OpenAiResponsesAdapter(sseClient, json),
        ProviderKind.ANTHROPIC to AnthropicAdapter(sseClient, json),
        ProviderKind.GOOGLE to GoogleAdapter(sseClient, json)
    )

    suspend fun stream(
        provider: ModelProviderConfig,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        imageBase64: String? = null,
        tools: List<ToolDef>? = null,
        firstDeltaTimeoutMillis: Long
    ): Flow<LlmEvent> {
        AppDebugLogStore.i(tag, "stream provider=${provider.kind} model=$model hasImage=${imageBase64 != null} hasTools=${tools != null}")
        val adapter = adapters[provider.kind]
            ?: error("不支持的 provider: ${provider.kind}")
        return adapter.stream(
            StreamRequest(
                provider = provider,
                model = model,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                imageBase64 = imageBase64,
                tools = tools,
                firstDeltaTimeoutMillis = firstDeltaTimeoutMillis
            )
        )
    }
}
