package `fun`.kirari.hanako.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
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

enum class Screen(val title: String, val icon: ImageVector) {
    Hanako("Hanako", Icons.Default.Home),
    Settings("设置", Icons.Default.Settings)
}

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
    
    var currentScreen by remember { mutableStateOf(Screen.Hanako) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(currentScreen.title) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
        },
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen },
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = {
                            AnimatedContent(
                                targetState = currentScreen == screen,
                                label = "label"
                            ) { selected ->
                                if (selected) {
                                    Text(screen.title)
                                }
                            }
                        }
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)).togetherWith(fadeOut(animationSpec = tween(300)))
            },
            label = "ScreenTransition",
            modifier = Modifier.padding(padding)
        ) { screen ->
            when (screen) {
                Screen.Hanako -> {
                    HanakoScreen(
                        settings = settings,
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
                        },
                        onSelectRoute = viewModel::setRoute,
                        onClearHistory = viewModel::clearHistory
                    )
                }
                Screen.Settings -> {
                    SettingsScreen(
                        settings = settings,
                        selectedProvider = selectedProvider,
                        selectedAssistant = selectedAssistant,
                        onSelectProvider = viewModel::selectProvider,
                        onUpdateProvider = viewModel::updateProvider,
                        onAddProvider = viewModel::addProvider,
                        onPickModel = { modelPickerTarget = it },
                        onSelectAssistant = viewModel::selectAssistant,
                        onUpdateAssistant = viewModel::updateAssistant,
                        onAddAssistant = viewModel::addAssistant
                    )
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

@Composable
fun HanakoScreen(
    settings: `fun`.kirari.hanako.data.AppSettings,
    onOpenOverlayPermission: () -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
    onSelectRoute: (`fun`.kirari.hanako.data.ProcessingRoute) -> Unit,
    onClearHistory: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
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
                onOpenOverlayPermission = onOpenOverlayPermission,
                onToggleOverlay = onToggleOverlay
            )
        }

        item {
            RouteSection(
                route = settings.processingRoute,
                onSelect = onSelectRoute
            )
        }

        if (settings.history.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "最近历史",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = onClearHistory) {
                        Text("清空")
                    }
                }
            }

            items(settings.history.size) { index ->
                val result = settings.history[index]
                SectionCard(title = "助手：${result.assistantName}") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (result.extractedText.isNotBlank()) {
                            Text("OCR：${result.extractedText}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        MarkdownLatexText(
                            content = result.answer,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
fun SettingsScreen(
    settings: `fun`.kirari.hanako.data.AppSettings,
    selectedProvider: `fun`.kirari.hanako.data.ModelProviderConfig?,
    selectedAssistant: `fun`.kirari.hanako.data.AssistantPreset?,
    onSelectProvider: (String) -> Unit,
    onUpdateProvider: (`fun`.kirari.hanako.data.ModelProviderConfig) -> Unit,
    onAddProvider: () -> Unit,
    onPickModel: (ProviderModelTarget) -> Unit,
    onSelectAssistant: (String) -> Unit,
    onUpdateAssistant: (`fun`.kirari.hanako.data.AssistantPreset) -> Unit,
    onAddAssistant: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard(title = "模型提供方") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    selectedProvider?.let {
                        ProviderEditor(
                            provider = it,
                            providers = settings.providers,
                            onSelect = onSelectProvider,
                            onChange = onUpdateProvider,
                            onAdd = onAddProvider,
                            onPickModel = onPickModel
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "助手配置") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AssistantSelector(
                        assistants = settings.assistants,
                        selectedId = selectedAssistant?.id,
                        onSelect = onSelectAssistant,
                        onChange = onUpdateAssistant,
                        onAdd = onAddAssistant
                    )
                }
            }
        }
        
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}
