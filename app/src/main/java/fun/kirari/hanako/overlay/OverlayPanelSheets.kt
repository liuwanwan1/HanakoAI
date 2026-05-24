package `fun`.kirari.hanako.overlay

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.data.AssistantPreset
import `fun`.kirari.hanako.data.ModelPurpose
import `fun`.kirari.hanako.data.ProcessingRoute
import `fun`.kirari.hanako.data.resolveModelName
import `fun`.kirari.hanako.data.resolveModelProvider
import `fun`.kirari.hanako.ui.cropBitmap
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
internal fun CropOverlaySheet(
    uiState: OverlayUiState,
    onClose: () -> Unit,
    onConfirm: (Bitmap) -> Unit,
    panelHeightPx: Int,
    onSelectAssistant: (String) -> Unit,
    onSelectPreviousAssistant: () -> Unit,
    onSelectNextAssistant: () -> Unit
) {
    val bitmap = uiState.screenshot
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var cropStart by remember { mutableStateOf(Offset.Zero) }
    var cropEnd by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val selectedAssistant = uiState.settings.assistants.firstOrNull { it.id == uiState.settings.selectedAssistantId }
    val routeText = if (uiState.settings.processingRoute == ProcessingRoute.OCR_THEN_LLM) "OCR模式" else "多模态模式"
    val activePurpose = if (uiState.settings.processingRoute == ProcessingRoute.OCR_THEN_LLM) {
        ModelPurpose.TEXT
    } else {
        ModelPurpose.VISION
    }
    val activeProvider = uiState.settings.resolveModelProvider(activePurpose)
    val activeModel = uiState.settings.resolveModelName(activePurpose)
    val modelProviderText = buildString {
        append(activeModel.ifBlank { "未配置模型" })
        activeProvider?.name?.takeIf { it.isNotBlank() }?.let { providerName ->
            append("（")
            append(providerName)
            append("）")
        }
    }
    val selectionColor = MaterialTheme.colorScheme.primary
    val panelMaxHeight = with(density) { panelHeightPx.toDp() }
    var showAssistantDialog by remember { mutableStateOf(false) }
    var assistantPickerClosing by remember { mutableStateOf(false) }
    var switchDirection by remember { mutableStateOf(AssistantSwitchDirection.PICKER) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        var closeRequested by remember { mutableStateOf(false) }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(panelMaxHeight)
                .align(Alignment.BottomCenter),
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
                    CropHeaderRow(
                        assistantName = selectedAssistant?.name.orEmpty(),
                        hasMultipleAssistants = uiState.settings.assistants.size > 1,
                        switchDirection = switchDirection,
                        onSelectPrevious = {
                            switchDirection = AssistantSwitchDirection.PREVIOUS
                            onSelectPreviousAssistant()
                        },
                        onSelectNext = {
                            switchDirection = AssistantSwitchDirection.NEXT
                            onSelectNextAssistant()
                        },
                        onOpenPicker = {
                            assistantPickerClosing = false
                            showAssistantDialog = true
                        },
                        onClose = {
                            if (closeRequested) return@CropHeaderRow
                            closeRequested = true
                            onClose()
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(routeText, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            modelProviderText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Black)
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
                                val overlayColor = Color.Black.copy(alpha = 0.42f)
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

    if (showAssistantDialog) {
        AssistantPickerOverlay(
            assistants = uiState.settings.assistants,
            selectedAssistantId = uiState.settings.selectedAssistantId,
            closing = assistantPickerClosing,
            onDismiss = { assistantPickerClosing = true },
            onDismissFinished = {
                showAssistantDialog = false
                assistantPickerClosing = false
            },
            onSelect = { assistantId ->
                switchDirection = AssistantSwitchDirection.PICKER
                onSelectAssistant(assistantId)
                assistantPickerClosing = true
            }
        )
    }
}

@Composable
internal fun ResultOverlaySheet(
    uiState: OverlayUiState,
    onClose: () -> Unit,
    panelHeightPx: Int
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val panelMaxHeight = with(density) { panelHeightPx.toDp() }
    val clipboardManager = LocalClipboardManager.current
    val answerText = uiState.liveAnswerText
    var rawTextMode by remember(answerText) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        var closeRequested by remember { mutableStateOf(false) }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(panelMaxHeight)
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                SheetTitleRow(
                    title = {
                        Text("Hanako")
                    },
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = 16.dp, end = 12.dp, bottom = 8.dp),
                    onClose = {
                        if (closeRequested) return@SheetTitleRow
                        closeRequested = true
                        onClose()
                    }
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
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
                    ResultCard(
                        title = "答案",
                        actions = {
                            if (!uiState.working && answerText.isNotBlank()) {
                                SmallHeaderAction(
                                    label = "复制",
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(answerText))
                                        Toast.makeText(context, "已复制全文", Toast.LENGTH_SHORT).show()
                                    }
                                )
                                SmallHeaderAction(
                                    label = "选择文本复制",
                                    active = rawTextMode,
                                    onClick = { rawTextMode = !rawTextMode }
                                )
                            }
                        }
                    ) {
                        when {
                            answerText.isNotBlank() && rawTextMode -> {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                                ) {
                                    SelectionContainer {
                                        Text(
                                            text = answerText,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                            answerText.isNotBlank() -> MarkdownLatexText(
                                content = answerText,
                                modifier = Modifier.fillMaxWidth()
                            )
                            uiState.working -> LoadingLine("正在生成答案…")
                            else -> Text("暂无内容")
                        }
                    }
                    uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }

        @Suppress("UNUSED_EXPRESSION")
        closeRequested
    }
}

@Composable
private fun CropHeaderRow(
    assistantName: String,
    hasMultipleAssistants: Boolean,
    switchDirection: AssistantSwitchDirection,
    onSelectPrevious: () -> Unit,
    onSelectNext: () -> Unit,
    onOpenPicker: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            AssistantSwitcher(
                assistantName = assistantName,
                hasMultipleAssistants = hasMultipleAssistants,
                switchDirection = switchDirection,
                onSelectPrevious = onSelectPrevious,
                onSelectNext = onSelectNext,
                onOpenPicker = onOpenPicker
            )
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(36.dp)
        ) {
            Text("×", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun SheetTitleRow(
    title: @Composable () -> Unit,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    modifier: Modifier = Modifier,
    onClose: () -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            CompositionLocalProvider(
                androidx.compose.material3.LocalTextStyle provides style
            ) {
                title()
            }
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(36.dp)
        ) {
            Text("×", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun AssistantSwitcher(
    assistantName: String,
    hasMultipleAssistants: Boolean,
    switchDirection: AssistantSwitchDirection,
    onSelectPrevious: () -> Unit,
    onSelectNext: () -> Unit,
    onOpenPicker: () -> Unit
) {
    Row(
        modifier = Modifier.wrapContentWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (hasMultipleAssistants) {
            SwitchArrowButton(symbol = "〈", onClick = onSelectPrevious)
        }
        Box(
            modifier = Modifier
                .width(168.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(onClick = onOpenPicker)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = assistantName,
                transitionSpec = {
                    assistantNameTransform(switchDirection)
                },
                label = "assistantName"
            ) { currentAssistantName ->
                Text(
                    text = "助手：$currentAssistantName",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
            }
        }
        if (hasMultipleAssistants) {
            SwitchArrowButton(symbol = "〉", onClick = onSelectNext)
        }
    }
}

@Composable
private fun SwitchArrowButton(
    symbol: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(width = 36.dp, height = 36.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(symbol, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun AssistantPickerOverlay(
    assistants: List<AssistantPreset>,
    selectedAssistantId: String?,
    closing: Boolean,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onSelect: (String) -> Unit
) {
    val density = LocalDensity.current.density
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }
    LaunchedEffect(closing) {
        if (closing) {
            visible = false
            delay(220)
            onDismissFinished()
        }
    }
    val overlayAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 180, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "assistantPickerOverlayAlpha"
    )
    val cardAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "assistantPickerCardAlpha"
    )
    val cardTranslationY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 0f else if (closing) -20f else 20f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "assistantPickerCardTranslationY"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f * overlayAlpha))
            .clickable(onClick = {
                if (!closing) onDismiss()
            }),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .width(300.dp)
                .graphicsLayer {
                    alpha = cardAlpha
                    translationY = cardTranslationY * density
                }
                .clickable(enabled = false, onClick = { })
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("选择助手", style = MaterialTheme.typography.titleMedium)
                LazyColumn(
                    modifier = Modifier.height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(assistants, key = { it.id }) { assistant ->
                        Surface(
                            onClick = { onSelect(assistant.id) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (assistant.id == selectedAssistantId) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(assistant.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    assistant.systemPrompt.replace('\n', ' '),
                                    maxLines = 2,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        if (!closing) onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("关闭")
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    title: String,
    actions: @Composable (RowScope.() -> Unit)? = null,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (actions != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun SmallHeaderAction(
    label: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (active) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    )
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
