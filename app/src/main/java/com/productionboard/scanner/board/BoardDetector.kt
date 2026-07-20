package com.productionboard.scanner.board

import android.graphics.Bitmap
import com.productionboard.scanner.domain.BoardTemplate
import com.productionboard.scanner.domain.PixelRect
import com.productionboard.scanner.domain.RowRect
import com.productionboard.scanner.domain.computeRowCount
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * Locates each project row within the corrected board image. The board
 * layout is fixed and already known via [BoardTemplate], so this does
 * not attempt general-purpose table detection - it looks for horizontal
 * separator lines near where the template expects them (a row-wise
 * Sobel-Y gradient-energy projection, peak-picked) and snaps to those
 * when confident, falling back to pure template math otherwise. Never
 * runs OCR - only row geometry.
 */
class BoardDetector {

    fun detectRows(board: Bitmap, template: BoardTemplate): List<RowRect> {
        val rowCount = template.computeRowCount()
        if (rowCount == 0) return emptyList()

        val lines = findHorizontalLines(board)
        val rows = mutableListOf<RowRect>()

        for (i in 0 until rowCount) {
            val expectedTop = template.marginTopPx + i * template.rowHeightPx
            val expectedBottom = expectedTop + template.rowHeightPx
            val tolerance = template.rowHeightPx * 0.25
            val snappedTop = snapToLine(lines, expectedTop.toDouble(), tolerance)
            val snappedBottom = snapToLine(lines, expectedBottom.toDouble(), tolerance)
            val detected = snappedTop != null || snappedBottom != null
            val top = snappedTop ?: expectedTop.toDouble()
            val bottom = snappedBottom ?: expectedBottom.toDouble()
            rows += RowRect(
                index = i,
                rect = PixelRect(0, top.toInt(), template.boardWidthPx, (bottom - top).toInt().coerceAtLeast(4)),
                detected = detected,
            )
        }
        return rows
    }

    /** Row-wise gradient-energy projection; returns y-positions of strong horizontal edges. */
    private fun findHorizontalLines(board: Bitmap): List<Double> {
        val src = Mat()
        Utils.bitmapToMat(board, src)
        val gray = Mat()
        val sobel = Mat()
        try {
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.Sobel(gray, sobel, CvType.CV_32F, 0, 1, 3)
            Core.convertScaleAbs(sobel, sobel)

            val rows = sobel.rows()
            val cols = sobel.cols()
            val rowEnergy = DoubleArray(rows)
            val rowBuf = ByteArray(cols)
            for (y in 0 until rows) {
                sobel.get(y, 0, rowBuf)
                var sum = 0L
                for (b in rowBuf) sum += (b.toInt() and 0xff)
                rowEnergy[y] = sum.toDouble() / cols
            }

            val mean = rowEnergy.average()
            val threshold = mean * 2.2
            val peaks = mutableListOf<Double>()
            for (y in 1 until rowEnergy.size - 1) {
                if (rowEnergy[y] > threshold && rowEnergy[y] >= rowEnergy[y - 1] && rowEnergy[y] >= rowEnergy[y + 1]) {
                    peaks += y.toDouble()
                }
            }
            return peaks
        } finally {
            src.release(); gray.release(); sobel.release()
        }
    }
}

private fun snapToLine(lines: List<Double>, expected: Double, tolerance: Double): Double? {
    var best: Double? = null
    var bestDist = tolerance
    for (y in lines) {
        val dist = kotlin.math.abs(y - expected)
        if (dist <= bestDist) {
            bestDist = dist
            best = y
        }
    }
    return best
}
