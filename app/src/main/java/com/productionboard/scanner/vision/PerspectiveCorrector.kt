package com.productionboard.scanner.vision

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

data class Corner(val x: Double, val y: Double)

/** top-left, top-right, bottom-right, bottom-left */
data class Quad(val topLeft: Corner, val topRight: Corner, val bottomRight: Corner, val bottomLeft: Corner) {
    fun toList() = listOf(topLeft, topRight, bottomRight, bottomLeft)
}

data class QuadDetectionResult(val quad: Quad?, val areaRatio: Double)

/**
 * Finds the whiteboard's boundary as a quadrilateral so the stitched
 * mosaic (which still has whatever skew the camera sweep left behind)
 * can be warped into a flat, document-scan-style rectangle: edges,
 * dilate to close gaps, largest 4-point contour. When detection isn't
 * confident, [CalibrationScreen]/the corner-adjustment step lets the
 * user drag the corners manually instead - this never hard-fails.
 */
class PerspectiveCorrector {

    fun detectBoardQuad(source: Bitmap): QuadDetectionResult {
        val src = Mat()
        Utils.bitmapToMat(source, src)
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        val dilated = Mat()
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()

        try {
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 50.0, 150.0)
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
            Imgproc.dilate(edges, dilated, kernel)
            kernel.release()

            Imgproc.findContours(dilated, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

            val imageArea = (src.cols() * src.rows()).toDouble()
            var best: Quad? = null
            var bestArea = 0.0

            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area < imageArea * 0.15 || area <= bestArea) continue
                val contour2f = MatOfPoint2f(*contour.toArray())
                val peri = Imgproc.arcLength(contour2f, true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(contour2f, approx, 0.02 * peri, true)
                val approxPoints = approx.toArray()
                if (approxPoints.size == 4 && Imgproc.isContourConvex(MatOfPoint(*approxPoints))) {
                    best = orderCorners(approxPoints.map { Corner(it.x, it.y) })
                    bestArea = area
                }
                contour2f.release()
                approx.release()
            }

            return QuadDetectionResult(best, bestArea / imageArea)
        } finally {
            src.release(); gray.release(); blurred.release(); edges.release(); dilated.release(); hierarchy.release()
            contours.forEach { it.release() }
        }
    }

    fun warpToRectangle(source: Bitmap, quad: Quad, outWidth: Int, outHeight: Int): Bitmap {
        val src = Mat()
        Utils.bitmapToMat(source, src)
        val corners = quad.toList()
        val srcPts = MatOfPoint2f(*corners.map { Point(it.x, it.y) }.toTypedArray())
        val dstPts = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(outWidth.toDouble(), 0.0),
            Point(outWidth.toDouble(), outHeight.toDouble()),
            Point(0.0, outHeight.toDouble()),
        )
        val m = Imgproc.getPerspectiveTransform(srcPts, dstPts)
        val dst = Mat()
        try {
            Imgproc.warpPerspective(src, dst, m, Size(outWidth.toDouble(), outHeight.toDouble()))
            val bitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(dst, bitmap)
            return bitmap
        } finally {
            src.release(); srcPts.release(); dstPts.release(); m.release(); dst.release()
        }
    }
}

private fun orderCorners(pts: List<Corner>): Quad {
    val sums = pts.map { it.x + it.y }
    val diffs = pts.map { it.x - it.y }
    val tl = pts[sums.indexOf(sums.min())]
    val br = pts[sums.indexOf(sums.max())]
    val tr = pts[diffs.indexOf(diffs.max())]
    val bl = pts[diffs.indexOf(diffs.min())]
    return Quad(tl, tr, br, bl)
}

fun defaultQuad(width: Int, height: Int, insetPct: Double = 0.03): Quad {
    val ix = width * insetPct
    val iy = height * insetPct
    return Quad(
        Corner(ix, iy),
        Corner(width - ix, iy),
        Corner(width - ix, height - iy),
        Corner(ix, height - iy),
    )
}
