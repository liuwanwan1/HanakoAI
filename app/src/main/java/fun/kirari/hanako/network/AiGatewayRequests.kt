package `fun`.kirari.hanako.network

import `fun`.kirari.hanako.data.ModelProviderConfig
import `fun`.kirari.hanako.data.ProviderKind
import kotlinx.serialization.json.JsonObject
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal fun AiGateway.baseRequest(
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
