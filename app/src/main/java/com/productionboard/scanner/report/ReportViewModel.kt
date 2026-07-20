package com.productionboard.scanner.report

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.productionboard.scanner.storage.ReportHistoryRepository
import kotlinx.coroutines.launch

class ReportViewModel(application: Application) : AndroidViewModel(application) {
    private val history = ReportHistoryRepository(application)

    fun recordGenerated(subject: String, body: String, rowCount: Int) {
        viewModelScope.launch { history.record(subject, body, rowCount) }
    }

    suspend fun latestReport() = history.latest()
}
