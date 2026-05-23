package `fun`.kirari.hanako.overlay

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.data.ProcessingRoute
import kotlin.math.abs

@Composable
internal fun OverlayPanel(
    viewModel: OverlayViewModel,
    onDismiss: () -> Unit,
    panelHeightPx: Int = 0
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(uiState.sheetMode, uiState.working, uiState.liveOcrText, uiState.liveAnswerText) {
        Log.d(
            "OverlayService",
            "OverlayPanel compose mode=${uiState.sheetMode} working=${uiState.working} ocr=${uiState.liveOcrText.length} answer=${uiState.liveAnswerText.length}"
        )
    }
    when (uiState.sheetMode) {
        OverlaySheetMode.CROP -> {
            CropOverlaySheet(
                uiState = uiState,
                onClose = onDismiss,
                onConfirm = viewModel::process,
                panelHeightPx = panelHeightPx
            )
        }

        OverlaySheetMode.RESULT -> {
            ResultOverlaySheet(
                uiState = uiState,
                onClose = onDismiss,
                panelHeightPx = panelHeightPx
            )
        }
    }
}

@Composable
private fun CropOverlaySheet(
    uiState: OverlayUiState,
    onClose: () -> Unit,
    onConfirm: (Bitmap) -> Unit,
    panelHeightPx: Int
) {
    val bitmap = uiState.screenshot
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var cropStart by remember { mutableStateOf(Offset.Zero) }
    var cropEnd by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val selectedAssistant = uiState.settings.assistants.firstOrNull { it.id == uiState.settings.selectedAssistantId }
    val routeText = if (uiState.settings.processingRoute == ProcessingRoute.OCR_THEN_LLM) "先 OCR 再发送" else "直接发给多模态"
    val selectionColor = MaterialTheme.colorScheme.primary
    val panelMaxHeight = with(density) { panelHeightPx.toDp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor.Transparent)
    ) {
        var closeRequested by remember { mutableStateOf(false) }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(panelMaxHeight)
                .align(Alignment.TopCenter),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 20.dp, top = 52.dp, end = 20.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SheetTitleRow(
                        title = "助手：${selectedAssistant?.name.orEmpty()}",
                        onClose = {
                            if (closeRequested) return@SheetTitleRow
                            closeRequested = true
                            onClose()
                        }
                    )
                    Text("处理方式：$routeText", style = MaterialTheme.typography.bodyMedium)
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
                        if (bitmap != null) {
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
                                    width = abs(cropEnd.x - cropStart.x),
                                    height = abs(cropEnd.y - cropStart.y)
                                )
                                val right = topLeft.x + size.width
                                val bottom = topLeft.y + size.height
                                val overlayColor = ComposeColor.Black.copy(alpha = 0.42f)
                                drawRect(overlayColor, topLeft = Offset.Zero, size = Size(this.size.width, topLeft.y.coerceAtLeast(0f)))
                                drawRect(overlayColor, topLeft = Offset.Zero, size = Size(topLeft.x.coerceAtLeast(0f), this.size.height))
                                drawRect(
                                    overlayColor,
                                    topLeft = Offset(right.coerceAtMost(this.size.width), 0f),
                                    size = Size((this.size.width - right).coerceAtLeast(0f), this.size.height)
                                )
                                drawRect(
                                    overlayColor,
                                    topLeft = Offset(0f, bottom.coerceAtMost(this.size.height)),
                                    size = Size(this.size.width, (this.size.height - bottom).coerceAtLeast(0f))
                                )
                                drawRect(
                                    color = selectionColor,
                                    topLeft = topLeft,
                                    size = size,
                                    style = Stroke(width = 4f)
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                bitmap?.let {
                                    Log.d("OverlayService", "confirm click")
                                    onConfirm(cropBitmap(it, cropStart, cropEnd, canvasSize))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = bitmap != null
                        ) {
                            Text("确认处理")
                        }
                    }
                }
            }
        }

        @Suppress("UNUSED_EXPRESSION")
        closeRequested
    }
}

@Composable
private fun ResultOverlaySheet(
    uiState: OverlayUiState,
    onClose: () -> Unit,
    panelHeightPx: Int
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val panelMaxHeight = with(density) { panelHeightPx.toDp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor.Transparent)
    ) {
        var closeRequested by remember { mutableStateOf(false) }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(panelMaxHeight)
                .align(Alignment.TopCenter),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(start = 20.dp, top = 52.dp, end = 20.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SheetTitleRow(
                        title = "处理中",
                        style = MaterialTheme.typography.titleLarge,
                        onClose = {
                            if (closeRequested) return@SheetTitleRow
                            closeRequested = true
                            onClose()
                        }
                    )
                    ResultCard(title = "原图") {
                        uiState.selectedBitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp)),
                                contentScale = ContentScale.FillWidth
                            )
                        }
                    }
                    if (uiState.settings.processingRoute == ProcessingRoute.OCR_THEN_LLM) {
                        ResultCard(title = "OCR 结果") {
                            if (uiState.liveOcrText.isBlank() && uiState.working) {
                                LoadingLine("正在识别文字…")
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
                            uiState.working -> LoadingLine("正在生成答案…")
                            else -> Text("暂无内容")
                        }
                    }
                    uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    OutlinedButton(
                        onClick = {
                            if (closeRequested) return@OutlinedButton
                            closeRequested = true
                            onClose()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (uiState.working) "后台处理中，先关闭" else "关闭")
                    }
                }
            }
        }

        @Suppress("UNUSED_EXPRESSION")
        closeRequested
    }
}

@Composable
private fun SheetTitleRow(
    title: String,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = style, modifier = Modifier.weight(1f))
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .padding(start = 12.dp)
                .size(36.dp)
        ) {
            Text("×", style = MaterialTheme.typography.titleLarge)
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

@Composable
private fun LoadingLine(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(strokeWidth = 2.dp)
        Text(text)
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
    val cropWidth = ((right - left) * scaleX).toInt().coerceAtLeast(1).coerceAtMost(source.width - cropLeft)
    val cropHeight = ((bottom - top) * scaleY).toInt().coerceAtLeast(1).coerceAtMost(source.height - cropTop)
    return Bitmap.createBitmap(source, cropLeft, cropTop, cropWidth, cropHeight)
}
