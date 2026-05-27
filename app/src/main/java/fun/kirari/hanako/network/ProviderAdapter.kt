package `fun`.kirari.hanako.network

import `fun`.kirari.hanako.data.ModelProviderConfig
import kotlinx.coroutines.flow.Flow

internal data class StreamRequest(
    val provider: ModelProviderConfig,
    val model: String,
    val systemPrompt: String,
    val userPrompt: String,
    val imageBase64: String? = null,
    val tools: List<ToolDef>? = null,
    val firstDeltaTimeoutMillis: Long
)

internal interface ProviderAdapter {
    suspend fun stream(request: StreamRequest): Flow<LlmEvent>
}

internal class PendingToolCall(
    var name: String? = null,
    val arguments: StringBuilder = StringBuilder()
)
