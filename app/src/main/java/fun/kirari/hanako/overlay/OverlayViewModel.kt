package `fun`.kirari.hanako.overlay

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import `fun`.kirari.hanako.capture.ProjectionSessionManager
import `fun`.kirari.hanako.data.ProcessingResult
import `fun`.kirari.hanako.data.ProcessingRoute
import `fun`.kirari.hanako.data.SettingsStore
import `fun`.kirari.hanako.network.AiGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class OverlayViewModel(
    private val appContext: Context,
    private val store: SettingsStore,
    private val gateway: AiGateway
) : ViewModel() {
    private val _uiState = MutableStateFlow(OverlayUiState())
    val uiState: StateFlow<OverlayUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            store.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    fun openCropSheet() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { ProjectionSessionManager.captureLatestBitmap() }
            }.onSuccess { bitmap ->
                Log.d("OverlayService", "openCropSheet success bitmap=${bitmap.width}x${bitmap.height}")
                _uiState.update {
                    it.copy(
                        screenshot = bitmap,
                        selectedBitmap = null,
                        liveOcrText = "",
                        liveAnswerText = "",
                        result = null,
                        error = null,
                        working = false,
                        sheetVisible = true,
                        sheetMode = OverlaySheetMode.CROP
                    )
                }
            }.onFailure { error ->
                Log.e("OverlayService", "openCropSheet failed", error)
                _uiState.update {
                    it.copy(
                        error = error.message ?: "截屏失败",
                        sheetVisible = true,
                        sheetMode = OverlaySheetMode.CROP
                    )
                }
            }
        }
    }

    fun process(bitmap: Bitmap) {
        val state = _uiState.value
        val provider = state.settings.providers.firstOrNull { it.id == state.settings.selectedProviderId } ?: return
        val assistant = state.settings.assistants.firstOrNull { it.id == state.settings.selectedAssistantId } ?: return

        viewModelScope.launch {
            Log.d(
                "OverlayService",
                "process start bitmap=${bitmap.width}x${bitmap.height} route=${state.settings.processingRoute} assistant=${assistant.name}"
            )
            _uiState.update {
                it.copy(
                    selectedBitmap = bitmap,
                    liveOcrText = "",
                    liveAnswerText = "",
                    result = null,
                    error = null,
                    working = true,
                    sheetVisible = true,
                    sheetMode = OverlaySheetMode.RESULT
                )
            }
            Log.d("OverlayService", "process switched to RESULT sheet")
            runCatching {
                when (state.settings.processingRoute) {
                    ProcessingRoute.OCR_THEN_LLM -> {
                        val (ocrText, answer) = gateway.streamOcrThenChat(
                            provider = provider,
                            assistant = assistant,
                            bitmap = bitmap,
                            onOcrDelta = { delta ->
                                _uiState.update { current ->
                                    current.copy(liveOcrText = current.liveOcrText + delta)
                                }
                                Log.d("OverlayService", "ocr delta len=${delta.length}")
                            },
                            onAnswerDelta = { delta ->
                                _uiState.update { current ->
                                    current.copy(liveAnswerText = current.liveAnswerText + delta)
                                }
                                Log.d("OverlayService", "answer delta len=${delta.length}")
                            }
                        )
                        ProcessingResult(
                            assistantName = assistant.name,
                            route = ProcessingRoute.OCR_THEN_LLM,
                            extractedText = ocrText,
                            answer = answer
                        )
                    }

                    ProcessingRoute.MULTIMODAL_DIRECT -> {
                        val answer = gateway.streamVisionDirect(
                            provider = provider,
                            assistant = assistant,
                            bitmap = bitmap,
                            onAnswerDelta = { delta ->
                                _uiState.update { current ->
                                    current.copy(liveAnswerText = current.liveAnswerText + delta)
                                }
                                Log.d("OverlayService", "answer delta len=${delta.length}")
                            }
                        )
                        ProcessingResult(
                            assistantName = assistant.name,
                            route = ProcessingRoute.MULTIMODAL_DIRECT,
                            answer = answer
                        )
                    }
                }
            }.onSuccess { result ->
                Log.d("OverlayService", "process success answer=${result.answer.length} ocr=${result.extractedText.length}")
                store.update { it.copy(lastResult = result) }
                _uiState.update {
                    it.copy(
                        working = false,
                        result = result,
                        liveOcrText = result.extractedText,
                        liveAnswerText = result.answer
                    )
                }
            }.onFailure { error ->
                Log.e("OverlayService", "process failed", error)
                _uiState.update {
                    it.copy(
                        working = false,
                        error = error.message ?: "处理失败"
                    )
                }
            }
        }
    }

    fun closeSheet() {
        _uiState.update { it.copy(sheetVisible = false, error = null) }
    }

    companion object {
        fun factory(appContext: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return OverlayViewModel(
                        appContext = appContext,
                        store = SettingsStore(appContext),
                        gateway = AiGateway()
                    ) as T
                }
            }
    }
}
