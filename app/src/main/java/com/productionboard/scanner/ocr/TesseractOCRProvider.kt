package com.productionboard.scanner.ocr

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local OCR via tess-two (a JNI wrapper around the native Tesseract
 * library). Trained data is bundled in assets/tessdata and extracted to
 * app-private storage on first use ([TessDataInstaller]) - no CDN, no
 * network call, ever. See [OCRProvider] for the swappable interface.
 */
class TesseractOCRProvider(private val context: Context) : OCRProvider {

    private var api: TessBaseAPI? = null

    override suspend fun initialize() {
        if (api != null) return
        withContext(Dispatchers.IO) {
            val dataRoot = TessDataInstaller.ensureInstalled(context)
            val tess = TessBaseAPI()
            val ok = tess.init(dataRoot.absolutePath, "eng")
            check(ok) { "Failed to initialize Tesseract - trained data may be missing or corrupt." }
            // PSM_SINGLE_LINE was tried first but a Python/pytesseract prototype of this
            // exact pipeline against real handwritten board photos showed it frequently
            // returns nothing at all on bold marker digits; PSM_SINGLE_BLOCK reliably
            // recognized the same crops (e.g. a correct "66455" at 90% confidence).
            tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
            api = tess
        }
    }

    override suspend fun recognize(bitmap: Bitmap, whitelist: String?): OCRResult = withContext(Dispatchers.Default) {
        val tess = api ?: error("TesseractOCRProvider.initialize() was not called.")
        synchronized(tess) {
            tess.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, whitelist ?: "")
            tess.setImage(bitmap)
            val text = tess.utF8Text ?: ""
            val confidence = tess.meanConfidence().toFloat()
            tess.clear()
            OCRResult(text.trim(), confidence)
        }
    }

    override fun close() {
        api?.end()
        api = null
    }
}
