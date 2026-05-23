package `fun`.kirari.hanako.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `fun`.kirari.hanako.data.AppSettings
import `fun`.kirari.hanako.data.AssistantPreset
import `fun`.kirari.hanako.data.ModelPurpose
import `fun`.kirari.hanako.data.ModelProviderConfig
import `fun`.kirari.hanako.data.ModelSelection
import `fun`.kirari.hanako.data.ProcessingResult
import `fun`.kirari.hanako.data.ProcessingRoute
import `fun`.kirari.hanako.data.SettingsStore
import `fun`.kirari.hanako.data.defaultAssistant
import `fun`.kirari.hanako.data.defaultProvider
import `fun`.kirari.hanako.data.modelSelectionFor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SettingsStore(application)

    val settings: StateFlow<AppSettings> = store.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings()
    )

    fun updateProvider(provider: ModelProviderConfig) {
        viewModelScope.launch {
            store.update { current ->
                current.copy(
                    providers = current.providers.map { if (it.id == provider.id) provider else it }
                )
            }
        }
    }

    fun addProvider() {
        viewModelScope.launch {
            store.update { current ->
                val provider = ModelProviderConfig(name = "自定义提供方 ${current.providers.size + 1}")
                current.copy(
                    providers = current.providers + provider,
                    selectedProviderId = provider.id
                )
            }
        }
    }

    fun selectProvider(providerId: String) {
        viewModelScope.launch {
            store.update { it.copy(selectedProviderId = providerId) }
        }
    }

    fun deleteProvider(providerId: String) {
        viewModelScope.launch {
            store.update { current ->
                val remaining = current.providers.filterNot { it.id == providerId }
                val providers = if (remaining.isEmpty()) listOf(defaultProvider()) else remaining
                val selectedProviderId = providers.firstOrNull()?.id
                val fallbackProvider = providers.firstOrNull()
                current.copy(
                    providers = providers,
                    selectedProviderId = selectedProviderId,
                    textModelSelection = remapSelection(
                        current.modelSelectionFor(ModelPurpose.TEXT),
                        providers,
                        fallbackProvider
                    ),
                    visionModelSelection = remapSelection(
                        current.modelSelectionFor(ModelPurpose.VISION),
                        providers,
                        fallbackProvider
                    ),
                    ocrModelSelection = remapSelection(
                        current.modelSelectionFor(ModelPurpose.OCR),
                        providers,
                        fallbackProvider
                    )
                )
            }
        }
    }

    fun updateAssistant(assistant: AssistantPreset) {
        viewModelScope.launch {
            store.update { current ->
                current.copy(
                    assistants = current.assistants.map { if (it.id == assistant.id) assistant else it }
                )
            }
        }
    }

    fun addAssistant() {
        viewModelScope.launch {
            store.update { current ->
                val assistant = AssistantPreset(
                    id = UUID.randomUUID().toString(),
                    name = "自定义助手 ${current.assistants.size + 1}",
                    systemPrompt = "你是一个乐于助人的中文助手。"
                )
                current.copy(
                    assistants = current.assistants + assistant,
                    selectedAssistantId = assistant.id
                )
            }
        }
    }

    fun selectAssistant(assistantId: String) {
        viewModelScope.launch {
            store.update { it.copy(selectedAssistantId = assistantId) }
        }
    }

    fun deleteAssistant(assistantId: String) {
        viewModelScope.launch {
            store.update { current ->
                val remaining = current.assistants.filterNot { it.id == assistantId }
                val assistants = if (remaining.isEmpty()) listOf(defaultAssistant()) else remaining
                val selectedAssistantId = assistants.firstOrNull()?.id
                current.copy(
                    assistants = assistants,
                    selectedAssistantId = selectedAssistantId
                )
            }
        }
    }

    fun setRoute(route: ProcessingRoute) {
        viewModelScope.launch {
            store.update { it.copy(processingRoute = route) }
        }
    }

    fun updateModelSelection(purpose: ModelPurpose, selection: ModelSelection) {
        viewModelScope.launch {
            store.update { current ->
                when (purpose) {
                    ModelPurpose.TEXT -> current.copy(textModelSelection = selection)
                    ModelPurpose.VISION -> current.copy(visionModelSelection = selection)
                    ModelPurpose.OCR -> current.copy(ocrModelSelection = selection)
                }
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            store.update { it.copy(history = emptyList(), lastResult = null) }
        }
    }

    fun saveResult(result: ProcessingResult) {
        viewModelScope.launch {
            store.update { it.copy(
                lastResult = result,
                history = (listOf(result) + it.history).take(20)
            ) }
        }
    }

    private fun remapSelection(
        selection: ModelSelection,
        providers: List<ModelProviderConfig>,
        fallbackProvider: ModelProviderConfig?
    ): ModelSelection {
        val providerExists = providers.any { it.id == selection.providerId }
        return when {
            providerExists -> selection
            fallbackProvider != null -> selection.copy(providerId = fallbackProvider.id)
            else -> selection.copy(providerId = null, model = "")
        }
    }
}
