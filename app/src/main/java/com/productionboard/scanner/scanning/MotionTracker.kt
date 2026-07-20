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

const val SAMPLE_WIDTH = 320

data class MotionSample(
    val dx: Double,
    val dy: Double,
    val speed: Double,
    val sharpness: Double,
    val trackingConfidence: Double,
    val brightness: Double,
    val glareRatio: Double,
    val hasPrevious: Boolean,
)

/**
 * Tracks frame-to-frame camera motion using phase correlation (cheap
 * enough to run on every live-preview frame) plus a Laplacian-variance
 * sharpness score, mean brightness, and a saturated-pixel ratio for
 * glare. This is deliberately cheaper than the ORB/homography pipeline
 * used for final stitching (see [com.productionboard.scanner.vision.ImageStitcher]) -
 * live tracking only needs "which way and how fast", not pixel-accurate
 * alignment.
 */
class MotionTracker(aspectRatio: Double) {
    private val sampleHeight = max(2, (SAMPLE_WIDTH / aspectRatio).toInt())
    private val hann = Mat().also { Imgproc.createHanningWindow(it, Size(SAMPLE_WIDTH.toDouble(), sampleHeight.toDouble()), CvType.CV_32F) }
    private var prevGray: Mat? = null

    fun reset() {
        prevGray?.release()
        prevGray = null
    }

    fun analyze(frame: Bitmap): MotionSample {
        val full = Mat()
        Utils.bitmapToMat(frame, full)
        val small = Mat()
        Imgproc.resize(full, small, Size(SAMPLE_WIDTH.toDouble(), sampleHeight.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
        full.release()
        val gray = Mat()
        Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY)
        small.release()

        val brightness = Core.mean(gray).`val`[0]
        val glareRatio = glareRatio(gray)
        val sharpness = sharpness(gray)

        var dx = 0.0
        var dy = 0.0
        var trackingConfidence = 0.0
        val hasPrevious = prevGray != null

        prevGray?.let { prev ->
            val grayF = Mat()
            val prevF = Mat()
            gray.convertTo(grayF, CvType.CV_32F)
            prev.convertTo(prevF, CvType.CV_32F)
            runCatching {
                val shift = Video.phaseCorrelate(prevF, grayF, hann)
                dx = shift.x
                dy = shift.y

                // The Java bindings don't expose phaseCorrelate's peak-response
                // score, so confidence is approximated from image texture (how
                // much signal there was to correlate against) plus a sanity
                // check on the shift magnitude.
                val meanM = MatOfDouble()
                val stddevM = MatOfDouble()
                Core.meanStdDev(gray, meanM, stddevM)
                val textureScore = min(1.0, stddevM.toArray().getOrElse(0) { 0.0 } / 35.0)
                val maxPlausibleShift = SAMPLE_WIDTH * 0.4
                val plausibility = max(0.0, 1.0 - max(0.0, hypot(dx, dy) - maxPlausibleShift) / maxPlausibleShift)
                trackingConfidence = min(textureScore, textureScore * 0.5 + plausibility * 0.5)
            }.onFailure { trackingConfidence = 0.0 }
            grayF.release()
            prevF.release()
        }

        prevGray?.release()
        prevGray = gray

        val speed = hypot(dx, dy)
        return MotionSample(dx, dy, speed, sharpness, trackingConfidence, brightness, glareRatio, hasPrevious)
    }

    private fun sharpness(gray: Mat): Double {
        val lap = Mat()
        Imgproc.Laplacian(gray, lap, CvType.CV_64F)
        val mean = MatOfDouble()
        val stddev = MatOfDouble()
        Core.meanStdDev(lap, mean, stddev)
        val std = stddev.toArray().getOrElse(0) { 0.0 }
        lap.release()
        return std * std
    }

    private fun glareRatio(gray: Mat): Double {
        val thresh = Mat()
        Imgproc.threshold(gray, thresh, 250.0, 255.0, Imgproc.THRESH_BINARY)
        val saturated = Core.countNonZero(thresh)
        thresh.release()
        return saturated.toDouble() / (gray.rows() * gray.cols())
    }

    fun destroy() {
        prevGray?.release()
        prevGray = null
        hann.release()
    }
}
