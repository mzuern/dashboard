package com.productionboard.scanner.processing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.productionboard.scanner.domain.ReviewRow
import com.productionboard.scanner.ocr.TesseractOCRProvider
import com.productionboard.scanner.photo.SelectedPhoto
import com.productionboard.scanner.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProcessingState(
    val currentPhotoIndex: Int = 0,
    val totalPhotos: Int = 0,
    val done: Boolean = false,
    val error: String? = null,
)

/**
 * Runs [PhotoProcessor] over the given photos, one at a time
 * (independently - no stitching), and returns their combined candidate
 * rows. Combining with any *previously* reviewed rows and duplicate
 * detection across the full set is the caller's job
 * ([com.productionboard.scanner.review.ReviewViewModel]) - this class
 * only knows about the batch it was given, which is what makes "Add More
 * Photos" (processing just the new photos) straightforward.
 */
class ProcessingViewModel(application: Application) : AndroidViewModel(application) {
    private val ocr = TesseractOCRProvider(application)
    private val settingsRepository = SettingsRepository(application)

    private val _state = MutableStateFlow(ProcessingState())
    val state: StateFlow<ProcessingState> = _state.asStateFlow()

    private val _result = MutableStateFlow<List<ReviewRow>?>(null)
    val result: StateFlow<List<ReviewRow>?> = _result.asStateFlow()

    fun start(photos: List<IndexedValue<SelectedPhoto>>) {
        if (_state.value.totalPhotos > 0 && !_state.value.done && _state.value.error == null) return // already running
        _state.value = ProcessingState(totalPhotos = photos.size)
        _result.value = null

        viewModelScope.launch {
            try {
                val settings = settingsRepository.current()
                ocr.initialize()
                val processor = PhotoProcessor(ocr)

                val allRows = mutableListOf<ReviewRow>()
                for ((progress, indexed) in photos.withIndex()) {
                    _state.update { it.copy(currentPhotoIndex = progress + 1) }
                    allRows += processor.process(indexed.value, indexed.index, settings.boardTemplate, settings.ocrConfidenceThreshold)
                }

                _result.value = allRows
                _state.update { it.copy(done = true) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Processing failed.") }
            } finally {
                ocr.close()
            }
        }
    }
}
