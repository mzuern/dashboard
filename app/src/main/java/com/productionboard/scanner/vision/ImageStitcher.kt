package com.productionboard.scanner.vision

import android.graphics.Bitmap
import com.productionboard.scanner.scanning.KeyFrame
import kotlinx.coroutines.delay
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

data class StitchDebugInfo(val frameIndex: Int, val matchedFeatures: Int, val usedFallback: Boolean, val placementX: Double, val placementY: Double)
data class StitchResult(val bitmap: Bitmap, val debug: List<StitchDebugInfo>)

private const val MIN_GOOD_MATCHES = 8
private const val RANSAC_REPROJ_THRESHOLD = 4.0
private const val MAX_CANVAS_DIMENSION = 6000

/**
 * Stitches keyframes into a single mosaic using ORB feature matching +
 * RANSAC homography estimation - the classic OpenCV panorama recipe:
 * detect features, match, estimate a transform mapping each new frame
 * into the growing mosaic's coordinate space, warp, and composite.
 * Frames are matched against a cropped region of interest of the mosaic
 * (near where FrameSelector's tracked position predicts they should
 * land) rather than the whole mosaic, which keeps matching fast and
 * avoids false matches against repeated whiteboard text elsewhere on
 * the board. Falls back to a translation-only placement per-frame if
 * matching fails, rather than aborting the whole stitch.
 */
class ImageStitcher(private val trackUnitToWorkingPx: Double) {

    suspend fun stitch(frames: List<KeyFrame>, onProgress: ((done: Int, total: Int) -> Unit)? = null): StitchResult {
        require(frames.isNotEmpty()) { "No frames to stitch." }
        val debug = mutableListOf<StitchDebugInfo>()

        if (frames.size == 1) {
            return StitchResult(frames[0].bitmap, debug)
        }

        val conv = trackUnitToWorkingPx
        val xs = frames.map { it.camX * conv }
        val ys = frames.map { it.camY * conv }
        val frameW = frames[0].bitmap.width
        val frameH = frames[0].bitmap.height
        val pad = (max(frameW, frameH) * 0.5).toInt()

        val minX = xs.min() - pad
        val maxX = xs.max() + frameW + pad
        val minY = ys.min() - pad
        val maxY = ys.max() + frameH + pad

        var canvasW = (maxX - minX).toInt()
        var canvasH = (maxY - minY).toInt()
        var scaleDown = 1.0
        if (max(canvasW, canvasH) > MAX_CANVAS_DIMENSION) {
            scaleDown = MAX_CANVAS_DIMENSION.toDouble() / max(canvasW, canvasH)
            canvasW = (canvasW * scaleDown).toInt()
            canvasH = (canvasH * scaleDown).toInt()
        }

        val originX = -minX * scaleDown
        val originY = -minY * scaleDown

        val mosaic = Mat(canvasH, canvasW, CvType.CV_8UC4, Scalar(0.0, 0.0, 0.0, 0.0))
        val mosaicMask = Mat(canvasH, canvasW, CvType.CV_8UC1, Scalar(0.0))

        try {
            val h0 = scaleTranslateMat(scaleDown, originX, originY)
            warpAndComposite(frames[0].bitmap, h0, mosaic, mosaicMask)
            h0.release()
            debug += StitchDebugInfo(0, 0, false, originX, originY)
            onProgress?.invoke(1, frames.size)

            for (i in 1 until frames.size) {
                val frame = frames[i]
                val expectedX = originX + (xs[i] - xs[0]) * scaleDown
                val expectedY = originY + (ys[i] - ys[0]) * scaleDown

                val match = estimateHomography(frame.bitmap, mosaic, mosaicMask, expectedX, expectedY, scaleDown)
                val h: Mat
                var usedFallback = false
                var matchedFeatures = 0
                if (match != null) {
                    h = match.first
                    matchedFeatures = match.second
                } else {
                    h = scaleTranslateMat(scaleDown, expectedX, expectedY)
                    usedFallback = true
                }

                warpAndComposite(frame.bitmap, h, mosaic, mosaicMask)
                h.release()
                debug += StitchDebugInfo(i, matchedFeatures, usedFallback, expectedX, expectedY)
                onProgress?.invoke(i + 1, frames.size)
                delay(1) // yield so the processing screen / cancellation stays responsive
            }

            val cropped = cropToContent(mosaic, mosaicMask)
            val bitmap = Bitmap.createBitmap(cropped.cols(), cropped.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(cropped, bitmap)
            cropped.release()
            return StitchResult(bitmap, debug)
        } finally {
            mosaic.release()
            mosaicMask.release()
        }
    }

    private fun estimateHomography(
        frameBitmap: Bitmap,
        mosaic: Mat,
        mosaicMask: Mat,
        expectedX: Double,
        expectedY: Double,
        scaleDown: Double,
    ): Pair<Mat, Int>? {
        val frameW = (frameBitmap.width * scaleDown).toInt()
        val frameH = (frameBitmap.height * scaleDown).toInt()
        val roiPad = (max(frameW, frameH) * 0.5).toInt()

        val roiX = clamp((expectedX - roiPad).toInt(), 0, mosaic.cols() - 1)
        val roiY = clamp((expectedY - roiPad).toInt(), 0, mosaic.rows() - 1)
        val roiW = clamp(frameW + roiPad * 2, 1, mosaic.cols() - roiX)
        val roiH = clamp(frameH + roiPad * 2, 1, mosaic.rows() - roiY)
        val roiRect = Rect(roiX, roiY, roiW, roiH)

        val mosaicROI = Mat(mosaic, roiRect)
        val maskROI = Mat(mosaicMask, roiRect)
        if (Core.countNonZero(maskROI) < 200) return null

        val frameFull = Mat()
        Utils.bitmapToMat(frameBitmap, frameFull)
        val frame = Mat()
        Imgproc.resize(frameFull, frame, Size(frameW.toDouble(), frameH.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
        frameFull.release()

        val grayFrame = Mat()
        val grayRoi = Mat()
        Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.cvtColor(mosaicROI, grayRoi, Imgproc.COLOR_RGBA2GRAY)

        val orb = ORB.create(600)
        val kp1 = MatOfKeyPoint()
        val kp2 = MatOfKeyPoint()
        val desc1 = Mat()
        val desc2 = Mat()
        orb.detectAndCompute(grayFrame, Mat(), kp1, desc1)
        orb.detectAndCompute(grayRoi, maskROI, kp2, desc2)

        var result: Pair<Mat, Int>? = null

        if (desc1.rows() >= 4 && desc2.rows() >= 4) {
            val bf = BFMatcher.create(Core.NORM_HAMMING, false)
            val knn = mutableListOf<MatOfDMatch>()
            bf.knnMatch(desc1, desc2, knn, 2)

            val kp1Arr = kp1.toArray()
            val kp2Arr = kp2.toArray()
            val srcPts = mutableListOf<Point>()
            val dstPts = mutableListOf<Point>()
            for (pair in knn) {
                val matches = pair.toArray()
                if (matches.size < 2) continue
                val m = matches[0]
                val n = matches[1]
                if (m.distance < 0.75f * n.distance) {
                    val p1 = kp1Arr[m.queryIdx].pt
                    val p2 = kp2Arr[m.trainIdx].pt
                    srcPts += p1
                    dstPts += Point(p2.x + roiX, p2.y + roiY)
                }
            }

            if (srcPts.size >= MIN_GOOD_MATCHES) {
                val srcMat = MatOfPoint2f().apply { fromList(srcPts) }
                val dstMat = MatOfPoint2f().apply { fromList(dstPts) }
                val inlierMask = Mat()
                val h = Calib3d.findHomography(srcMat, dstMat, Calib3d.RANSAC, RANSAC_REPROJ_THRESHOLD, inlierMask)
                val inliers = Core.countNonZero(inlierMask)

                if (!h.empty() && inliers >= MIN_GOOD_MATCHES && isSaneHomography(h, expectedX, expectedY, frameW.toDouble())) {
                    result = h to inliers
                } else {
                    h.release()
                }
                srcMat.release()
                dstMat.release()
                inlierMask.release()
            }
            bf.clear()
        }

        result?.let { (h, inliers) ->
            val s = Mat(3, 3, CvType.CV_64F)
            s.put(0, 0, scaleDown, 0.0, 0.0, 0.0, scaleDown, 0.0, 0.0, 0.0, 1.0)
            val composed = Mat()
            Core.gemm(h, s, 1.0, Mat(), 0.0, composed)
            h.release()
            s.release()
            result = composed to inliers
        }

        frame.release(); grayFrame.release(); grayRoi.release()
        kp1.release(); kp2.release(); desc1.release(); desc2.release()
        mosaicROI.release(); maskROI.release()

        return result
    }

    private fun warpAndComposite(frameBitmap: Bitmap, h: Mat, mosaic: Mat, mosaicMask: Mat) {
        val frame = Mat()
        Utils.bitmapToMat(frameBitmap, frame)
        val size = Size(mosaic.cols().toDouble(), mosaic.rows().toDouble())

        val warped = Mat()
        Imgproc.warpPerspective(frame, warped, h, size, Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar(0.0, 0.0, 0.0, 0.0))

        val srcMask = Mat(frame.rows(), frame.cols(), CvType.CV_8UC1, Scalar(255.0))
        val warpedMask = Mat()
        Imgproc.warpPerspective(srcMask, warpedMask, h, size, Imgproc.INTER_NEAREST, Core.BORDER_CONSTANT, Scalar(0.0))

        warped.copyTo(mosaic, warpedMask)
        Core.bitwise_or(mosaicMask, warpedMask, mosaicMask)

        frame.release(); warped.release(); srcMask.release(); warpedMask.release()
    }

    private fun cropToContent(mosaic: Mat, mosaicMask: Mat): Mat {
        val rect = Imgproc.boundingRect(mosaicMask)
        if (rect.width <= 0 || rect.height <= 0) return mosaic.clone()
        return Mat(mosaic, rect).clone()
    }
}

private fun scaleTranslateMat(scale: Double, tx: Double, ty: Double): Mat {
    val m = Mat(3, 3, CvType.CV_64F)
    m.put(0, 0, scale, 0.0, tx, 0.0, scale, ty, 0.0, 0.0, 1.0)
    return m
}

private fun clamp(v: Int, lo: Int, hi: Int): Int = max(lo, min(hi, v))

private fun isSaneHomography(h: Mat, expectedX: Double, expectedY: Double, frameW: Double): Boolean {
    val d = DoubleArray(9)
    h.get(0, 0, d)
    val det = d[0] * d[4] - d[1] * d[3]
    if (det < 0.4 || det > 2.5) return false
    val tx = d[2]
    val ty = d[5]
    val dist = kotlin.math.hypot(tx - expectedX, ty - expectedY)
    return dist < frameW * 1.5
}
