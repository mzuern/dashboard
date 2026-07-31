package com.productionboard.scanner.processing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.productionboard.scanner.domain.ReviewRow
import com.productionboard.scanner.ocr.TesseractOCRProvider
import com.productionboard.scanner.photo.SelectedPhoto
import com.productionboard.scanner.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProcessingState(
    val currentPhotoIndex: Int = 0,
    val totalPhotos: Int = 0,
    val stitching: Boolean = false,
    val done: Boolean = false,
    val error: String? = null,
)

/** Guided scan frames become one board mosaic; ordinary photos remain an independent-photo fallback. */
class ProcessingViewModel(application: Application) : AndroidViewModel(application) {
    private val ocr = TesseractOCRProvider(application)
    private val settingsRepository = SettingsRepository(application)

    private val _state = MutableStateFlow(ProcessingState())
    val state: StateFlow<ProcessingState> = _state.asStateFlow()

    private val _result = MutableStateFlow<List<ReviewRow>?>(null)
    val result: StateFlow<List<ReviewRow>?> = _result.asStateFlow()

    fun start(photos: List<IndexedValue<SelectedPhoto>>) {
        if (_state.value.totalPhotos > 0 && !_state.value.done && _state.value.error == null) return
        _state.value = ProcessingState(totalPhotos = photos.size)
        _result.value = null

        viewModelScope.launch {
            try {
                require(photos.isNotEmpty()) { "Take at least one board scan frame first." }
                val settings = settingsRepository.current()
                ocr.initialize()
                val processor = PhotoProcessor(ocr)
                val allRows = mutableListOf<ReviewRow>()

                val isGuidedScan = photos.size > 1 && photos.all { it.value.scanX != null && it.value.scanY != null }
                if (isGuidedScan) {
                    _state.update { it.copy(stitching = true, currentPhotoIndex = photos.size) }
                    val stitched = withContext(Dispatchers.Default) {
                        BoardStitcher(getApplication<Application>()).stitch(photos.map { it.value })
                    }
                    _state.update { it.copy(stitching = false) }
                    allRows += processor.process(
                        stitched.photo,
                        photos.first().index,
                        settings.boardTemplate,
                        settings.ocrConfidenceThreshold,
                    )
                } else {
                    for ((progress, indexed) in photos.withIndex()) {
                        _state.update { it.copy(currentPhotoIndex = progress + 1) }
                        allRows += processor.process(
                            indexed.value,
                            indexed.index,
                            settings.boardTemplate,
                            settings.ocrConfidenceThreshold,
                        )
                    }
                }

                _result.value = allRows
                _state.update { it.copy(done = true, stitching = false) }
            } catch (e: Exception) {
                _state.update { it.copy(stitching = false, error = e.message ?: "Processing failed.") }
            } finally {
                ocr.close()
            }
        }
    }
}
