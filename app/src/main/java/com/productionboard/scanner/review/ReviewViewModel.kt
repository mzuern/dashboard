package com.productionboard.scanner.review

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.productionboard.scanner.domain.FieldKey
import com.productionboard.scanner.domain.FieldResult
import com.productionboard.scanner.domain.ReviewRow
import com.productionboard.scanner.ocr.OCRValidator
import com.productionboard.scanner.storage.DraftRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the editable review list: loads/persists the draft (so an
 * interrupted review survives an app kill), and applies edits without
 * ever silently discarding a low-confidence value - editing a field just
 * re-runs it through [OCRValidator] as a manual, full-confidence entry.
 */
class ReviewViewModel(application: Application) : AndroidViewModel(application) {
    private val draftRepository = DraftRepository(application)

    private val _rows = MutableStateFlow<List<ReviewRow>>(emptyList())
    val rows: StateFlow<List<ReviewRow>> = _rows.asStateFlow()

    fun loadDraftIfPresent(onLoaded: (Boolean) -> Unit) {
        viewModelScope.launch {
            val draft = draftRepository.load()
            if (!draft.isNullOrEmpty()) {
                _rows.value = draft
                onLoaded(true)
            } else {
                onLoaded(false)
            }
        }
    }

    fun setRows(rows: List<ReviewRow>) {
        _rows.value = rows
        persist()
    }

    fun updateField(rowId: String, field: FieldKey, text: String, confidenceThreshold: Float) {
        _rows.update { rows ->
            rows.map { row ->
                if (row.id != rowId) return@map row
                val updatedField = when (field) {
                    FieldKey.PROJECT_NUMBER -> OCRValidator.projectNumber(text, 100f, "manual")
                    FieldKey.CUSTOMER -> OCRValidator.customer(text, 100f, "manual")
                    FieldKey.DAYS_REMAINING -> OCRValidator.daysRemaining(text, 100f, "manual")
                }
                val next = when (field) {
                    FieldKey.PROJECT_NUMBER -> row.copy(projectNumber = updatedField)
                    FieldKey.CUSTOMER -> row.copy(customer = updatedField)
                    FieldKey.DAYS_REMAINING -> row.copy(daysRemaining = updatedField)
                }
                recomputeNeedsReview(next, confidenceThreshold)
            }
        }
        persist()
    }

    fun toggleVerified(rowId: String) {
        _rows.update { rows -> rows.map { if (it.id == rowId) it.copy(verified = !it.verified) else it } }
        persist()
    }

    fun deleteRow(rowId: String) {
        _rows.update { rows -> rows.filterNot { it.id == rowId } }
        persist()
    }

    fun addBlankRow() {
        val blank = FieldResult(rawText = "", value = "", confidence = 100f, formatValid = false)
        _rows.update { rows -> rows + ReviewRow(id = "manual-${System.currentTimeMillis()}", rowIndex = rows.size, projectNumber = blank, customer = blank, daysRemaining = blank) }
        persist()
    }

    fun clearDraft() {
        _rows.value = emptyList()
        viewModelScope.launch { draftRepository.clear() }
    }

    private fun persist() {
        viewModelScope.launch { draftRepository.save(_rows.value) }
    }

    private fun recomputeNeedsReview(row: ReviewRow, threshold: Float): ReviewRow {
        val needsReview = !OCRValidator.isOk(row.projectNumber, threshold) ||
            !OCRValidator.isOk(row.customer, threshold) ||
            !OCRValidator.isOk(row.daysRemaining, threshold)
        return row.copy(needsReview = needsReview)
    }
}
