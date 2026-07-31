package com.productionboard.scanner.scanning

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.productionboard.scanner.photo.PhotoStorage
import com.productionboard.scanner.photo.SelectedPhoto
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


data class GuidedScanState(
    val frameCount: Int = 0,
    val instruction: String = "Aim at the upper-left area of the board, then move slowly across it.",
    val isSaving: Boolean = false,
    val error: String? = null,
)

/** Owns the lightweight live-scan analysis and selected panorama keyframes. */
class GuidedScanViewModel(application: Application) : AndroidViewModel(application) {
    private val motionTracker = MotionTracker()
    private val selector = FrameSelector()
    private val _state = MutableStateFlow(GuidedScanState())
    val state: StateFlow<GuidedScanState> = _state.asStateFlow()

    fun acceptFrame(frame: Bitmap) {
        try {
            val sample = motionTracker.analyze(frame)
            val decision = selector.consider(frame, sample)
            val instruction = when {
                sample.glareRatio >= 0.22 -> "Too much glare — tilt the phone slightly."
                sample.sharpness < 28.0 -> "Image is blurry — move more slowly."
                sample.hasPrevious && sample.speed > 42.0 -> "Slow down so adjacent frames overlap."
                decision == FrameDecision.CAPTURED -> "Good. Keep moving steadily with about half the view overlapping."
                decision == FrameDecision.FULL -> "Enough frames captured. Tap Use Scan."
                else -> "Keep moving slowly across the board."
            }
            _state.value = _state.value.copy(frameCount = selector.count, instruction = instruction, error = null)
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = e.message ?: "Could not analyze camera frame.")
        } finally {
            if (!frame.isRecycled) frame.recycle()
        }
    }

    fun finish(onComplete: (List<SelectedPhoto>) -> Unit) {
        if (selector.count < 2) {
            _state.value = _state.value.copy(error = "Scan at least two overlapping sections before continuing.")
            return
        }
        if (_state.value.isSaving) return
        _state.value = _state.value.copy(isSaving = true, error = null)

        viewModelScope.launch {
            try {
                val photos = selector.keyframes.map { keyFrame ->
                    val file = PhotoStorage.saveScanFrame(getApplication(), keyFrame.bitmap)
                    SelectedPhoto(id = "scan-${UUID.randomUUID()}", file = file)
                }
                onComplete(photos)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSaving = false, error = e.message ?: "Could not save scan frames.")
            }
        }
    }

    fun reset() {
        selector.clear()
        motionTracker.reset()
        _state.value = GuidedScanState()
    }

    override fun onCleared() {
        selector.clear()
        motionTracker.close()
        super.onCleared()
    }
}
