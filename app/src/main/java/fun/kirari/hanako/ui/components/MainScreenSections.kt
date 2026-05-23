package `fun`.kirari.hanako.ui.components

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("悬浮球截图助手", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "在 App 内配置 OpenAI 兼容提供方与助手提示词，然后通过悬浮球抓屏、裁剪并发送给 OCR 或多模态模型。",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onOpenOverlayPermission, modifier = Modifier.weight(1f)) {
                    Text("开启悬浮窗权限")
                }
                Button(
                    onClick = {
                        val canOverlay = Settings.canDrawOverlays(context)
                        onToggleOverlay(!overlayEnabled && canOverlay)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = Settings.canDrawOverlays(context)
                ) {
                    Text(if (overlayEnabled) "停止悬浮球" else "启动悬浮球")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("悬浮球状态")
                Switch(
                    checked = overlayEnabled,
                    onCheckedChange = {
                        if (!Settings.canDrawOverlays(context)) {
                            onOpenOverlayPermission()
                        } else {
                            onToggleOverlay(it)
                        }
                    }
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Text(
                    "截图时系统会请求屏幕捕捉授权。",
                    style = MaterialTheme.typography.bodySmall
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
    SectionCard(title = "处理路径") {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ProcessingRoute.entries.forEachIndexed { index, item ->
                SegmentedButton(
                    selected = route == item,
                    onClick = { onSelect(item) },
                    shape = SegmentedButtonDefaults.itemShape(index, ProcessingRoute.entries.size)
                ) {
                    Text(if (item == ProcessingRoute.OCR_THEN_LLM) "OCR + LLM" else "多模态直发")
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            assistants.forEach {
                FilterChip(
                    selected = it.id == selectedId,
                    onClick = { onSelect(it.id) },
                    label = { Text(it.name) }
                )
            }
            TextButton(onClick = onAdd) {
                Text("新增")
            }
        }
        assistants.firstOrNull { it.id == selectedId }?.let { selected ->
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
                label = "系统提示词",
                minLines = 5
            )
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
        label = { Text(label) }
    )
}

@Composable
fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}
