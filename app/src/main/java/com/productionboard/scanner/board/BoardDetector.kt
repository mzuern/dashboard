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

/**
 * Locates each visible project row within a single photo's board-area
 * crop. Each photo may show a different section of the board (not
 * necessarily starting at row 1), so this doesn't assume a fixed global
 * row index - it applies the calibrated row spacing ([BoardTemplate.rowHeightPct])
 * starting from wherever the crop begins, refined with a row-wise dark-band
 * projection that snaps to real separator lines when confident. Never runs
 * OCR - only row geometry.
 */
class BoardDetector {

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

    /**
     * Row-wise darkness projection; returns the center y of each thick,
     * near-black band spanning the crop's full width.
     *
     * A pure gradient-peak approach (tried first, verified against real
     * board photos) regularly locked onto a card's own internal grid lines
     * - e.g. the rule under "Turn to Shipping:" - because a curled or
     * overlapping laminated card can produce as strong an edge as the
     * actual board divider. The board's real row dividers are a physically
     * thick black tape/marker line running the full row width, while
     * internal card rules and handwriting are thin and/or don't span the
     * whole width - averaging brightness across each full row (rather than
     * looking at gradient magnitude) and requiring a minimum run length
     * suppresses both of those false positives.
     *
     * The dark/light cutoff is Otsu's threshold computed over the row-mean
     * profile itself (not a fixed or fixed-fraction value): tried against
     * real board photos, a fixed fraction of the overall image brightness
     * missed dividers that were partially washed out by glare in one part
     * of the frame, while Otsu on the per-row profile - which only has to
     * separate "mostly-white rows" from "mostly-black rows", a strongly
     * bimodal signal - found all of them.
     */
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
            val darkThreshold = Imgproc.threshold(rowMeanMat, otsuOutput, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
            val minThicknessPx = (rows * 0.006).coerceAtLeast(3.0)

            val lines = mutableListOf<Double>()
            var runStart = -1
            for (y in 0 until rows) {
                val isDark = rowMean[y] < darkThreshold
                if (isDark && runStart == -1) {
                    runStart = y
                } else if (!isDark && runStart != -1) {
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
