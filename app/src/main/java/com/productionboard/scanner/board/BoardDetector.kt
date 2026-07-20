package com.productionboard.scanner.board

import android.graphics.Bitmap
import com.productionboard.scanner.domain.BoardTemplate
import com.productionboard.scanner.domain.PixelRect
import com.productionboard.scanner.domain.RowRect
import com.productionboard.scanner.domain.maxRowCount
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import kotlin.math.min

/**
 * Locates each visible project row within a single photo's board-area
 * crop. Each photo may show a different section of the board (not
 * necessarily starting at row 1), so this doesn't assume a fixed global
 * row index - it applies the calibrated row spacing ([BoardTemplate.rowHeightPct])
 * starting from wherever the crop begins, refined with a row-wise
 * Sobel-Y gradient-energy projection (peak-picked) that snaps to real
 * separator lines when confident. Never runs OCR - only row geometry.
 */
class BoardDetector {

    fun detectRows(board: Bitmap, template: BoardTemplate): List<RowRect> {
        val boardHeightPx = board.height
        val boardWidthPx = board.width
        val rowHeightPx = template.rowHeightPct * boardHeightPx
        val maxRows = template.maxRowCount(boardHeightPx)
        if (maxRows == 0 || rowHeightPx < 4f) return emptyList()

        val lines = findHorizontalLines(board)
        val tolerance = rowHeightPx * 0.25
        val rows = mutableListOf<RowRect>()

        for (i in 0 until maxRows) {
            val expectedTop = template.firstRowTopPct * boardHeightPx + i * rowHeightPx
            if (expectedTop >= boardHeightPx) break
            val expectedBottom = expectedTop + rowHeightPx
            val visibleBottom = min(expectedBottom, boardHeightPx.toDouble())
            // A trailing row that's mostly cut off at the bottom of this photo isn't usable - stop here.
            if ((visibleBottom - expectedTop) < rowHeightPx * 0.6) break

            val snappedTop = snapToLine(lines, expectedTop, tolerance)
            val snappedBottom = snapToLine(lines, expectedBottom, tolerance)
            val detected = snappedTop != null || snappedBottom != null
            val top = snappedTop ?: expectedTop
            val bottom = min(snappedBottom ?: visibleBottom, boardHeightPx.toDouble())

            rows += RowRect(
                index = i,
                rect = PixelRect(0, top.toInt(), boardWidthPx, (bottom - top).toInt().coerceAtLeast(4)),
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
