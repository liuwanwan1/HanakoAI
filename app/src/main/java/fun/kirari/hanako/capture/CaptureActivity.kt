package `fun`.kirari.hanako.capture

import android.app.Application
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import `fun`.kirari.hanako.data.AppSettings
import `fun`.kirari.hanako.data.ModelPurpose
import `fun`.kirari.hanako.data.ProcessingResult
import `fun`.kirari.hanako.data.ProcessingRoute
import `fun`.kirari.hanako.data.SettingsStore
import `fun`.kirari.hanako.data.resolveModelName
import `fun`.kirari.hanako.data.resolveModelProvider
import `fun`.kirari.hanako.data.toHistoryBase64
import `fun`.kirari.hanako.network.AiGateway
import `fun`.kirari.hanako.ui.theme.HanakoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CaptureActivity : ComponentActivity() {
    private val viewModel by viewModels<CaptureViewModel> {
        CaptureViewModel.factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HanakoTheme {
                CaptureScreen(
                    viewModel = viewModel,
                    onClose = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        stopService(android.content.Intent(this, MediaProjectionForegroundService::class.java))
        super.onDestroy()
    }
}

data class CaptureUiState(
    val settings: AppSettings = AppSettings(),
    val screenshot: Bitmap? = null,
    val selectedBitmap: Bitmap? = null,
    val result: ProcessingResult? = null,
    val liveOcrText: String = "",
    val liveAnswerText: String = "",
    val working: Boolean = false,
    val requestingCapture: Boolean = false,
    val showResultSheet: Boolean = false,
    val error: String? = null
)

class CaptureViewModel(
    application: Application,
    private val store: SettingsStore,
    private val gateway: AiGateway
) : ViewModel() {
    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            store.settings.collect { settings ->
                _uiState.value = _uiState.value.copy(settings = settings)
            }
        }
    }

    fun markRequestingCapture() {
        _uiState.value = _uiState.value.copy(requestingCapture = true, error = null)
    }

    fun captureScreen() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    ProjectionSessionManager.captureLatestBitmap()
                }
            }.onSuccess { bitmap ->
                _uiState.value = _uiState.value.copy(
                    requestingCapture = false,
                    screenshot = bitmap,
                    error = null
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    requestingCapture = false,
                    error = error.message ?: "截屏失败"
                )
            }
        }
    }

    fun captureScreenFromStoredPermission() {
        _uiState.value = _uiState.value.copy(
            requestingCapture = false,
            error = "屏幕捕捉会话未初始化，请重新启动悬浮球"
        )
    }

    fun process(bitmap: Bitmap) {
        val state = _uiState.value
        val assistant = state.settings.assistants.firstOrNull { it.id == state.settings.selectedAssistantId }
            ?: return
        val ocrProvider = state.settings.resolveModelProvider(ModelPurpose.OCR)
        val ocrModel = state.settings.resolveModelName(ModelPurpose.OCR)
        val textProvider = state.settings.resolveModelProvider(ModelPurpose.TEXT)
        val textModel = state.settings.resolveModelName(ModelPurpose.TEXT)
        val visionProvider = state.settings.resolveModelProvider(ModelPurpose.VISION)
        val visionModel = state.settings.resolveModelName(ModelPurpose.VISION)

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                selectedBitmap = bitmap,
                working = true,
                error = null,
                result = null,
                liveOcrText = "",
                liveAnswerText = "",
                showResultSheet = true
            )
            runCatching {
                when (state.settings.processingRoute) {
                    ProcessingRoute.OCR_THEN_LLM -> {
                        if (ocrProvider == null || ocrModel.isBlank() || textProvider == null || textModel.isBlank()) {
                            error("请先在模型设置中配置 OCR 和文本模型")
                        }
                        val (ocrText, answer) = gateway.streamOcrThenChat(
                            ocrProvider = ocrProvider,
                            ocrModel = ocrModel,
                            textProvider = textProvider,
                            textModel = textModel,
                            assistant = assistant,
                            bitmap = bitmap,
                            onOcrDelta = { delta ->
                                _uiState.value = _uiState.value.copy(
                                    liveOcrText = _uiState.value.liveOcrText + delta
                                )
                            },
                            onAnswerDelta = { delta ->
                                _uiState.value = _uiState.value.copy(
                                    liveAnswerText = _uiState.value.liveAnswerText + delta
                                )
                            }
                        )
                        ProcessingResult(
                            assistantName = assistant.name,
                            route = ProcessingRoute.OCR_THEN_LLM,
                            modelSummary = buildModelSummary(textModel, textProvider?.name),
                            extractedText = ocrText,
                            answer = answer,
                            screenshotBase64 = bitmap.toHistoryBase64()
                        )
                    }

                    ProcessingRoute.MULTIMODAL_DIRECT -> {
                        if (visionProvider == null || visionModel.isBlank()) {
                            error("请先在模型设置中配置多模态模型")
                        }
                        val answer = gateway.streamVisionDirect(
                            provider = visionProvider,
                            model = visionModel,
                            assistant = assistant,
                            bitmap = bitmap,
                            onAnswerDelta = { delta ->
                                _uiState.value = _uiState.value.copy(
                                    liveAnswerText = _uiState.value.liveAnswerText + delta
                                )
                            }
                        )
                        ProcessingResult(
                            assistantName = assistant.name,
                            route = ProcessingRoute.MULTIMODAL_DIRECT,
                            modelSummary = buildModelSummary(visionModel, visionProvider?.name),
                            answer = answer,
                            screenshotBase64 = bitmap.toHistoryBase64()
                        )
                    }
                }
            }.onSuccess { result ->
                store.update { it.copy(lastResult = result) }
                _uiState.value = _uiState.value.copy(
                    working = false,
                    result = result,
                    liveOcrText = result.extractedText,
                    liveAnswerText = result.answer
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    working = false,
                    error = error.message ?: "处理失败"
                )
            }
        }
    }

    fun closeResultSheet() {
        _uiState.value = _uiState.value.copy(showResultSheet = false)
    }

    private fun buildModelSummary(model: String, providerName: String?): String {
        val trimmedModel = model.trim()
        val trimmedProvider = providerName?.trim().orEmpty()
        if (trimmedModel.isBlank()) return ""
        return if (trimmedProvider.isBlank()) trimmedModel else "$trimmedModel（$trimmedProvider）"
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CaptureViewModel(
                        application = application,
                        store = SettingsStore(application),
                        gateway = AiGateway()
                    ) as T
                }
            }
    }
}
