package `fun`.kirari.hanako.network

import android.graphics.Bitmap
import `fun`.kirari.hanako.data.AssistantPreset
import `fun`.kirari.hanako.data.ModelProviderConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient

class AiGateway(
    private val client: OkHttpClient = OkHttpClient(),
    internal val json: Json = Json { ignoreUnknownKeys = true }
) {
    internal val sseClient = SseStreamClient(client)
    internal val JSON = "application/json; charset=utf-8".toMediaType()

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
            imageBase64 = bitmap.toBase64Jpeg(),
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
            imageBase64 = bitmap.toBase64Jpeg(),
            onDelta = onAnswerDelta
        )
    }
}
