package com.productionboard.scanner.scanning

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private const val SAMPLE_WIDTH = 320

data class MotionSample(
    val dx: Double,
    val dy: Double,
    val speed: Double,
    val sharpness: Double,
    val trackingConfidence: Double,
    val glareRatio: Double,
    val hasPrevious: Boolean,
)

/** Cheap frame-to-frame tracking used only to decide when a useful overlapping frame should be retained. */
class MotionTracker(aspectRatio: Double = 16.0 / 9.0) {
    private val sampleHeight = max(2, (SAMPLE_WIDTH / aspectRatio).toInt())
    private val hann = Mat().also {
        Imgproc.createHanningWindow(it, Size(SAMPLE_WIDTH.toDouble(), sampleHeight.toDouble()), CvType.CV_32F)
    }
    private var previous: Mat? = null

    fun analyze(frame: Bitmap): MotionSample {
        val rgba = Mat()
        val small = Mat()
        val gray = Mat()
        Utils.bitmapToMat(frame, rgba)
        Imgproc.resize(rgba, small, Size(SAMPLE_WIDTH.toDouble(), sampleHeight.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
        Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY)
        rgba.release(); small.release()

        val sharpness = sharpness(gray)
        val glare = glareRatio(gray)
        var dx = 0.0
        var dy = 0.0
        var confidence = 0.0
        val hadPrevious = previous != null

        previous?.let { prev ->
            val currentF = Mat()
            val previousF = Mat()
            gray.convertTo(currentF, CvType.CV_32F)
            prev.convertTo(previousF, CvType.CV_32F)
            runCatching {
                val shift = Video.phaseCorrelate(previousF, currentF, hann)
                dx = shift.x
                dy = shift.y
                val mean = MatOfDouble()
                val std = MatOfDouble()
                Core.meanStdDev(gray, mean, std)
                val texture = min(1.0, std.toArray().getOrElse(0) { 0.0 } / 35.0)
                val plausible = max(0.0, 1.0 - hypot(dx, dy) / (SAMPLE_WIDTH * 0.55))
                confidence = texture * plausible
            }
            currentF.release(); previousF.release()
        }

        previous?.release()
        previous = gray
        return MotionSample(dx, dy, hypot(dx, dy), sharpness, confidence, glare, hadPrevious)
    }

    private fun sharpness(gray: Mat): Double {
        val lap = Mat()
        val mean = MatOfDouble()
        val std = MatOfDouble()
        Imgproc.Laplacian(gray, lap, CvType.CV_64F)
        Core.meanStdDev(lap, mean, std)
        val s = std.toArray().getOrElse(0) { 0.0 }
        lap.release()
        return s * s
    }

    private fun glareRatio(gray: Mat): Double {
        val mask = Mat()
        Imgproc.threshold(gray, mask, 250.0, 255.0, Imgproc.THRESH_BINARY)
        val ratio = Core.countNonZero(mask).toDouble() / (gray.rows() * gray.cols())
        mask.release()
        return ratio
    }

    fun reset() {
        previous?.release()
        previous = null
    }

    fun close() {
        reset()
        hann.release()
    }
}
