package `fun`.kirari.hanako.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "hanako_settings")

class SettingsStore(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        val raw = preferences[SETTINGS_KEY]
        if (raw.isNullOrBlank()) {
            AppSettings()
        } else {
            runCatching { json.decodeFromString<AppSettings>(raw) }.getOrElse { AppSettings() }
        }
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { preferences ->
            val currentRaw = preferences[SETTINGS_KEY]
            val current = if (currentRaw.isNullOrBlank()) {
                AppSettings()
            } else {
                runCatching { json.decodeFromString<AppSettings>(currentRaw) }.getOrElse { AppSettings() }
            }
            preferences[SETTINGS_KEY] = json.encodeToString(AppSettings.serializer(), transform(current))
        }
    }

    companion object {
        private val SETTINGS_KEY = stringPreferencesKey("app_settings")
    }
}
