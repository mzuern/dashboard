package com.productionboard.scanner.ocr

import android.graphics.Bitmap

data class OCRResult(val text: String, val confidence: Float)

/**
 * Abstraction over the local OCR engine, so the Tesseract-based
 * implementation ([TesseractOCRProvider]) can be swapped for a different
 * fully-offline engine later without touching RegionExtractor/
 * ReviewScreen/OCRValidator. No implementation may call a network/cloud
 * OCR service.
 */
interface OCRProvider {
    suspend fun initialize()
    suspend fun recognize(bitmap: Bitmap, whitelist: String? = null): OCRResult
    fun close()
}
