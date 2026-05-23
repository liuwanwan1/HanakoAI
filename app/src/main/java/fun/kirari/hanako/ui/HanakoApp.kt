package `fun`.kirari.hanako.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.capture.MediaProjectionForegroundService
import `fun`.kirari.hanako.capture.ProjectionPermissionActivity
import `fun`.kirari.hanako.overlay.MarkdownLatexText
import `fun`.kirari.hanako.overlay.OverlayService
import `fun`.kirari.hanako.ui.components.AssistantSelector
import `fun`.kirari.hanako.ui.components.CustomModelDialog
import `fun`.kirari.hanako.ui.components.HeroSection
import `fun`.kirari.hanako.ui.components.ModelPickerDialog
import `fun`.kirari.hanako.ui.components.ProviderEditor
import `fun`.kirari.hanako.ui.components.ProviderModelTarget
import `fun`.kirari.hanako.ui.components.RouteSection
import `fun`.kirari.hanako.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HanakoApp(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        viewModel.setOverlayEnabled(result.resultCode == Activity.RESULT_OK)
    }
    val selectedProvider = settings.providers.firstOrNull { it.id == settings.selectedProviderId }
    val selectedAssistant = settings.assistants.firstOrNull { it.id == settings.selectedAssistantId }
    var modelPickerTarget by remember { mutableStateOf<ProviderModelTarget?>(null) }
    var customModelTarget by remember { mutableStateOf<ProviderModelTarget?>(null) }
    var customModelDialogTitle by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Hanako") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerLow,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                ),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HeroSection(
                    overlayEnabled = settings.overlayEnabled,
                    onOpenOverlayPermission = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    },
                    onToggleOverlay = { enabled ->
                        if (enabled) {
                            permissionLauncher.launch(Intent(context, ProjectionPermissionActivity::class.java))
                        } else {
                            viewModel.setOverlayEnabled(false)
                            context.stopService(Intent(context, OverlayService::class.java))
                            context.stopService(
                                Intent(context, MediaProjectionForegroundService::class.java).apply {
                                    action = MediaProjectionForegroundService.ACTION_STOP
                                }
                            )
                        }
                    }
                )
            }

            item {
                RouteSection(
                    route = settings.processingRoute,
                    onSelect = viewModel::setRoute
                )
            }

            item {
                SectionCard(title = "模型提供方") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        selectedProvider?.let {
                            ProviderEditor(
                                provider = it,
                                providers = settings.providers,
                                onSelect = viewModel::selectProvider,
                                onChange = viewModel::updateProvider,
                                onAdd = viewModel::addProvider,
                                onPickModel = { target ->
                                    modelPickerTarget = target
                                }
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = "助手") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AssistantSelector(
                            assistants = settings.assistants,
                            selectedId = selectedAssistant?.id,
                            onSelect = viewModel::selectAssistant,
                            onChange = viewModel::updateAssistant,
                            onAdd = viewModel::addAssistant
                        )
                    }
                }
            }

            settings.lastResult?.let { result ->
                item {
                    SectionCard(title = "最近结果") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("助手：${result.assistantName}", fontWeight = FontWeight.SemiBold)
                            if (result.extractedText.isNotBlank()) {
                                Text("OCR：${result.extractedText}", style = MaterialTheme.typography.bodySmall)
                            }
                            MarkdownLatexText(
                                content = result.answer,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }

    val pickerProvider = selectedProvider
    val pickerTarget = modelPickerTarget
    if (pickerProvider != null && pickerTarget != null) {
        val title = when (pickerTarget) {
            ProviderModelTarget.CHAT -> "选择文本模型"
            ProviderModelTarget.VISION -> "选择多模态模型"
            ProviderModelTarget.OCR -> "选择 OCR 模型"
        }
        ModelPickerDialog(
            provider = pickerProvider,
            title = title,
            onDismiss = { modelPickerTarget = null },
            onPick = { model ->
                viewModel.updateProvider(
                    when (pickerTarget) {
                        ProviderModelTarget.CHAT -> pickerProvider.copy(chatModel = model)
                        ProviderModelTarget.VISION -> pickerProvider.copy(visionModel = model)
                        ProviderModelTarget.OCR -> pickerProvider.copy(ocrModel = model)
                    }
                )
                modelPickerTarget = null
            },
            onCustomModelRequest = { dialogTitle ->
                customModelTarget = pickerTarget
                customModelDialogTitle = dialogTitle
            }
        )
    }

    customModelDialogTitle?.let { title ->
        CustomModelDialog(
            title = title,
            onDismiss = { customModelDialogTitle = null },
            onConfirm = { model ->
                val provider = selectedProvider ?: return@CustomModelDialog
                val updated = when (customModelTarget ?: ProviderModelTarget.CHAT) {
                    ProviderModelTarget.CHAT -> provider.copy(chatModel = model)
                    ProviderModelTarget.VISION -> provider.copy(visionModel = model)
                    ProviderModelTarget.OCR -> provider.copy(ocrModel = model)
                }
                viewModel.updateProvider(updated)
                customModelDialogTitle = null
                customModelTarget = null
            }
        )
    }
}
