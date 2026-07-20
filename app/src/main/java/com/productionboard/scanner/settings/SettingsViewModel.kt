package com.productionboard.scanner.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.productionboard.scanner.domain.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)

    private val _settings = MutableStateFlow(AppSettings.DEFAULT)
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _saveErrors = MutableStateFlow<List<String>>(emptyList())
    val saveErrors: StateFlow<List<String>> = _saveErrors.asStateFlow()

    init {
        viewModelScope.launch { repository.settingsFlow.collect { _settings.value = it } }
    }

    fun save(settings: AppSettings, onSaved: (AppSettings) -> Unit) {
        viewModelScope.launch {
            when (val result = repository.save(settings)) {
                is SaveResult.Ok -> {
                    _saveErrors.value = emptyList()
                    onSaved(settings)
                }
                is SaveResult.Invalid -> _saveErrors.value = result.errors
            }
        }
    }

    fun resetToDefaults(onDone: (AppSettings) -> Unit) {
        viewModelScope.launch {
            repository.resetToDefaults()
            onDone(repository.current())
        }
    }
}
