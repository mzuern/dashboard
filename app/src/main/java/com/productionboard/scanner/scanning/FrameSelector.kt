package com.productionboard.scanner.scanning

import android.graphics.Bitmap
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

data class KeyFrame(val bitmap: Bitmap, val camX: Double, val camY: Double, val capturedAtMs: Long)

enum class FrameDecision { CAPTURED, SKIPPED_QUALITY, SKIPPED_SPACING, FULL }

data class FrameSelectorOptions(
    val sampleWidth: Int = SAMPLE_WIDTH,
    val minSharpness: Double = 25.0,
    val minTrackingConfidence: Double = 0.25,
    val maxFrames: Int = 140,
    /** Max working width for stored keyframes - kept modest for stitching memory/perf. */
    val workingWidth: Int = 1280,
)

/**
 * Decides which live frames are worth keeping for stitching. Frames are
 * rejected for being blurry, overexposed/glare, or too similar to the
 * last keyframe (spacing), and kept when they add new, sharp, tracked
 * coverage with roughly 40-70% overlap to the previous keyframe.
 */
class FrameSelector(private val opts: FrameSelectorOptions = FrameSelectorOptions()) {
    private val frames = mutableListOf<KeyFrame>()
    private var lastKeyframeX: Double? = null
    private var lastKeyframeY: Double? = null
    private var camX = 0.0
    private var camY = 0.0
    private var distanceSinceKeyframe = 0.0

    val count get() = frames.size
    val isFull get() = frames.size >= opts.maxFrames
    val keyframes: List<KeyFrame> get() = frames

    /** How far (in sampleWidth units) we've drifted past ideal spacing without a good capture - used for "insufficient overlap" guidance. */
    val overdueRatio: Double
        get() {
            val maxSpacing = opts.sampleWidth * 0.75
            return distanceSinceKeyframe / maxSpacing
        }

    fun reset() {
        frames.forEach { it.bitmap.recycle() }
        frames.clear()
        lastKeyframeX = null
        lastKeyframeY = null
        camX = 0.0
        camY = 0.0
        distanceSinceKeyframe = 0.0
    }

    fun consider(source: Bitmap, sample: MotionSample): FrameDecision {
        if (isFull) return FrameDecision.FULL

        if (sample.hasPrevious) {
            camX += sample.dx
            camY += sample.dy
        }

        val qualityOk = sample.sharpness >= opts.minSharpness && sample.trackingConfidence >= opts.minTrackingConfidence

        if (frames.isEmpty()) {
            if (!qualityOk) return FrameDecision.SKIPPED_QUALITY
            capture(source)
            return FrameDecision.CAPTURED
        }

        val dxSinceKey = camX - (lastKeyframeX ?: 0.0)
        val dySinceKey = camY - (lastKeyframeY ?: 0.0)
        distanceSinceKeyframe = hypot(dxSinceKey, dySinceKey)

        val minSpacing = opts.sampleWidth * 0.3
        if (distanceSinceKeyframe < minSpacing) return FrameDecision.SKIPPED_SPACING
        if (!qualityOk) return FrameDecision.SKIPPED_QUALITY

        capture(source)
        return FrameDecision.CAPTURED
    }

    private fun capture(source: Bitmap) {
        val scale = min(1.0, opts.workingWidth.toDouble() / source.width)
        val w = (source.width * scale).roundToInt().coerceAtLeast(1)
        val h = (source.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = if (scale < 1.0) Bitmap.createScaledBitmap(source, w, h, true) else source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
        frames.add(KeyFrame(scaled, camX, camY, System.currentTimeMillis()))
        lastKeyframeX = camX
        lastKeyframeY = camY
    }
}
