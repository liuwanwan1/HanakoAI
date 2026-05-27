package `fun`.kirari.hanako.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AiGateway(
    internal val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build(),
    internal val json: Json = Json { ignoreUnknownKeys = true }
) {
    internal val sseClient = SseStreamClient(client)
    internal val JSON = "application/json; charset=utf-8".toMediaType()
}
