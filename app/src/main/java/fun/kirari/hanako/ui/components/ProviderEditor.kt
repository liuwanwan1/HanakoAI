package `fun`.kirari.hanako.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.data.ModelProviderConfig
import `fun`.kirari.hanako.data.ProviderKind
import `fun`.kirari.hanako.data.displayName
import `fun`.kirari.hanako.data.requestPreviewUrl

enum class ProviderModelTarget {
    CHAT,
    VISION,
    OCR
}

@Composable
fun ProviderEditor(
    provider: ModelProviderConfig,
    providers: List<ModelProviderConfig>,
    onSelect: (String) -> Unit,
    onChange: (ModelProviderConfig) -> Unit,
    onAdd: () -> Unit,
    onPickModel: (ProviderModelTarget) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            providers.forEach {
                FilterChip(
                    selected = it.id == provider.id,
                    onClick = { onSelect(it.id) },
                    label = { Text(it.name) }
                )
            }
            TextButton(onClick = onAdd) {
                Text("新增")
            }
        }

        ProviderTypeSelector(
            kind = provider.kind,
            onChange = { nextKind ->
                onChange(provider.copy(kind = nextKind))
            }
        )

        EditableField(
            value = provider.name,
            onCommit = { onChange(provider.copy(name = it)) },
            label = "提供方名称"
        )

        EditableField(
            value = provider.baseUrl,
            onCommit = { onChange(provider.copy(baseUrl = it)) },
            label = "Base URL"
        )

        Text(
            text = provider.requestPreviewUrl(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        EditableField(
            value = provider.apiKey,
            onCommit = { onChange(provider.copy(apiKey = it)) },
            label = "API Key",
            password = true
        )

        ModelButtonField(
            label = when (provider.kind) {
                ProviderKind.OPENAI_RESPONSES -> "OpenAI Responses"
                else -> "文本模型"
            },
            value = provider.chatModel,
            onPick = { onPickModel(ProviderModelTarget.CHAT) }
        )

        ModelButtonField(
            label = "多模态模型",
            value = provider.visionModel,
            onPick = { onPickModel(ProviderModelTarget.VISION) }
        )

        ModelButtonField(
            label = "OCR 模型",
            value = provider.ocrModel,
            onPick = { onPickModel(ProviderModelTarget.OCR) }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ProviderTypeSelector(
    kind: ProviderKind,
    onChange: (ProviderKind) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = kind.displayName,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
            label = { Text("提供方类型") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
        ProviderKind.entries.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.displayName) },
                    onClick = {
                        expanded = false
                        onChange(item)
                    }
                )
            }
        }
    }
}

@Composable
private fun EditableField(
    value: String,
    onCommit: (String) -> Unit,
    label: String,
    password: Boolean = false
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onCommit,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (!password) {
            null
        } else {
            {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (visible) "隐藏密码" else "显示密码"
                    )
                }
            }
        }
    )
}

@Composable
private fun ModelButtonField(
    label: String,
    value: String,
    onPick: () -> Unit,
) {
    Button(
        onClick = onPick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("$label: ${value.ifBlank { "点击选择" }}")
    }
}
