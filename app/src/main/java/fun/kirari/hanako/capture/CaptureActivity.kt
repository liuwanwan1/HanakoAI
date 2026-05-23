package `fun`.kirari.hanako.capture

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import `fun`.kirari.hanako.data.AppSettings
import `fun`.kirari.hanako.data.ModelPurpose
import `fun`.kirari.hanako.data.ProcessingResult
import `fun`.kirari.hanako.data.ProcessingRoute
import `fun`.kirari.hanako.data.resolveModelName
import `fun`.kirari.hanako.data.resolveModelProvider
import `fun`.kirari.hanako.data.SettingsStore
import `fun`.kirari.hanako.network.AiGateway
import `fun`.kirari.hanako.overlay.MarkdownLatexText
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
        stopService(Intent(this, MediaProjectionForegroundService::class.java))
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
    private val appContext = application.applicationContext
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
                            extractedText = ocrText,
                            answer = answer
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
                            answer = answer
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

@Composable
private fun CaptureScreen(
    viewModel: CaptureViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        if (uiState.screenshot == null && !uiState.requestingCapture) {
            viewModel.markRequestingCapture()
            if (ProjectionSessionManager.hasActiveSession()) {
                viewModel.captureScreen()
            } else {
                viewModel.captureScreenFromStoredPermission()
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.screenshot == null -> {
                if (uiState.error != null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = uiState.error ?: "截屏失败",
                                modifier = Modifier.padding(top = 16.dp)
                            )
                            OutlinedButton(
                                onClick = {
                                    context.stopService(Intent(context, MediaProjectionForegroundService::class.java))
                                    onClose()
                                },
                                modifier = Modifier.padding(top = 16.dp)
                            ) {
                                Text("关闭")
                            }
                        }
                    }
                }
            }

            else -> {
                val screenshot = uiState.screenshot ?: return@Surface
                if (uiState.showResultSheet) {
                    ProcessingResultSheet(
                        originalBitmap = uiState.selectedBitmap ?: screenshot,
                        uiState = uiState,
                        onClose = {
                            viewModel.closeResultSheet()
                            onClose()
                        }
                    )
                } else {
                    CropAndProcessContent(
                        bitmap = screenshot,
                        uiState = uiState,
                        onClose = onClose,
                        onConfirm = viewModel::process
                    )
                }
            }
        }
    }
}

@Composable
private fun CropAndProcessContent(
    bitmap: Bitmap,
    uiState: CaptureUiState,
    onClose: () -> Unit,
    onConfirm: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var cropStart by remember { mutableStateOf(Offset.Zero) }
    var cropEnd by remember { mutableStateOf(Offset.Zero) }
    var sheetOffsetPx by remember { mutableStateOf(0f) }
    val selectedAssistant = uiState.settings.assistants.firstOrNull { it.id == uiState.settings.selectedAssistantId }
    val primaryColor = MaterialTheme.colorScheme.primary
    val routeText = if (uiState.settings.processingRoute == ProcessingRoute.OCR_THEN_LLM) {
        "先 OCR 再发送"
    } else {
        "直接发给多模态"
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor.Black.copy(alpha = 0.08f))
    ) {
        val panelPeekHeight = maxHeight * 0.88f
        val panelMaxOffsetPx = with(density) { (panelPeekHeight - 140.dp).toPx() }.coerceAtLeast(0f)

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(panelPeekHeight)
                .align(Alignment.BottomCenter)
                .offset { IntOffset(0, sheetOffsetPx.toInt()) }
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .pointerInput(panelMaxOffsetPx) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                sheetOffsetPx = (sheetOffsetPx + dragAmount.y).coerceIn(0f, panelMaxOffsetPx)
                                if (sheetOffsetPx >= panelMaxOffsetPx) {
                                    context.stopService(Intent(context, MediaProjectionForegroundService::class.java))
                                    onClose()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Spacer(
                            modifier = Modifier
                                .padding(vertical = 3.dp)
                                .fillMaxWidth(0.14f)
                                .height(6.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("助手：${selectedAssistant?.name.orEmpty()}", style = MaterialTheme.typography.titleMedium)
                    Text("处理方式：$routeText", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "拖动顶部抓手可调整面板高度，拖到底会关闭。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(ComposeColor.Black)
                            .onSizeChanged { size ->
                                canvasSize = size
                                if (cropEnd == Offset.Zero) {
                                    cropStart = Offset(size.width * 0.12f, size.height * 0.12f)
                                    cropEnd = Offset(size.width * 0.88f, size.height * 0.88f)
                                }
                            }
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(canvasSize) {
                                    detectDragGestures(
                                        onDragStart = {
                                            cropStart = it
                                            cropEnd = it
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            cropEnd += dragAmount
                                        }
                                    )
                                }
                        ) {
                            val topLeft = Offset(minOf(cropStart.x, cropEnd.x), minOf(cropStart.y, cropEnd.y))
                            val size = Size(
                                width = kotlin.math.abs(cropEnd.x - cropStart.x),
                                height = kotlin.math.abs(cropEnd.y - cropStart.y)
                            )
                            val right = topLeft.x + size.width
                            val bottom = topLeft.y + size.height
                            val overlayColor = ComposeColor.Black.copy(alpha = 0.42f)

                            drawRect(
                                color = overlayColor,
                                topLeft = Offset.Zero,
                                size = Size(this.size.width, topLeft.y.coerceAtLeast(0f))
                            )
                            drawRect(
                                color = overlayColor,
                                topLeft = Offset.Zero,
                                size = Size(topLeft.x.coerceAtLeast(0f), this.size.height)
                            )
                            drawRect(
                                color = overlayColor,
                                topLeft = Offset(right.coerceAtMost(this.size.width), 0f),
                                size = Size((this.size.width - right).coerceAtLeast(0f), this.size.height)
                            )
                            drawRect(
                                color = overlayColor,
                                topLeft = Offset(0f, bottom.coerceAtMost(this.size.height)),
                                size = Size(this.size.width, (this.size.height - bottom).coerceAtLeast(0f))
                            )
                            drawRect(
                                color = primaryColor,
                                topLeft = topLeft,
                                size = size,
                                style = Stroke(width = 4f)
                            )
                        }
                    }
                    uiState.result?.let {
                        if (it.extractedText.isNotBlank()) {
                            Text("OCR：${it.extractedText}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("回答：${it.answer}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                context.stopService(Intent(context, MediaProjectionForegroundService::class.java))
                                onClose()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("关闭")
                        }
                        Button(
                            onClick = {
                                val cropped = cropBitmap(bitmap, cropStart, cropEnd, canvasSize)
                                onConfirm(cropped)
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.working
                        ) {
                            if (uiState.working) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("确认处理")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessingResultSheet(
    originalBitmap: Bitmap,
    uiState: CaptureUiState,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var sheetOffsetPx by remember { mutableStateOf(0f) }
    val scrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor.Black.copy(alpha = 0.08f))
    ) {
        val panelPeekHeight = maxHeight * 0.92f
        val panelMaxOffsetPx = with(density) { (panelPeekHeight - 120.dp).toPx() }.coerceAtLeast(0f)

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(panelPeekHeight)
                .align(Alignment.BottomCenter)
                .offset { IntOffset(0, sheetOffsetPx.toInt()) }
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .pointerInput(panelMaxOffsetPx) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                sheetOffsetPx = (sheetOffsetPx + dragAmount.y).coerceIn(0f, panelMaxOffsetPx)
                                if (sheetOffsetPx >= panelMaxOffsetPx) {
                                    context.stopService(Intent(context, MediaProjectionForegroundService::class.java))
                                    onClose()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Spacer(
                            modifier = Modifier
                                .padding(vertical = 3.dp)
                                .fillMaxWidth(0.14f)
                                .height(6.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("处理中", style = MaterialTheme.typography.titleLarge)
                    ResultCard(title = "原图") {
                        Image(
                            bitmap = originalBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp)),
                            contentScale = ContentScale.FillWidth
                        )
                    }
                    if (uiState.settings.processingRoute == ProcessingRoute.OCR_THEN_LLM) {
                        ResultCard(title = "OCR 结果") {
                            if (uiState.liveOcrText.isBlank() && uiState.working) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(strokeWidth = 2.dp)
                                    Text("正在识别文字…")
                                }
                            } else {
                                Text(uiState.liveOcrText.ifBlank { "暂无内容" })
                            }
                        }
                    }
                    ResultCard(title = "答案") {
                        when {
                            uiState.liveAnswerText.isNotBlank() -> MarkdownLatexText(
                                content = uiState.liveAnswerText,
                                modifier = Modifier.fillMaxWidth()
                            )
                            uiState.working -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(strokeWidth = 2.dp)
                                    Text("正在生成答案…")
                                }
                            }
                            else -> Text("暂无内容")
                        }
                    }
                    uiState.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedButton(
                        onClick = {
                            context.stopService(Intent(context, MediaProjectionForegroundService::class.java))
                            onClose()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (uiState.working) "后台处理中，先关闭" else "关闭")
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

private fun cropBitmap(
    source: Bitmap,
    start: Offset,
    end: Offset,
    canvasSize: IntSize
): Bitmap {
    if (canvasSize.width == 0 || canvasSize.height == 0) return source
    val left = minOf(start.x, end.x).coerceIn(0f, canvasSize.width.toFloat())
    val top = minOf(start.y, end.y).coerceIn(0f, canvasSize.height.toFloat())
    val right = maxOf(start.x, end.x).coerceIn(0f, canvasSize.width.toFloat())
    val bottom = maxOf(start.y, end.y).coerceIn(0f, canvasSize.height.toFloat())
    val scaleX = source.width.toFloat() / canvasSize.width.toFloat()
    val scaleY = source.height.toFloat() / canvasSize.height.toFloat()
    val cropLeft = (left * scaleX).toInt().coerceIn(0, source.width - 1)
    val cropTop = (top * scaleY).toInt().coerceIn(0, source.height - 1)
    val cropWidth = ((right - left) * scaleX).toInt().coerceAtLeast(1)
        .coerceAtMost(source.width - cropLeft)
    val cropHeight = ((bottom - top) * scaleY).toInt().coerceAtLeast(1)
        .coerceAtMost(source.height - cropTop)
    return Bitmap.createBitmap(source, cropLeft, cropTop, cropWidth, cropHeight)
}
