package com.productionboard.scanner.board

import android.graphics.Bitmap
import com.productionboard.scanner.domain.BoardTemplate
import com.productionboard.scanner.domain.PixelRect
import com.productionboard.scanner.domain.RowRect
import com.productionboard.scanner.domain.maxRowCount
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import kotlin.math.min

/** Locates project rows without performing OCR. */
class BoardDetector {

    /** Existing calibrated-photo path, retained for ordinary/manual photos. */
    fun detectRows(board: Bitmap, template: BoardTemplate): List<RowRect> {
        val boardHeightPx = board.height
        val boardWidthPx = board.width
        val rowHeightPx = template.rowHeightPct.toDouble() * boardHeightPx
        val maxRows = template.maxRowCount(boardHeightPx)
        if (maxRows == 0 || rowHeightPx < 4.0) return emptyList()

        val lines = findHorizontalLines(board)
        val tolerance = rowHeightPx * 0.25
        val rows = mutableListOf<RowRect>()

        for (i in 0 until maxRows) {
            val expectedTop = template.firstRowTopPct.toDouble() * boardHeightPx + i * rowHeightPx
            if (expectedTop >= boardHeightPx) break
            val expectedBottom = expectedTop + rowHeightPx
            val visibleBottom = min(expectedBottom, boardHeightPx.toDouble())
            if ((visibleBottom - expectedTop) < rowHeightPx * 0.6) break

            val snappedTop = snapToLine(lines, expectedTop, tolerance)
            val snappedBottom = snapToLine(lines, expectedBottom, tolerance)
            val top = snappedTop ?: expectedTop
            val bottom = min(snappedBottom ?: visibleBottom, boardHeightPx.toDouble())
            rows += RowRect(
                index = i,
                rect = PixelRect(0, top.toInt(), boardWidthPx, (bottom - top).toInt().coerceAtLeast(4)),
                detected = snappedTop != null || snappedBottom != null,
            )
        }
        return rows
    }

    /**
     * Stitched-scan path. The mosaic itself establishes the coordinate system,
     * so rows are inferred directly from real full-width separator bands rather
     * than reusing first-row/row-height percentages from an unrelated photo.
     */
    fun detectRowsAuto(board: Bitmap): List<RowRect> {
        val lines = findHorizontalLines(board).sorted()
        if (lines.size < 2) return emptyList()

        val minUsefulGap = board.height * 0.025
        val maxUsefulGap = board.height * 0.30
        val rows = mutableListOf<RowRect>()
        for (i in 0 until lines.lastIndex) {
            val top = lines[i]
            val bottom = lines[i + 1]
            val gap = bottom - top
            if (gap < minUsefulGap || gap > maxUsefulGap) continue
            rows += RowRect(
                index = rows.size,
                rect = PixelRect(
                    x = 0,
                    y = top.toInt().coerceAtLeast(0),
                    width = board.width,
                    height = gap.toInt().coerceAtLeast(4),
                ),
                detected = true,
            )
        }
        return rows
    }

    /** Finds thick dark horizontal bands that span most of the board width. */
    private fun findHorizontalLines(board: Bitmap): List<Double> {
        val src = Mat()
        val gray = Mat()
        val rowMeanMat = Mat()
        val otsuOutput = Mat()
        try {
            Utils.bitmapToMat(board, src)
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)

            val rows = gray.rows()
            val cols = gray.cols()
            val rowMean = DoubleArray(rows)
            val rowBuf = ByteArray(cols)
            for (y in 0 until rows) {
                gray.get(y, 0, rowBuf)
                var sum = 0L
                for (b in rowBuf) sum += (b.toInt() and 0xff)
                rowMean[y] = sum.toDouble() / cols
            }

            rowMeanMat.create(rows, 1, CvType.CV_8U)
            rowMeanMat.put(0, 0, ByteArray(rows) { rowMean[it].toInt().coerceIn(0, 255).toByte() })
            val darkThreshold = Imgproc.threshold(
                rowMeanMat,
                otsuOutput,
                0.0,
                255.0,
                Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU,
            )
            val minThicknessPx = (rows * 0.004).coerceAtLeast(2.0)

            val lines = mutableListOf<Double>()
            var runStart = -1
            for (y in 0 until rows) {
                val isDark = rowMean[y] < darkThreshold
                if (isDark && runStart == -1) runStart = y
                else if (!isDark && runStart != -1) {
                    if (y - runStart >= minThicknessPx) lines += (runStart + y - 1) / 2.0
                    runStart = -1
                }
            }
            if (runStart != -1 && rows - runStart >= minThicknessPx) {
                lines += (runStart + rows - 1) / 2.0
            }
            return lines
        } finally {
            src.release(); gray.release(); rowMeanMat.release(); otsuOutput.release()
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
