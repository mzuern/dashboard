package com.productionboard.scanner.board

import com.productionboard.scanner.domain.BoardTemplate
import com.productionboard.scanner.settings.SaveResult
import com.productionboard.scanner.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Template-focused façade over [SettingsRepository] (which persists the
 * whole [com.productionboard.scanner.domain.AppSettings] blob, including
 * this template) - used by CalibrationScreen so it only has to think
 * about the template, not every other setting.
 */
class BoardTemplateRepository(private val settings: SettingsRepository) {

    val templateFlow: Flow<BoardTemplate> = settings.settingsFlow.map { it.boardTemplate }

    suspend fun current(): BoardTemplate = settings.current().boardTemplate

    suspend fun save(template: BoardTemplate): SaveResult {
        val current = settings.current()
        return settings.save(current.copy(boardTemplate = template))
    }
}
