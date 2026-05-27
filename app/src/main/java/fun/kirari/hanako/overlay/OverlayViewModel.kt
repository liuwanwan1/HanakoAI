package `fun`.kirari.hanako.overlay

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import `fun`.kirari.hanako.AppContainer
import `fun`.kirari.hanako.data.AutomationActionType
import `fun`.kirari.hanako.data.ModelPurpose
import `fun`.kirari.hanako.data.ProcessingEvent
import `fun`.kirari.hanako.data.ProcessingResult
import `fun`.kirari.hanako.data.ProcessingRoute
import `fun`.kirari.hanako.data.ProcessingStatus
import `fun`.kirari.hanako.data.ModelSelection
import `fun`.kirari.hanako.data.SettingsRepository
import `fun`.kirari.hanako.debug.AppDebugLogStore
import `fun`.kirari.hanako.localocr.LocalOcrManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal class OverlayViewModel(
    private val appContext: Context,
    private val repository: SettingsRepository,
    private val pipeline: ProcessingPipeline
) : ViewModel() {
    private val tag = "HanakoOverlayVM"
    private val processingTimeoutMillis = 90_000L
    private val _uiState = MutableStateFlow(OverlayUiState())
    val uiState: StateFlow<OverlayUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    fun setLaunchMode(mode: OverlayLaunchMode) {
        AppDebugLogStore.i(tag, "setLaunchMode mode=$mode")
        _uiState.update { state ->
            state.copy(
                launchMode = mode,
                autoRunState = if (mode == OverlayLaunchMode.NORMAL) AutoRunState.IDLE else state.autoRunState,
                autoCopiedLabel = if (mode == OverlayLaunchMode.NORMAL) null else state.autoCopiedLabel,
                bubbleDisplayState = if (mode == OverlayLaunchMode.NORMAL) `fun`.kirari.hanako.automation.BubbleDisplayState.IDLE else state.bubbleDisplayState,
                bubbleLetters = if (mode == OverlayLaunchMode.NORMAL) null else state.bubbleLetters,
                error = null
            )
        }
    }

    fun openCropSheet() {
        AppDebugLogStore.i(tag, "openCropSheet launchMode=${_uiState.value.launchMode}")
        if (_uiState.value.launchMode == OverlayLaunchMode.AUTO) {
            AppDebugLogStore.i(tag, "openCropSheet delegated to processFullScreen for auto mode")
            processFullScreen()
            return
        }
        viewModelScope.launch {
            runCatching {
                `fun`.kirari.hanako.capture.ScreenCaptureManager.captureLatestBitmap(appContext, _uiState.value.settings.screenCaptureMethod)
            }.onSuccess { bitmap ->
                AppDebugLogStore.i(tag, "openCropSheet capture success width=${bitmap.width} height=${bitmap.height}")
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
                        sheetMode = OverlaySheetMode.CROP,
                        autoRunState = AutoRunState.IDLE,
                        autoCopiedLabel = null,
                        bubbleDisplayState = `fun`.kirari.hanako.automation.BubbleDisplayState.IDLE,
                        bubbleLetters = null
                    )
                }
            }.onFailure { error ->
                AppDebugLogStore.e(tag, "openCropSheet failed", error)
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

    fun processFullScreen() {
        AppDebugLogStore.i(tag, "processFullScreen start launchMode=${_uiState.value.launchMode}")
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    liveOcrText = "",
                    liveAnswerText = "",
                    result = null,
                    error = null,
                    working = true,
                    sheetVisible = false,
                    autoRunState = AutoRunState.RUNNING,
                    autoCopiedLabel = null,
                    bubbleDisplayState = `fun`.kirari.hanako.automation.BubbleDisplayState.RUNNING,
                    bubbleLetters = null
                )
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    `fun`.kirari.hanako.capture.ScreenCaptureManager.captureLatestBitmap(appContext, _uiState.value.settings.screenCaptureMethod)
                }
            }.onSuccess { bitmap ->
                AppDebugLogStore.i(tag, "processFullScreen capture success width=${bitmap.width} height=${bitmap.height}")
                processAutoBitmap(bitmap)
            }.onFailure { error ->
                AppDebugLogStore.e(tag, "processFullScreen failed", error)
                _uiState.update {
                    it.copy(
                        working = false,
                        autoRunState = AutoRunState.IDLE,
                        error = error.message ?: "截屏失败"
                    )
                }
            }
        }
    }

    fun process(bitmap: Bitmap) {
        val state = _uiState.value
        AppDebugLogStore.i(tag, "process start route=${state.settings.processingRoute} bitmap=${bitmap.width}x${bitmap.height}")

        val models = runCatching { pipeline.resolveModels(state) }.getOrElse { error ->
            _uiState.update { it.copy(error = error.message) }
            return
        }
        val (baseResult, historyId, screenshotPath) = pipeline.createBaseResult(models, bitmap, "请求已开始")

        viewModelScope.launch {
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
            upsertHistory(baseResult)
            runCatching {
                withTimeout(processingTimeoutMillis) {
                    when (models.route) {
                        ProcessingRoute.OCR_THEN_LLM -> {
                            pipeline.validateOcrThenLlmModels(models)
                            val (ocrText, answer) = pipeline.streamOcrThenChat(
                                models = models,
                                bitmap = bitmap,
                                onOcrDelta = { delta ->
                                    _uiState.update { current -> current.copy(liveOcrText = current.liveOcrText + delta) }
                                },
                                onAnswerDelta = { delta ->
                                    _uiState.update { current -> current.copy(liveAnswerText = current.liveAnswerText + delta) }
                                }
                            )
                            pipeline.buildChatResult(baseResult, models, ocrText, answer, historyId, screenshotPath)
                        }

                        ProcessingRoute.MULTIMODAL_DIRECT -> {
                            pipeline.validateVisionModels(models)
                            val answer = pipeline.streamVisionDirect(
                                models = models,
                                bitmap = bitmap,
                                onAnswerDelta = { delta ->
                                    _uiState.update { current -> current.copy(liveAnswerText = current.liveAnswerText + delta) }
                                }
                            )
                            pipeline.buildChatResult(baseResult, models, "", answer, historyId, screenshotPath)
                        }
                    }
                }
            }.onSuccess { result ->
                AppDebugLogStore.i(tag, "process success resultId=${result.id} answerLength=${result.answer.length}")
                upsertHistory(result)
                _uiState.update {
                    it.copy(
                        working = false,
                        result = result,
                        liveOcrText = result.extractedText,
                        liveAnswerText = result.answer,
                        autoRunState = AutoRunState.IDLE,
                        autoCopiedLabel = null,
                        bubbleDisplayState = `fun`.kirari.hanako.automation.BubbleDisplayState.IDLE,
                        bubbleLetters = null
                    )
                }
            }.onFailure { error ->
                AppDebugLogStore.e(tag, "process failed", error)
                handleError(error, baseResult)
            }
        }
    }

    fun closeSheet() {
        _uiState.update { it.copy(sheetVisible = false, error = null) }
    }

    fun consumeAutoCompletedState() {
        AppDebugLogStore.d(tag, "consumeAutoCompletedState state=${_uiState.value.autoRunState} bubble=${_uiState.value.bubbleDisplayState}")
        _uiState.update { state ->
            if (state.launchMode == OverlayLaunchMode.AUTO && state.autoRunState == AutoRunState.COMPLETED) {
                if (state.bubbleDisplayState == `fun`.kirari.hanako.automation.BubbleDisplayState.COPIED) {
                    state.copy(autoRunState = AutoRunState.IDLE, bubbleDisplayState = `fun`.kirari.hanako.automation.BubbleDisplayState.IDLE)
                } else {
                    state.copy(autoRunState = AutoRunState.IDLE)
                }
            } else {
                state
            }
        }
    }

    fun onBubbleTappedAfterLettersShown() {
        AppDebugLogStore.i(tag, "onBubbleTappedAfterLettersShown launchMode=${_uiState.value.launchMode} bubble=${_uiState.value.bubbleDisplayState}")
        _uiState.update { state ->
            if (state.launchMode == OverlayLaunchMode.AUTO && state.bubbleDisplayState == `fun`.kirari.hanako.automation.BubbleDisplayState.SHOWING_LETTERS) {
                AppDebugLogStore.i(tag, "onBubbleTappedAfterLettersShown clearing letters and entering pending reset")
                state.copy(
                    bubbleDisplayState = `fun`.kirari.hanako.automation.BubbleDisplayState.SHOWING_LETTERS_PENDING_RESET,
                    bubbleLetters = null
                )
            } else {
                state
            }
        }
    }

    fun selectAssistant(assistantId: String) = repository.selectAssistant(viewModelScope, assistantId)

    fun selectPreviousAssistant() {
        val current = _uiState.value.settings
        val assistants = current.assistants
        if (assistants.isEmpty()) return
        val selectedIndex = assistants.indexOfFirst { it.id == current.selectedAssistantId }.takeIf { it >= 0 } ?: 0
        val previousIndex = if (selectedIndex == 0) assistants.lastIndex else selectedIndex - 1
        selectAssistant(assistants[previousIndex].id)
    }

    fun selectNextAssistant() {
        val current = _uiState.value.settings
        val assistants = current.assistants
        if (assistants.isEmpty()) return
        val selectedIndex = assistants.indexOfFirst { it.id == current.selectedAssistantId }.takeIf { it >= 0 } ?: 0
        val nextIndex = if (selectedIndex == assistants.lastIndex) 0 else selectedIndex + 1
        selectAssistant(assistants[nextIndex].id)
    }

    fun updateModelSelection(purpose: ModelPurpose, selection: ModelSelection) =
        repository.updateModelSelection(viewModelScope, purpose, selection)

    fun updateModelSelectionWithFavorite(purpose: ModelPurpose, selection: ModelSelection, favoriteModel: Boolean = false) =
        repository.updateModelSelectionWithFavorite(viewModelScope, purpose, selection, favoriteModel)

    fun toggleFavoriteModel(providerId: String, modelId: String) =
        repository.toggleFavoriteModel(viewModelScope, providerId, modelId)

    fun toggleProcessingRoute() {
        viewModelScope.launch {
            repository.update { current ->
                current.copy(
                    processingRoute = when (current.processingRoute) {
                        ProcessingRoute.OCR_THEN_LLM -> ProcessingRoute.MULTIMODAL_DIRECT
                        ProcessingRoute.MULTIMODAL_DIRECT -> ProcessingRoute.OCR_THEN_LLM
                    }
                )
            }
        }
    }

    private suspend fun processAutoBitmap(bitmap: Bitmap) {
        val state = _uiState.value
        AppDebugLogStore.i(tag, "processAutoBitmap start route=${state.settings.processingRoute} bitmap=${bitmap.width}x${bitmap.height}")

        val models = runCatching { pipeline.resolveModels(state) }.getOrElse { error ->
            _uiState.update { it.copy(working = false, autoRunState = AutoRunState.IDLE, error = error.message) }
            return
        }
        val (baseResult, historyId, screenshotPath) = pipeline.createBaseResult(models, bitmap, "自动流程已开始")
        upsertHistory(baseResult)

        runCatching<Pair<`fun`.kirari.hanako.data.AutomationActionRecord, ProcessingResult>> {
            withTimeout(processingTimeoutMillis) {
                val (action, result) = when (models.route) {
                    ProcessingRoute.OCR_THEN_LLM -> {
                        pipeline.validateOcrThenLlmModels(models)
                        val (ocrText, automationResult) = pipeline.streamOcrThenAutomation(
                            models = models,
                            bitmap = bitmap,
                            onOcrDelta = { delta ->
                                _uiState.update { current -> current.copy(liveOcrText = current.liveOcrText + delta) }
                            },
                            onThoughtDelta = { delta ->
                                _uiState.update { current -> current.copy(liveAnswerText = current.liveAnswerText + delta) }
                            }
                        )
                        pipeline.buildAutomationResult(baseResult, models, ocrText, automationResult, historyId, screenshotPath)
                    }

                    ProcessingRoute.MULTIMODAL_DIRECT -> {
                        pipeline.validateVisionModels(models)
                        val automationResult = pipeline.streamAutomationDirect(
                            models = models,
                            bitmap = bitmap,
                            onThoughtDelta = { delta ->
                                _uiState.update { current -> current.copy(liveAnswerText = current.liveAnswerText + delta) }
                            }
                        )
                        pipeline.buildAutomationResult(baseResult, models, "", automationResult, historyId, screenshotPath)
                    }
                }
                AppDebugLogStore.i(tag, "processAutoBitmap gateway success resultId=${result.id} action=${action.type}")
                upsertHistory(result)
                action to result
            }
        }.onSuccess { (action, result) ->
            val bubbleState = when (action.type) {
                AutomationActionType.SET_CLIPBOARD -> `fun`.kirari.hanako.automation.BubbleDisplayState.COPIED
                AutomationActionType.SHOW_BUBBLE_LETTERS -> `fun`.kirari.hanako.automation.BubbleDisplayState.SHOWING_LETTERS
            }
            _uiState.update {
                it.copy(
                    screenshot = bitmap,
                    selectedBitmap = bitmap,
                    working = false,
                    result = result,
                    liveAnswerText = result.automationThought,
                    autoRunState = AutoRunState.COMPLETED,
                    autoCopiedLabel = action.text.takeIf { action.type == AutomationActionType.SET_CLIPBOARD },
                    bubbleDisplayState = bubbleState,
                    bubbleLetters = action.text.takeIf { action.type == AutomationActionType.SHOW_BUBBLE_LETTERS },
                    error = null
                )
            }
        }.onFailure { error ->
            AppDebugLogStore.e(tag, "processAutoBitmap failed", error)
            handleError(error, baseResult, isAutoMode = true)
        }
    }

    private suspend fun handleError(error: Throwable, baseResult: ProcessingResult, isAutoMode: Boolean = false) {
        val isTimeout = error is TimeoutCancellationException
        val message = error.message?.ifBlank { null } ?: if (isTimeout) "请求超时（90 秒）" else "处理失败"
        upsertHistory(
            baseResult.copy(
                status = if (isTimeout) ProcessingStatus.TIMEOUT else ProcessingStatus.ERROR,
                detail = message,
                extractedText = _uiState.value.liveOcrText,
                answer = if (isAutoMode) "" else _uiState.value.liveAnswerText,
                automationThought = if (isAutoMode) _uiState.value.liveAnswerText.orEmpty() else "",
                events = baseResult.events + ProcessingEvent(
                    title = if (isTimeout) "请求超时" else "请求失败",
                    detail = message
                )
            )
        )
        _uiState.update {
            it.copy(
                working = false,
                autoRunState = AutoRunState.IDLE,
                bubbleDisplayState = if (isAutoMode) `fun`.kirari.hanako.automation.BubbleDisplayState.IDLE else it.bubbleDisplayState,
                bubbleLetters = if (isAutoMode) null else it.bubbleLetters,
                error = message
            )
        }
    }

    private suspend fun upsertHistory(result: ProcessingResult) {
        repository.update { current ->
            val history = listOf(result) + current.history.filterNot { it.id == result.id }
            current.copy(lastResult = result, history = history)
        }
    }

    companion object {
        fun factory(appContext: Context): ViewModelProvider.Factory {
            val container = (appContext.applicationContext as `fun`.kirari.hanako.HanakoApplication).container
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return OverlayViewModel(
                        appContext = appContext,
                        repository = container.settingsRepository,
                        pipeline = ProcessingPipeline(appContext, container.unifiedLLMClient, container.localOcrManager)
                    ) as T
                }
            }
        }
    }
}
