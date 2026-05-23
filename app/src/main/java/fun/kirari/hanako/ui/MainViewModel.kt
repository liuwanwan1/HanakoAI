package `fun`.kirari.hanako.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `fun`.kirari.hanako.data.AppSettings
import `fun`.kirari.hanako.data.AssistantPreset
import `fun`.kirari.hanako.data.ModelProviderConfig
import `fun`.kirari.hanako.data.ProcessingResult
import `fun`.kirari.hanako.data.ProcessingRoute
import `fun`.kirari.hanako.data.SettingsStore
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

    fun setRoute(route: ProcessingRoute) {
        viewModelScope.launch {
            store.update { it.copy(processingRoute = route) }
        }
    }

    fun setOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            store.update { it.copy(overlayEnabled = enabled) }
        }
    }

    fun saveResult(result: ProcessingResult) {
        viewModelScope.launch {
            store.update { it.copy(lastResult = result) }
        }
    }
}
