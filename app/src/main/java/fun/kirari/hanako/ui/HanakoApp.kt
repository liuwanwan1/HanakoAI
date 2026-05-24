package `fun`.kirari.hanako.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import `fun`.kirari.hanako.capture.MediaProjectionForegroundService
import `fun`.kirari.hanako.capture.ProjectionPermissionActivity
import `fun`.kirari.hanako.capture.ProjectionSessionManager
import `fun`.kirari.hanako.data.ModelPurpose
import `fun`.kirari.hanako.data.ModelSelection
import `fun`.kirari.hanako.data.displayName
import `fun`.kirari.hanako.data.modelSelectionFor
import `fun`.kirari.hanako.data.normalize
import `fun`.kirari.hanako.data.resolveModelName
import `fun`.kirari.hanako.data.resolveModelProvider
import `fun`.kirari.hanako.overlay.MarkdownLatexText
import `fun`.kirari.hanako.overlay.OverlayService
import `fun`.kirari.hanako.ui.components.AssistantSelector
import `fun`.kirari.hanako.ui.components.CustomModelDialog
import `fun`.kirari.hanako.ui.components.HeroSection
import `fun`.kirari.hanako.ui.components.ModelPickerDialog
import `fun`.kirari.hanako.ui.components.ModelButtonField
import `fun`.kirari.hanako.ui.components.ProviderEditor
import `fun`.kirari.hanako.ui.components.RouteSection
import `fun`.kirari.hanako.ui.components.SectionCard

enum class Screen(val title: String, val icon: ImageVector) {
    Hanako("Hanako", Icons.Default.Home),
    Settings("设置", Icons.Default.Settings)
}

private const val ROUTE_HANAKO_HOME = "hanako_home"
private const val ROUTE_HANAKO_HISTORY = "hanako_history"
private const val ROUTE_SETTINGS_MENU = "settings_menu"
private const val ROUTE_SETTINGS_PROVIDER = "settings_provider"
private const val ROUTE_SETTINGS_PROVIDER_DETAIL = "settings_provider_detail"
private const val ROUTE_SETTINGS_MODEL = "settings_model"
private const val ROUTE_SETTINGS_ASSISTANT = "settings_assistant"
private const val ROUTE_SETTINGS_ASSISTANT_DETAIL = "settings_assistant_detail"
private const val ARG_PROVIDER_ID = "providerId"
private const val ARG_ASSISTANT_ID = "assistantId"

private fun providerDetailRoute(providerId: String): String = "$ROUTE_SETTINGS_PROVIDER_DETAIL/$providerId"
private fun assistantDetailRoute(assistantId: String): String = "$ROUTE_SETTINGS_ASSISTANT_DETAIL/$assistantId"

private fun settingsTitle(route: String?): String = when (route) {
    ROUTE_SETTINGS_PROVIDER -> "模型提供方"
    ROUTE_SETTINGS_MODEL -> "模型设置"
    ROUTE_SETTINGS_ASSISTANT -> "助手配置"
    null -> "设置"
    else -> when {
        route.startsWith("$ROUTE_SETTINGS_PROVIDER_DETAIL/") -> "编辑提供方"
        route.startsWith("$ROUTE_SETTINGS_ASSISTANT_DETAIL/") -> "编辑助手"
        else -> "设置"
    }
}

private fun hanakoTitle(route: String?): String = when (route) {
    ROUTE_HANAKO_HISTORY -> "历史记录"
    else -> Screen.Hanako.title
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HanakoApp(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val overlayEnabled by ProjectionSessionManager.sessionActive.collectAsState()
    val selectedAssistant = settings.assistants.firstOrNull { it.id == settings.selectedAssistantId }
    var providerModelsPreviewId by remember { mutableStateOf<String?>(null) }
    var modelPickerTarget by remember { mutableStateOf<ModelPurpose?>(null) }
    var customModelTarget by remember { mutableStateOf<ModelPurpose?>(null) }
    var customModelDialogTitle by remember { mutableStateOf<String?>(null) }
    var providerPickerTarget by remember { mutableStateOf<ModelPurpose?>(null) }
    var modelPickerProviderId by remember { mutableStateOf<String?>(null) }

    var currentScreen by rememberSaveable { mutableStateOf(Screen.Hanako) }
    var pendingHanakoReset by rememberSaveable { mutableStateOf(false) }
    var pendingSettingsReset by rememberSaveable { mutableStateOf(false) }

    val hanakoNavController = rememberNavController()
    val hanakoBackStackEntry by hanakoNavController.currentBackStackEntryAsState()
    val hanakoRoute = hanakoBackStackEntry?.destination?.route
    val inHanakoSubPage = hanakoRoute != null && hanakoRoute != ROUTE_HANAKO_HOME
    val settingsNavController = rememberNavController()
    val settingsBackStackEntry by settingsNavController.currentBackStackEntryAsState()
    val settingsRoute = settingsBackStackEntry?.destination?.route
    val inSettingsSubPage = settingsRoute != null && settingsRoute != ROUTE_SETTINGS_MENU
    val pagerState = rememberPagerState(
        initialPage = currentScreen.ordinal,
        pageCount = { Screen.entries.size }
    )

    BackHandler(enabled = currentScreen == Screen.Settings && !inSettingsSubPage) {
        currentScreen = Screen.Hanako
    }

    LaunchedEffect(currentScreen) {
        if (pagerState.targetPage != currentScreen.ordinal && pagerState.currentPage != currentScreen.ordinal) {
            pagerState.animateScrollToPage(currentScreen.ordinal)
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        val settledScreen = Screen.entries[pagerState.settledPage]
        if (currentScreen != settledScreen) {
            currentScreen = settledScreen
        }
    }

    LaunchedEffect(currentScreen, hanakoRoute, pendingHanakoReset) {
        if (currentScreen == Screen.Hanako && pendingHanakoReset && hanakoRoute != null) {
            if (hanakoRoute != ROUTE_HANAKO_HOME) {
                hanakoNavController.popBackStack(ROUTE_HANAKO_HOME, inclusive = false)
            }
            pendingHanakoReset = false
        }
    }

    LaunchedEffect(currentScreen, settingsRoute, pendingSettingsReset) {
        if (currentScreen == Screen.Settings && pendingSettingsReset && settingsRoute != null) {
            if (settingsRoute != ROUTE_SETTINGS_MENU) {
                settingsNavController.popBackStack(ROUTE_SETTINGS_MENU, inclusive = false)
            }
            pendingSettingsReset = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            when (currentScreen) {
                                Screen.Settings -> settingsTitle(settingsRoute)
                                Screen.Hanako -> hanakoTitle(hanakoRoute)
                            },
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    navigationIcon = {
                        when {
                            currentScreen == Screen.Hanako && inHanakoSubPage -> {
                                IconButton(onClick = { hanakoNavController.popBackStack() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                                }
                            }
                            currentScreen == Screen.Settings && inSettingsSubPage -> {
                                IconButton(onClick = { settingsNavController.popBackStack() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            bottomBar = {
                NavigationBar {
                    Screen.entries.forEach { screen ->
                        NavigationBarItem(
                            selected = currentScreen == screen,
                            onClick = {
                                when (screen) {
                                    Screen.Hanako -> {
                                        pendingHanakoReset = true
                                        currentScreen = Screen.Hanako
                                    }
                                    Screen.Settings -> {
                                        pendingSettingsReset = true
                                        currentScreen = Screen.Settings
                                    }
                                }
                            },
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
            containerColor = Color.Transparent
        ) { padding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                userScrollEnabled = !inSettingsSubPage && !inHanakoSubPage
            ) {
                when (Screen.entries[it]) {
                    Screen.Hanako -> {
                        HanakoNavHost(
                            navController = hanakoNavController,
                            settings = settings,
                            overlayEnabled = overlayEnabled,
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
                                    context.startActivity(Intent(context, ProjectionPermissionActivity::class.java))
                                } else {
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
                        SettingsNavHost(
                            navController = settingsNavController,
                            settings = settings,
                            onSelectProvider = viewModel::selectProvider,
                            onUpdateProvider = viewModel::updateProvider,
                            onAddProvider = viewModel::addProvider,
                            onDeleteProvider = viewModel::deleteProvider,
                            onOpenModelSettings = { settingsNavController.navigate(ROUTE_SETTINGS_MODEL) },
                            onPreviewProviderModels = { providerModelsPreviewId = it },
                            onPickModel = { providerPickerTarget = it },
                            onSelectAssistant = viewModel::selectAssistant,
                            onUpdateAssistant = viewModel::updateAssistant,
                            onAddAssistant = viewModel::addAssistant,
                            onDeleteAssistant = viewModel::deleteAssistant,
                            onUpdateModelSelection = viewModel::updateModelSelection
                        )
                    }
                }
            }
        }
    }

    val pickerProvider = settings.providers.firstOrNull { it.id == modelPickerProviderId }
    if (providerPickerTarget != null) {
        ProviderSelectDialog(
            providers = settings.providers,
            title = "选择${providerPickerTarget?.displayName}提供方",
            onDismiss = { providerPickerTarget = null },
            onPick = { provider ->
                modelPickerProviderId = provider.id
                modelPickerTarget = providerPickerTarget
                providerPickerTarget = null
            }
        )
    }

    val pickerTarget = modelPickerTarget
    if (pickerProvider != null && pickerTarget != null) {
        val title = when (pickerTarget) {
            ModelPurpose.TEXT -> "选择文本模型"
            ModelPurpose.VISION -> "选择多模态模型"
            ModelPurpose.OCR -> "选择 OCR 模型"
        }
        ModelPickerDialog(
            provider = pickerProvider,
            title = title,
            onDismiss = {
                modelPickerTarget = null
                modelPickerProviderId = null
            },
            onPick = { model ->
                viewModel.updateModelSelection(
                    pickerTarget,
                    ModelSelection(providerId = pickerProvider.id, model = model)
                )
                modelPickerTarget = null
                modelPickerProviderId = null
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
            onDismiss = {
                customModelDialogTitle = null
                customModelTarget = null
                modelPickerProviderId = null
            },
            onConfirm = { model ->
                val purpose = customModelTarget ?: ModelPurpose.TEXT
                val providerId = modelPickerProviderId ?: return@CustomModelDialog
                viewModel.updateModelSelection(
                    purpose,
                    ModelSelection(providerId = providerId, model = model)
                )
                customModelDialogTitle = null
                customModelTarget = null
                modelPickerTarget = null
                modelPickerProviderId = null
            }
        )
    }

    val previewProvider = settings.providers.firstOrNull { it.id == providerModelsPreviewId }
    if (previewProvider != null) {
        ModelPickerDialog(
            provider = previewProvider,
            title = "查看可用模型",
            onDismiss = { providerModelsPreviewId = null },
            onPick = { providerModelsPreviewId = null },
            onCustomModelRequest = { }
        )
    }
}

@Composable
private fun HanakoNavHost(
    navController: androidx.navigation.NavController,
    settings: `fun`.kirari.hanako.data.AppSettings,
    overlayEnabled: Boolean,
    onOpenOverlayPermission: () -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
    onSelectRoute: (`fun`.kirari.hanako.data.ProcessingRoute) -> Unit,
    onClearHistory: () -> Unit
) {
    NavHost(
        navController = navController as androidx.navigation.NavHostController,
        startDestination = ROUTE_HANAKO_HOME,
        enterTransition = {
            slideInHorizontally { it } + fadeIn()
        },
        exitTransition = {
            slideOutHorizontally { -it / 2 } + fadeOut()
        },
        popEnterTransition = {
            slideInHorizontally { -it / 2 } + fadeIn()
        },
        popExitTransition = {
            slideOutHorizontally { it }
        }
    ) {
        composable(ROUTE_HANAKO_HOME) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    HeroSection(
                        overlayEnabled = overlayEnabled,
                        route = settings.processingRoute,
                        onSelectRoute = onSelectRoute,
                        onOpenOverlayPermission = onOpenOverlayPermission,
                        onToggleOverlay = onToggleOverlay
                    )
                }

                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = { navController.navigate(ROUTE_HANAKO_HISTORY) }),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "历史记录",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "查看悬浮窗处理过的历史记录",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
        composable(ROUTE_HANAKO_HISTORY) {
            HistorySubScreen(
                settings = settings,
                onClearHistory = onClearHistory
            )
        }
    }
}

@Composable
private fun HistorySubScreen(
    settings: `fun`.kirari.hanako.data.AppSettings,
    onClearHistory: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "历史记录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = onClearHistory,
                    enabled = settings.history.isNotEmpty()
                ) {
                    Text("清空")
                }
            }
        }
        if (settings.history.isEmpty()) {
            item {
                SectionCard(title = "暂无历史") {
                    Text(
                        "悬浮窗处理过的记录会显示在这里。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(settings.history.size) { index ->
                val result = settings.history[index]
                SectionCard(title = "助手：${result.assistantName}") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (result.route == `fun`.kirari.hanako.data.ProcessingRoute.OCR_THEN_LLM) "模式：OCR" else "模式：多模态",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (result.extractedText.isNotBlank()) {
                            Text(
                                "OCR：${result.extractedText}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
private fun SettingsNavHost(
    navController: androidx.navigation.NavController,
    settings: `fun`.kirari.hanako.data.AppSettings,
    onSelectProvider: (String) -> Unit,
    onUpdateProvider: (`fun`.kirari.hanako.data.ModelProviderConfig) -> Unit,
    onAddProvider: () -> Unit,
    onDeleteProvider: (String) -> Unit,
    onOpenModelSettings: () -> Unit,
    onPreviewProviderModels: (String) -> Unit,
    onPickModel: (ModelPurpose) -> Unit,
    onSelectAssistant: (String) -> Unit,
    onUpdateAssistant: (`fun`.kirari.hanako.data.AssistantPreset) -> Unit,
    onAddAssistant: () -> Unit,
    onDeleteAssistant: (String) -> Unit,
    onUpdateModelSelection: (ModelPurpose, ModelSelection) -> Unit
) {
    NavHost(
        navController = navController as androidx.navigation.NavHostController,
        startDestination = ROUTE_SETTINGS_MENU,
        enterTransition = {
            slideInHorizontally { it } + fadeIn()
        },
        exitTransition = {
            slideOutHorizontally { -it / 2 } + fadeOut()
        },
        popEnterTransition = {
            slideInHorizontally { -it / 2 } + fadeIn()
        },
        popExitTransition = {
            slideOutHorizontally { it }
        }
    ) {
        composable(ROUTE_SETTINGS_MENU) {
            SettingsMenuScreen(
                onNavigateProvider = { navController.navigate(ROUTE_SETTINGS_PROVIDER) },
                onNavigateModel = { navController.navigate(ROUTE_SETTINGS_MODEL) },
                onNavigateAssistant = { navController.navigate(ROUTE_SETTINGS_ASSISTANT) }
            )
        }
        composable(ROUTE_SETTINGS_PROVIDER) {
            ProviderSettingsScreen(
                settings = settings,
                onAddProvider = onAddProvider,
                onDeleteProvider = onDeleteProvider,
                onOpenProvider = { providerId ->
                    onSelectProvider(providerId)
                    navController.navigate(providerDetailRoute(providerId))
                }
            )
        }
        composable("$ROUTE_SETTINGS_PROVIDER_DETAIL/{$ARG_PROVIDER_ID}") { backStackEntry ->
            val providerId = backStackEntry.arguments?.getString(ARG_PROVIDER_ID)
            val provider = settings.providers.firstOrNull { it.id == providerId }
            if (provider != null) {
                ProviderDetailScreen(
                    provider = provider,
                    onUpdateProvider = onUpdateProvider,
                    onViewModels = { onPreviewProviderModels(provider.id) }
                )
            }
        }
        composable(ROUTE_SETTINGS_MODEL) {
            ModelSettingsScreen(
                settings = settings,
                onPickModel = onPickModel
            )
        }
        composable(ROUTE_SETTINGS_ASSISTANT) {
            AssistantSettingsScreen(
                settings = settings,
                onAddAssistant = onAddAssistant,
                onDeleteAssistant = onDeleteAssistant,
                onOpenAssistant = { assistantId ->
                    onSelectAssistant(assistantId)
                    navController.navigate(assistantDetailRoute(assistantId))
                }
            )
        }
        composable("$ROUTE_SETTINGS_ASSISTANT_DETAIL/{$ARG_ASSISTANT_ID}") { backStackEntry ->
            val assistantId = backStackEntry.arguments?.getString(ARG_ASSISTANT_ID)
            val assistant = settings.assistants.firstOrNull { it.id == assistantId }
            if (assistant != null) {
                AssistantDetailScreen(
                    assistant = assistant,
                    onUpdateAssistant = onUpdateAssistant
                )
            }
        }
    }
}

@Composable
fun SettingsMenuScreen(
    onNavigateProvider: () -> Unit,
    onNavigateModel: () -> Unit,
    onNavigateAssistant: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateProvider),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.Build,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            "模型提供方",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "配置 API 地址与模型",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateModel),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.Memory,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            "模型设置",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "为 OCR、文本、多模态分别指定提供方和模型",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateAssistant),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            "助手配置",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "管理助手名称与提示词",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProviderSettingsScreen(
    settings: `fun`.kirari.hanako.data.AppSettings,
    onAddProvider: () -> Unit,
    onDeleteProvider: (String) -> Unit,
    onOpenProvider: (String) -> Unit
) {
    var deleteTargetId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "模型提供方",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = onAddProvider) {
                    Text("新增")
                }
            }
        }
        items(settings.providers.size, key = { index -> settings.providers[index].id }) { index ->
            val provider = settings.providers[index]
            Surface(
                modifier = Modifier.combinedClickable(
                    onClick = { onOpenProvider(provider.id) },
                    onLongClick = { deleteTargetId = provider.id }
                ),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            provider.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            provider.kind.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }

    val deleteTarget = settings.providers.firstOrNull { it.id == deleteTargetId }
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTargetId = null },
            title = { Text("删除提供方") },
            text = { Text("确认删除 ${deleteTarget.name}？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteProvider(deleteTarget.id)
                        deleteTargetId = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetId = null }) {
                    Text("取消")
                }
            },
            icon = {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
        )
    }
}

@Composable
fun ProviderDetailScreen(
    provider: `fun`.kirari.hanako.data.ModelProviderConfig,
    onUpdateProvider: (`fun`.kirari.hanako.data.ModelProviderConfig) -> Unit,
    onViewModels: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard(title = "提供方配置") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProviderEditor(
                        provider = provider,
                        onChange = onUpdateProvider
                    )
                    OutlinedButton(
                        onClick = onViewModels,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("查看可用模型")
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
fun ModelSettingsScreen(
    settings: `fun`.kirari.hanako.data.AppSettings,
    onPickModel: (ModelPurpose) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard(title = "模型设置") {
                ModelPurpose.entries.forEach { purpose ->
                    val provider = settings.resolveModelProvider(purpose)
                    val model = settings.resolveModelName(purpose)
                    ModelButtonField(
                        label = "${purpose.displayName} 模型",
                        value = buildString {
                            append(provider?.name ?: "未选择提供方")
                            if (model.isNotBlank()) {
                                append(" / ")
                                append(model)
                            }
                        },
                        onPick = { onPickModel(purpose) }
                    )
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun ProviderSelectDialog(
    providers: List<`fun`.kirari.hanako.data.ModelProviderConfig>,
    title: String,
    onDismiss: () -> Unit,
    onPick: (`fun`.kirari.hanako.data.ModelProviderConfig) -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                LazyColumn(
                    modifier = Modifier.height(240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(providers, key = { it.id }) { provider ->
                        OutlinedButton(
                            onClick = { onPick(provider) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(provider.name)
                                Text(
                                    provider.kind.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("取消")
                }
            }
        }
    }
}

@Composable
fun AssistantSettingsScreen(
    settings: `fun`.kirari.hanako.data.AppSettings,
    onAddAssistant: () -> Unit,
    onDeleteAssistant: (String) -> Unit,
    onOpenAssistant: (String) -> Unit
) {
    var deleteTargetId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "助手配置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = onAddAssistant) {
                    Text("新增")
                }
            }
        }
        items(settings.assistants.size, key = { index -> settings.assistants[index].id }) { index ->
            val assistant = settings.assistants[index]
            Surface(
                modifier = Modifier.combinedClickable(
                    onClick = { onOpenAssistant(assistant.id) },
                    onLongClick = { deleteTargetId = assistant.id }
                ),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            assistant.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            assistant.systemPrompt.replace('\n', ' '),
                            maxLines = 2,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }

    val deleteTarget = settings.assistants.firstOrNull { it.id == deleteTargetId }
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTargetId = null },
            title = { Text("删除助手") },
            text = { Text("确认删除 ${deleteTarget.name}？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAssistant(deleteTarget.id)
                        deleteTargetId = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetId = null }) {
                    Text("取消")
                }
            },
            icon = {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
        )
    }
}


@Composable
fun AssistantDetailScreen(
    assistant: `fun`.kirari.hanako.data.AssistantPreset,
    onUpdateAssistant: (`fun`.kirari.hanako.data.AssistantPreset) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard(title = "助手配置") {
                AssistantSelector(
                    assistant = assistant,
                    onChange = onUpdateAssistant
                )
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}
