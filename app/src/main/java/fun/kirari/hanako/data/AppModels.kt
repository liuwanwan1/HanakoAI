package `fun`.kirari.hanako.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class ProviderKind {
    OPENAI_COMPATIBLE,
    OPENAI_RESPONSES,
    ANTHROPIC,
    GOOGLE
}

@Serializable
data class ModelProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "OpenAI Compatible",
    val kind: ProviderKind = ProviderKind.OPENAI_COMPATIBLE,
    val baseUrl: String = kind.defaultBaseUrl,
    val apiKey: String = "",
    val chatModel: String = "gpt-4o-mini",
    val visionModel: String = "gpt-4o",
    val ocrModel: String = "gpt-4.1-mini",
    val enabled: Boolean = true
)

val ProviderKind.displayName: String
    get() = when (this) {
        ProviderKind.OPENAI_COMPATIBLE -> "OpenAI Compatible"
        ProviderKind.OPENAI_RESPONSES -> "OpenAI Responses"
        ProviderKind.ANTHROPIC -> "Anthropic"
        ProviderKind.GOOGLE -> "Google Gemini"
    }

val ProviderKind.defaultBaseUrl: String
    get() = when (this) {
        ProviderKind.OPENAI_COMPATIBLE -> "https://api.openai.com/v1"
        ProviderKind.OPENAI_RESPONSES -> "https://api.openai.com/v1"
        ProviderKind.ANTHROPIC -> "https://api.anthropic.com/v1"
        ProviderKind.GOOGLE -> "https://generativelanguage.googleapis.com/v1beta"
    }

val ProviderKind.modelsRequestSuffix: String
    get() = when (this) {
        ProviderKind.OPENAI_COMPATIBLE -> "/models"
        ProviderKind.OPENAI_RESPONSES -> "/models"
        ProviderKind.ANTHROPIC -> "/models"
        ProviderKind.GOOGLE -> "/models?pageSize=100"
    }

val ProviderKind.requestPathSuffix: String
    get() = when (this) {
        ProviderKind.OPENAI_COMPATIBLE -> "/chat/completions"
        ProviderKind.OPENAI_RESPONSES -> "/responses"
        ProviderKind.ANTHROPIC -> "/messages"
        ProviderKind.GOOGLE -> "/models"
    }

fun ModelProviderConfig.requestPreviewUrl(): String = "${baseUrl.trimEnd('/')}${kind.requestPathSuffix}"

fun ModelProviderConfig.modelsRequestUrl(): String = "${baseUrl.trimEnd('/')}${kind.modelsRequestSuffix}"

@Serializable
data class AssistantPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val systemPrompt: String
)

@Serializable
enum class ProcessingRoute {
    OCR_THEN_LLM,
    MULTIMODAL_DIRECT
}

@Serializable
data class AppSettings(
    val providers: List<ModelProviderConfig> = listOf(defaultProvider()),
    val selectedProviderId: String? = providers.firstOrNull()?.id,
    val assistants: List<AssistantPreset> = defaultAssistants(),
    val selectedAssistantId: String? = assistants.firstOrNull()?.id,
    val processingRoute: ProcessingRoute = ProcessingRoute.OCR_THEN_LLM,
    val overlayEnabled: Boolean = false,
    val lastResult: ProcessingResult? = null
)

@Serializable
data class ProcessingResult(
    val assistantName: String,
    val route: ProcessingRoute,
    val extractedText: String = "",
    val answer: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)

fun defaultProvider(): ModelProviderConfig = ModelProviderConfig()

fun defaultAssistants(): List<AssistantPreset> = listOf(
    AssistantPreset(
        name = "聊天记录总结助手",
        systemPrompt = "你是聊天记录总结助手。请提炼重点、待办、情绪倾向，并用简洁中文输出。"
    ),
    AssistantPreset(
        name = "题目解答助手",
        systemPrompt = "你是题目解答助手。请先识别题目内容，再给出解题思路、关键知识点和答案。"
    )
)
