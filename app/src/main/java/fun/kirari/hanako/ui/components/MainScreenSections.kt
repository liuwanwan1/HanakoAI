package `fun`.kirari.hanako.ui.components

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.data.AssistantPreset
import `fun`.kirari.hanako.data.ProcessingRoute
import kotlinx.coroutines.delay

@Composable
fun HeroSection(
    overlayEnabled: Boolean,
    onOpenOverlayPermission: () -> Unit,
    onToggleOverlay: (Boolean) -> Unit
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (overlayEnabled) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Column {
                    Text(
                        "Hanako 截图助手",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        if (overlayEnabled) "悬浮球正在运行" else "悬浮球已停止",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            Text(
                "通过悬浮球快速捕捉屏幕内容，支持 OCR 识别与多模态模型对话，助你高效处理屏幕信息。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onOpenOverlayPermission,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("权限设置")
                }
                Button(
                    onClick = {
                        val canOverlay = Settings.canDrawOverlays(context)
                        onToggleOverlay(!overlayEnabled && canOverlay)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = Settings.canDrawOverlays(context),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(if (overlayEnabled) "停止助手" else "启动助手")
                }
            }

            if (!Settings.canDrawOverlays(context)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "需要开启悬浮窗权限以使用功能",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !overlayEnabled) {
                Text(
                    "注意：启动助手后，截图时系统会请求屏幕捕捉授权。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun RouteSection(
    route: ProcessingRoute,
    onSelect: (ProcessingRoute) -> Unit
) {
    SectionCard(title = "工作模式") {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ProcessingRoute.entries.forEachIndexed { index, item ->
                SegmentedButton(
                    selected = route == item,
                    onClick = { onSelect(item) },
                    shape = SegmentedButtonDefaults.itemShape(index, ProcessingRoute.entries.size),
                    icon = {}
                ) {
                    Text(
                        if (item == ProcessingRoute.OCR_THEN_LLM) "OCR 增强" else "视觉直达",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun AssistantSelector(
    assistants: List<AssistantPreset>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onChange: (AssistantPreset) -> Unit,
    onAdd: () -> Unit
) {
    Column(
        modifier = Modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                assistants.forEach {
                    FilterChip(
                        selected = it.id == selectedId,
                        onClick = { onSelect(it.id) },
                        label = { Text(it.name) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
            TextButton(onClick = onAdd) {
                Text("新增")
            }
        }
        assistants.firstOrNull { it.id == selectedId }?.let { selected ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DraftOutlinedTextField(
                    fieldKey = "${selected.id}:name",
                    value = selected.name,
                    onCommit = { onChange(selected.copy(name = it)) },
                    label = "助手名称"
                )
                DraftOutlinedTextField(
                    fieldKey = "${selected.id}:systemPrompt",
                    value = selected.systemPrompt,
                    onCommit = { onChange(selected.copy(systemPrompt = it)) },
                    label = "角色设定与提示词",
                    minLines = 5
                )
            }
        }
    }
}

@Composable
fun DraftOutlinedTextField(
    fieldKey: String,
    value: String,
    onCommit: (String) -> Unit,
    label: String,
    minLines: Int = 1
) {
    var textFieldValue by rememberSaveable(fieldKey, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(value, TextRange(value.length)))
    }
    var isFocused by remember(fieldKey) { mutableStateOf(false) }

    LaunchedEffect(fieldKey, value, isFocused) {
        if (!isFocused && value != textFieldValue.text) {
            textFieldValue = TextFieldValue(value, TextRange(value.length))
        }
    }

    LaunchedEffect(fieldKey, textFieldValue.text) {
        delay(250)
        if (textFieldValue.text != value) {
            onCommit(textFieldValue.text)
        }
    }

    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { textFieldValue = it },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                if (!focusState.isFocused && textFieldValue.text != value) {
                    onCommit(textFieldValue.text)
                }
            },
        minLines = minLines,
        label = { Text(label) },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                content()
            }
        }
    }
}
