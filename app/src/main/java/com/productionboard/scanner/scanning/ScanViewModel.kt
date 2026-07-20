package com.productionboard.scanner.scanning

import android.app.Application
import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.productionboard.scanner.board.BoardDetector
import com.productionboard.scanner.board.RegionExtractor
import com.productionboard.scanner.camera.CameraController
import com.productionboard.scanner.domain.AppSettings
import com.productionboard.scanner.domain.FieldKey
import com.productionboard.scanner.domain.FieldResult
import com.productionboard.scanner.domain.ReviewRow
import com.productionboard.scanner.domain.RowRect
import com.productionboard.scanner.domain.ScanIssue
import com.productionboard.scanner.domain.ScanIssueCode
import com.productionboard.scanner.domain.ScanPhase
import com.productionboard.scanner.ocr.OCRValidator
import com.productionboard.scanner.ocr.TesseractOCRProvider
import com.productionboard.scanner.ocr.whitelistFor
import com.productionboard.scanner.vision.ImageStitcher
import com.productionboard.scanner.vision.PerspectiveCorrector
import com.productionboard.scanner.vision.Quad
import com.productionboard.scanner.vision.StitchDebugInfo
import com.productionboard.scanner.vision.defaultQuad
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val MIN_SHARPNESS = 25.0
private const val MIN_TRACKING_CONFIDENCE = 0.25
private const val LOW_LIGHT_THRESHOLD = 55.0
private const val GLARE_THRESHOLD = 0.12
private const val LOST_THRESHOLD = 0.12
private const val MIN_FRAMES_BEFORE_AUTOSTOP = 8
private const val MIN_SCAN_MS_BEFORE_AUTOSTOP = 4000L
private const val TICK_MS = 160L
private val SPEED_FAST_THRESHOLD = SAMPLE_WIDTH * 0.28

data class ScanUiState(
    val phase: ScanPhase = ScanPhase.IDLE,
    val coverageFraction: Float = 0f,
    val coverageSnapshot: CoverageSnapshot? = null,
    val guidance: String? = null,
    val activeIssue: ScanIssue? = null,
    val frameCount: Int = 0,
    val elapsedMs: Long = 0,
    val processingProgress: Float = 0f,
    val processingLabel: String = "",
    val mosaicBitmap: Bitmap? = null,
    val quad: Quad? = null,
    val boardBitmap: Bitmap? = null,
    val rows: List<RowRect>? = null,
    val reviewRows: List<ReviewRow>? = null,
    val stitchDebug: List<StitchDebugInfo>? = null,
    val quadAreaRatio: Double? = null,
    val error: String? = null,
)

/**
 * Orchestrates the full pipeline for one scan session: live camera +
 * motion/coverage tracking while scanning, then (once the user or
 * auto-stop ends the scan) stitching, perspective correction, row
 * detection, region extraction, and OCR - producing review-ready rows.
 * This is the only class that knows the *order* the other modules run
 * in; each of them (MotionTracker, CoverageTracker, FrameSelector,
 * ImageStitcher, PerspectiveCorrector, BoardDetector, RegionExtractor,
 * OCRProvider) stays independently usable/testable.
 */
class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val camera = CameraController(application)
    private var motion: MotionTracker? = null
    private val coverage = CoverageTracker()
    private var frames: FrameSelector? = null
    private val ocr = TesseractOCRProvider(application)

    private var settings: AppSettings = AppSettings.DEFAULT
    private var scanStartedAtMs = 0L
    private var processingJob: Job? = null

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    fun updateSettings(settings: AppSettings) {
        this.settings = settings
    }

    fun startScanning(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        viewModelScope.launch {
            _state.update { ScanUiState(phase = ScanPhase.SCANNING, guidance = "Slowly sweep across the board") }
            coverage.reset()
            frames = FrameSelector()
            scanStartedAtMs = System.currentTimeMillis()

            try {
                camera.start(lifecycleOwner, previewView, settings.cameraResolution, TICK_MS) { bitmap, rotationDegrees ->
                    onFrame(bitmap, rotationDegrees)
                }
            } catch (e: Exception) {
                _state.update { it.copy(phase = ScanPhase.IDLE, error = e.message ?: "Could not access the camera.") }
            }
        }
    }

    // rotationDegrees is unused here - YuvToBitmap already rotates the bitmap upright before this callback fires.
    private fun onFrame(bitmap: Bitmap, @Suppress("UNUSED_PARAMETER") rotationDegrees: Int) {
        if (_state.value.phase != ScanPhase.SCANNING) {
            bitmap.recycle()
            return
        }
        if (motion == null) motion = MotionTracker(bitmap.width.toDouble() / bitmap.height)
        val tracker = motion ?: return
        val selector = frames ?: return

        val sample = tracker.analyze(bitmap)
        val qualityOk = sample.sharpness >= MIN_SHARPNESS && sample.trackingConfidence >= MIN_TRACKING_CONFIDENCE
        coverage.update(sample, qualityOk)
        selector.consider(bitmap, sample)

        val issue = classifyIssue(sample, selector)
        val snapshot = coverage.snapshot()
        val elapsed = System.currentTimeMillis() - scanStartedAtMs

        _state.update {
            it.copy(
                coverageFraction = snapshot.coverageFraction,
                coverageSnapshot = snapshot,
                guidance = issue?.message ?: "Board Coverage",
                activeIssue = issue,
                frameCount = selector.count,
                elapsedMs = elapsed,
            )
        }

        val readyToAutostop = snapshot.coverageFraction >= settings.coverageThreshold &&
            selector.count >= MIN_FRAMES_BEFORE_AUTOSTOP &&
            elapsed >= MIN_SCAN_MS_BEFORE_AUTOSTOP

        if (readyToAutostop || selector.isFull) {
            requestStop()
        }
    }

    private fun classifyIssue(sample: MotionSample, selector: FrameSelector): ScanIssue? {
        if (sample.hasPrevious && sample.trackingConfidence < LOST_THRESHOLD) {
            return ScanIssue(ScanIssueCode.TRACKING_LOST, "Move back to the highlighted area")
        }
        if (sample.speed > SPEED_FAST_THRESHOLD) {
            return ScanIssue(ScanIssueCode.MOTION_TOO_FAST, "Move slower")
        }
        if (sample.sharpness < MIN_SHARPNESS) {
            return ScanIssue(ScanIssueCode.BLUR, "Hold steady")
        }
        if (sample.brightness < LOW_LIGHT_THRESHOLD) {
            return ScanIssue(ScanIssueCode.LOW_LIGHT, "Poor lighting - move to a brighter area")
        }
        if (sample.glareRatio > GLARE_THRESHOLD) {
            return ScanIssue(ScanIssueCode.GLARE, "Glare detected - adjust angle")
        }
        if (selector.count > 0 && selector.overdueRatio > 1.4) {
            return ScanIssue(ScanIssueCode.INSUFFICIENT_OVERLAP, "Slow down to re-cover the last section")
        }
        return null
    }

    /** Manual (Done Scanning) or automatic stop. Pauses for confirmation if coverage is short of threshold. */
    fun requestStop() {
        if (_state.value.phase != ScanPhase.SCANNING) return
        val snapshot = coverage.snapshot()
        if (snapshot.coverageFraction < settings.coverageThreshold) {
            camera.stop()
            _state.update {
                it.copy(
                    phase = ScanPhase.CONFIRM_INCOMPLETE,
                    activeIssue = ScanIssue(
                        ScanIssueCode.INCOMPLETE_SCAN,
                        "Only ${(snapshot.coverageFraction * 100).toInt()}% of the board looks covered.",
                    ),
                )
            }
            return
        }
        camera.stop()
        beginProcessing()
    }

    fun resumeScanning(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        if (_state.value.phase != ScanPhase.CONFIRM_INCOMPLETE) return
        _state.update { it.copy(phase = ScanPhase.SCANNING, activeIssue = null) }
        viewModelScope.launch {
            camera.start(lifecycleOwner, previewView, settings.cameraResolution, TICK_MS) { bitmap, rotationDegrees ->
                onFrame(bitmap, rotationDegrees)
            }
        }
    }

    fun forceProcessAnyway() {
        if (_state.value.phase != ScanPhase.CONFIRM_INCOMPLETE) return
        beginProcessing()
    }

    private fun beginProcessing() {
        val selector = frames ?: return
        if (selector.count == 0) {
            _state.update { it.copy(phase = ScanPhase.IDLE, error = "No usable frames were captured. Try scanning again, moving more slowly.") }
            return
        }

        processingJob = viewModelScope.launch {
            try {
                _state.update { it.copy(phase = ScanPhase.STITCHING, processingProgress = 0f, processingLabel = "Stitching frames...") }
                val workingWidth = selector.keyframes[0].bitmap.width
                val stitcher = ImageStitcher(workingWidth.toDouble() / SAMPLE_WIDTH)
                val result = stitcher.stitch(selector.keyframes) { done, total ->
                    _state.update { it.copy(processingProgress = done.toFloat() / total) }
                }

                val corrector = PerspectiveCorrector()
                val detection = corrector.detectBoardQuad(result.bitmap)
                val quad = detection.quad ?: defaultQuad(result.bitmap.width, result.bitmap.height)

                _state.update {
                    it.copy(
                        phase = ScanPhase.ADJUST_CORNERS,
                        mosaicBitmap = result.bitmap,
                        quad = quad,
                        quadAreaRatio = detection.areaRatio,
                        stitchDebug = result.debug,
                        processingProgress = 1f,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(phase = ScanPhase.IDLE, error = e.message ?: "Stitching failed.") }
            }
        }
    }

    fun updateQuad(quad: Quad) {
        if (_state.value.phase != ScanPhase.ADJUST_CORNERS) return
        _state.update { it.copy(quad = quad) }
    }

    fun confirmCorners(quad: Quad) {
        val mosaic = _state.value.mosaicBitmap ?: return
        if (_state.value.phase != ScanPhase.ADJUST_CORNERS) return

        processingJob = viewModelScope.launch {
            val template = settings.boardTemplate
            _state.update { it.copy(phase = ScanPhase.CORRECTING, processingProgress = 0f, processingLabel = "Correcting perspective...") }
            val corrector = PerspectiveCorrector()
            val board = corrector.warpToRectangle(mosaic, quad, template.boardWidthPx, template.boardHeightPx)

            _state.update { it.copy(phase = ScanPhase.DETECTING, boardBitmap = board, processingProgress = 0f, processingLabel = "Detecting rows...") }
            val rows = BoardDetector().detectRows(board, template)

            _state.update { it.copy(rows = rows, phase = ScanPhase.OCR, processingProgress = 0f, processingLabel = "Reading project rows...") }
            val reviewRows = runOcr(board, rows)

            _state.update { it.copy(phase = ScanPhase.REVIEW, reviewRows = reviewRows, processingProgress = 1f) }
        }
    }

    private suspend fun runOcr(board: Bitmap, rows: List<RowRect>): List<ReviewRow> {
        ocr.initialize()
        val extractor = RegionExtractor(settings.boardTemplate)
        val results = mutableListOf<ReviewRow>()
        val total = maxOf(1, rows.size)

        for ((i, row) in rows.withIndex()) {
            val regions = extractor.extractRow(board, row)
            val byField = mutableMapOf<FieldKey, FieldResult>()

            for (region in regions) {
                val ocrResult = ocr.recognize(region.bitmap, whitelistFor(region.field))
                val fieldResult = when (region.field) {
                    FieldKey.PROJECT_NUMBER -> OCRValidator.projectNumber(ocrResult.text, ocrResult.confidence)
                    FieldKey.CUSTOMER -> OCRValidator.customer(ocrResult.text, ocrResult.confidence)
                    FieldKey.DAYS_REMAINING -> OCRValidator.daysRemaining(ocrResult.text, ocrResult.confidence)
                }
                byField[region.field] = fieldResult
            }

            val projectNumber = byField.getValue(FieldKey.PROJECT_NUMBER)
            val customer = byField.getValue(FieldKey.CUSTOMER)
            val daysRemaining = byField.getValue(FieldKey.DAYS_REMAINING)
            val threshold = settings.ocrConfidenceThreshold
            val needsReview = !OCRValidator.isOk(projectNumber, threshold) ||
                !OCRValidator.isOk(customer, threshold) ||
                !OCRValidator.isOk(daysRemaining, threshold)

            results += ReviewRow(
                id = "row-${row.index}-${System.currentTimeMillis()}",
                rowIndex = row.index,
                projectNumber = projectNumber,
                customer = customer,
                daysRemaining = daysRemaining,
                verified = !needsReview,
                needsReview = needsReview,
            )

            _state.update { it.copy(processingProgress = (i + 1).toFloat() / total) }
        }

        ocr.close()
        return results.filter { it.projectNumber.value.isNotBlank() || it.customer.value.isNotBlank() }
    }

    fun cancelScan() {
        processingJob?.cancel()
        camera.stop()
        motion?.destroy()
        motion = null
        _state.update { ScanUiState() }
    }

    override fun onCleared() {
        super.onCleared()
        camera.shutdown()
        motion?.destroy()
        ocr.close()
    }
}
