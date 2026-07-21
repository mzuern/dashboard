package com.productionboard.scanner.processing

import android.graphics.Bitmap
import com.productionboard.scanner.board.BoardDetector
import com.productionboard.scanner.board.ExtractedRegion
import com.productionboard.scanner.board.RegionExtractor
import com.productionboard.scanner.domain.BoardTemplate
import com.productionboard.scanner.domain.FieldKey
import com.productionboard.scanner.domain.FieldResult
import com.productionboard.scanner.domain.PixelRect
import com.productionboard.scanner.domain.ReviewRow
import com.productionboard.scanner.domain.RowRect
import com.productionboard.scanner.ocr.OCRProvider
import com.productionboard.scanner.ocr.OCRValidator
import com.productionboard.scanner.ocr.whitelistFor
import com.productionboard.scanner.photo.SelectedPhoto
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Processes one photo independently end to end: orientation correction,
 * crop to the calibrated board area, best-effort perspective correction,
 * row detection, per-field region extraction, and local OCR. Photos are
 * never combined here - each photo yields its own candidate rows, and
 * the caller ([ProcessingViewModel]) is responsible for combining them
 * and flagging duplicates.
 */
class PhotoProcessor(private val ocr: OCRProvider) {

    suspend fun process(
        photo: SelectedPhoto,
        photoIndex: Int,
        template: BoardTemplate,
        confidenceThreshold: Float,
    ): List<ReviewRow> = withContext(Dispatchers.Default) {
        val board = prepareBoardImage(photo, template)

        var rows = BoardDetector().detectRows(board, template)
        if (rows.isEmpty()) {
            // Calibration produced no usable row spacing for this crop - fall
            // back to treating the whole board-area crop as one row, rather
            // than silently producing nothing for this photo.
            rows = listOf(RowRect(index = 0, rect = PixelRect(0, 0, board.width, board.height), detected = false))
        }

        val extractor = RegionExtractor(template)
        val results = mutableListOf<ReviewRow>()

        for (row in rows) {
            val regions = extractor.extractRow(board, row)
            val byField = mutableMapOf<FieldKey, FieldResult>()

            for (region in regions) {
                byField[region.field] = recognizeField(region, confidenceThreshold)
            }

            val projectNumber = byField.getValue(FieldKey.PROJECT_NUMBER)
            val customer = byField.getValue(FieldKey.CUSTOMER)
            val daysRemaining = byField.getValue(FieldKey.DAYS_REMAINING)

            // A row with nothing recognized in either identifying field is almost
            // certainly blank board space below the last real row - skip it
            // rather than adding an empty candidate to review.
            if (projectNumber.value.isBlank() && customer.value.isBlank()) continue

            val needsReview = !OCRValidator.isOk(projectNumber, confidenceThreshold) ||
                !OCRValidator.isOk(customer, confidenceThreshold) ||
                !OCRValidator.isOk(daysRemaining, confidenceThreshold)

            results += ReviewRow(
                id = "p$photoIndex-r${row.index}-${System.nanoTime()}",
                sourcePhotoIndex = photoIndex,
                rowIndex = row.index,
                projectNumber = projectNumber,
                customer = customer,
                daysRemaining = daysRemaining,
                included = !needsReview,
                needsReview = needsReview,
                rowThumbnail = cropToJpegBytes(board, row.rect),
            )
        }

        results
    }

    /**
     * Runs OCR on a field crop, preferring the Otsu-binarized version and
     * falling back to adaptive thresholding only if Otsu's result doesn't
     * pass validation - adaptive compensates for uneven lighting (e.g.
     * glare on one side of a card) but is noisier on a clean, evenly-lit
     * crop, so it isn't tried first.
     */
    private suspend fun recognizeField(region: ExtractedRegion, confidenceThreshold: Float): FieldResult {
        val whitelist = whitelistFor(region.field)

        val otsuResult = ocr.recognize(OcrPreprocessor.otsu(region.bitmap), whitelist)
        val otsuField = validate(region.field, otsuResult.text, otsuResult.confidence, "otsu")
        if (OCRValidator.isOk(otsuField, confidenceThreshold)) return otsuField

        val adaptiveResult = ocr.recognize(OcrPreprocessor.adaptive(region.bitmap), whitelist)
        val adaptiveField = validate(region.field, adaptiveResult.text, adaptiveResult.confidence, "adaptive")
        return if (adaptiveField.confidence > otsuField.confidence) adaptiveField else otsuField
    }

    private fun validate(field: FieldKey, text: String, confidence: Float, variant: String): FieldResult = when (field) {
        FieldKey.PROJECT_NUMBER -> OCRValidator.projectNumber(text, confidence, variant)
        FieldKey.CUSTOMER -> OCRValidator.customer(text, confidence, variant)
        FieldKey.DAYS_REMAINING -> OCRValidator.daysRemaining(text, confidence, variant)
    }

    /** EXIF + manual rotation, crop to the calibrated board area, then best-effort perspective correction. */
    private fun prepareBoardImage(photo: SelectedPhoto, template: BoardTemplate): Bitmap {
        val upright = ImageLoader.loadUpright(photo.file)
        val rotated = if (photo.rotationDegrees != 0) ImageLoader.rotate(upright, photo.rotationDegrees) else upright

        val area = template.boardArea
        val x = (area.xPct * rotated.width).toInt().coerceIn(0, rotated.width - 1)
        val y = (area.yPct * rotated.height).toInt().coerceIn(0, rotated.height - 1)
        val w = (area.wPct * rotated.width).toInt().coerceAtMost(rotated.width - x).coerceAtLeast(1)
        val h = (area.hPct * rotated.height).toInt().coerceAtMost(rotated.height - y).coerceAtLeast(1)
        val boardCrop = Bitmap.createBitmap(rotated, x, y, w, h)

        val corrector = com.productionboard.scanner.vision.PerspectiveCorrector()
        val detection = corrector.detectBoardQuad(boardCrop)
        val quad = detection.quad
        return if (quad != null && detection.areaRatio > 0.5) {
            corrector.warpToRectangle(boardCrop, quad, boardCrop.width, boardCrop.height)
        } else {
            boardCrop
        }
    }

    private fun cropToJpegBytes(board: Bitmap, rect: PixelRect): ByteArray {
        val x = rect.x.coerceIn(0, (board.width - 1).coerceAtLeast(0))
        val y = rect.y.coerceIn(0, (board.height - 1).coerceAtLeast(0))
        val w = rect.width.coerceAtMost(board.width - x).coerceAtLeast(1)
        val h = rect.height.coerceAtMost(board.height - y).coerceAtLeast(1)
        val crop = Bitmap.createBitmap(board, x, y, w, h)
        val out = ByteArrayOutputStream()
        crop.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return out.toByteArray()
    }
}
