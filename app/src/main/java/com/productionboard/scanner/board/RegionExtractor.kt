package com.productionboard.scanner.board

import android.graphics.Bitmap
import com.productionboard.scanner.domain.BoardTemplate
import com.productionboard.scanner.domain.FieldKey
import com.productionboard.scanner.domain.PixelRect
import com.productionboard.scanner.domain.RowRect

private const val MIN_OCR_WIDTH = 400

data class ExtractedRegion(val field: FieldKey, val bitmap: Bitmap, val sourceRect: PixelRect)

/**
 * Crops only the three configured field regions out of each detected row
 * - never the whole board. Region rectangles are defined as percentages
 * of the row's bounding box in [BoardTemplate], so they scale
 * automatically with row height and with the canonical board resolution
 * configured in Settings/CalibrationScreen.
 */
class RegionExtractor(private val template: BoardTemplate) {

    fun extractRow(board: Bitmap, row: RowRect): List<ExtractedRegion> = FieldKey.entries.map { field ->
        val frac = template.regions.get(field)
        val rect = PixelRect(
            x = row.rect.x + (frac.xPct * row.rect.width).toInt(),
            y = row.rect.y + (frac.yPct * row.rect.height).toInt(),
            width = (frac.wPct * row.rect.width).toInt().coerceAtLeast(1),
            height = (frac.hPct * row.rect.height).toInt().coerceAtLeast(1),
        )
        val clampedX = rect.x.coerceIn(0, (board.width - 1).coerceAtLeast(0))
        val clampedY = rect.y.coerceIn(0, (board.height - 1).coerceAtLeast(0))
        val clampedW = rect.width.coerceAtMost(board.width - clampedX).coerceAtLeast(1)
        val clampedH = rect.height.coerceAtMost(board.height - clampedY).coerceAtLeast(1)

        var cropped = Bitmap.createBitmap(board, clampedX, clampedY, clampedW, clampedH)
        if (cropped.width < MIN_OCR_WIDTH) {
            val scale = MIN_OCR_WIDTH.toFloat() / cropped.width
            cropped = Bitmap.createScaledBitmap(cropped, (cropped.width * scale).toInt(), (cropped.height * scale).toInt(), true)
        }
        ExtractedRegion(field, cropped, rect)
    }
}
