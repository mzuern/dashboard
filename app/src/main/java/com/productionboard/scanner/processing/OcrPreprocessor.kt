package com.productionboard.scanner.processing

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Cleans up a field crop before OCR: grayscale, denoise, then binarize to
 * plain black text on white. Tesseract is far more reliable on a clean
 * binary image than on a raw color marker-on-whiteboard photo (glare,
 * faint strokes, background tint all confuse it otherwise).
 *
 * Two binarization strategies are offered because no single one wins on
 * every crop - [otsu] assumes fairly even lighting across the crop, while
 * [adaptive] compensates for a lighting gradient (e.g. glare on one side
 * of a card) at the cost of being noisier on a clean, evenly-lit crop.
 * [PhotoProcessor] tries otsu first and only falls back to adaptive when
 * the otsu-based OCR result doesn't pass validation.
 */
object OcrPreprocessor {

    fun otsu(bitmap: Bitmap): Bitmap = binarize(bitmap) { gray, out ->
        Imgproc.threshold(gray, out, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
    }

    fun adaptive(bitmap: Bitmap): Bitmap = binarize(bitmap) { gray, out ->
        val blockSize = (minOf(gray.rows(), gray.cols()) / 6).let { if (it % 2 == 0) it + 1 else it }.coerceAtLeast(3)
        Imgproc.adaptiveThreshold(
            gray, out, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, blockSize, 10.0,
        )
    }

    private inline fun binarize(bitmap: Bitmap, threshold: (gray: Mat, out: Mat) -> Unit): Bitmap {
        val src = Mat()
        val gray = Mat()
        val blurred = Mat()
        val out = Mat()
        try {
            Utils.bitmapToMat(bitmap, src)
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(3.0, 3.0), 0.0)
            threshold(blurred, out)

            val result = Bitmap.createBitmap(out.cols(), out.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(out, result)
            return result
        } finally {
            src.release(); gray.release(); blurred.release(); out.release()
        }
    }
}
