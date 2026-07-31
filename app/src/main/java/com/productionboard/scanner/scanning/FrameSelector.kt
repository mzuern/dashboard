package com.productionboard.scanner.scanning

import android.graphics.Bitmap
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

data class KeyFrame(val bitmap: Bitmap, val camX: Double, val camY: Double)

enum class FrameDecision { CAPTURED, SKIPPED_QUALITY, SKIPPED_SPACING, FULL }

/** Keeps sharp frames far enough apart to add coverage while retaining substantial overlap for stitching. */
class FrameSelector(
    private val minSharpness: Double = 28.0,
    private val minTrackingConfidence: Double = 0.20,
    private val maxFrames: Int = 60,
    private val workingWidth: Int = 1600,
) {
    private val frames = mutableListOf<KeyFrame>()
    private var camX = 0.0
    private var camY = 0.0
    private var lastX: Double? = null
    private var lastY: Double? = null

    val keyframes: List<KeyFrame> get() = frames
    val count: Int get() = frames.size

    fun consider(source: Bitmap, sample: MotionSample): FrameDecision {
        if (frames.size >= maxFrames) return FrameDecision.FULL
        if (sample.hasPrevious) {
            camX += sample.dx
            camY += sample.dy
        }

        val qualityOk = sample.sharpness >= minSharpness &&
            (frames.isEmpty() || sample.trackingConfidence >= minTrackingConfidence) &&
            sample.glareRatio < 0.22
        if (!qualityOk) return FrameDecision.SKIPPED_QUALITY

        if (frames.isNotEmpty()) {
            val distance = hypot(camX - (lastX ?: 0.0), camY - (lastY ?: 0.0))
            // Motion is measured on a 320px tracking image. Roughly 90-150px between
            // captures leaves enough common content for panorama matching.
            if (distance < 95.0) return FrameDecision.SKIPPED_SPACING
        }

        val scale = min(1.0, workingWidth.toDouble() / source.width)
        val w = (source.width * scale).roundToInt().coerceAtLeast(1)
        val h = (source.height * scale).roundToInt().coerceAtLeast(1)
        val copy = if (scale < 1.0) Bitmap.createScaledBitmap(source, w, h, true)
        else source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
        frames += KeyFrame(copy, camX, camY)
        lastX = camX
        lastY = camY
        return FrameDecision.CAPTURED
    }

    fun clear() {
        frames.forEach { it.bitmap.recycle() }
        frames.clear()
        camX = 0.0
        camY = 0.0
        lastX = null
        lastY = null
    }
}
