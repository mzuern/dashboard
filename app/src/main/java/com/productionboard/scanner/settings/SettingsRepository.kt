package com.productionboard.scanner.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.productionboard.scanner.domain.AppSettings
import com.productionboard.scanner.domain.validate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

sealed interface SaveResult {
    data object Ok : SaveResult
    data class Invalid(val errors: List<String>) : SaveResult
}

/**
 * Persists [AppSettings] (which embeds the current [com.productionboard.scanner.domain.BoardTemplate])
 * as a single JSON blob in DataStore. Everything here is local device storage -
 * nothing is ever synced or uploaded.
 */
class SettingsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val settingsKey = stringPreferencesKey("app_settings_json")

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        prefs[settingsKey]?.let { raw ->
            runCatching { json.decodeFromString<AppSettings>(raw) }.getOrNull()
        } ?: AppSettings.DEFAULT
    }

    suspend fun current(): AppSettings = settingsFlow.first()

    suspend fun save(settings: AppSettings): SaveResult {
        val errors = settings.boardTemplate.validate().toMutableList()
        if (settings.ocrConfidenceThreshold < 0f || settings.ocrConfidenceThreshold > 100f) {
            errors += "OCR confidence threshold must be between 0 and 100."
        }
        if (settings.coverageThreshold <= 0f || settings.coverageThreshold > 1f) {
            errors += "Coverage threshold must be between 1% and 100%."
        }
        if (errors.isNotEmpty()) return SaveResult.Invalid(errors)

        context.settingsDataStore.edit { prefs ->
            prefs[settingsKey] = json.encodeToString(settings)
        }
        return SaveResult.Ok
    }

    suspend fun resetToDefaults() {
        context.settingsDataStore.edit { prefs -> prefs[settingsKey] = json.encodeToString(AppSettings.DEFAULT) }
    }
}
