package com.productionboard.scanner.ocr

import com.productionboard.scanner.domain.FieldKey
import com.productionboard.scanner.domain.FieldResult

private const val DIGITS_WHITELIST = "0123456789"
private const val CUSTOMER_WHITELIST = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 &.,'/-"
private const val DAYS_WHITELIST = "0123456789CcompleteDdoneUnknw" // digits, or "Complete"/"Done"/"Unknown"

fun whitelistFor(field: FieldKey): String = when (field) {
    FieldKey.PROJECT_NUMBER -> DIGITS_WHITELIST
    FieldKey.CUSTOMER -> CUSTOMER_WHITELIST
    FieldKey.DAYS_REMAINING -> DAYS_WHITELIST
}

/**
 * Turns raw OCR output into a [FieldResult]. Never guesses or silently
 * drops a value: if the cleaned text doesn't match the expected format,
 * the raw OCR text is kept as-is with formatValid = false, so
 * ReviewScreen flags the row for a human to correct.
 */
object OCRValidator {

    fun projectNumber(rawText: String, confidence: Float, variant: String = "default"): FieldResult {
        val cleaned = rawText.filter { it.isDigit() }
        val formatValid = cleaned.length >= 3
        return FieldResult(rawText, cleaned.ifEmpty { rawText.trim() }, confidence, formatValid, variant)
    }

    fun customer(rawText: String, confidence: Float, variant: String = "default"): FieldResult {
        val cleaned = rawText.trim().replace(Regex("\\s+"), " ")
        return FieldResult(rawText, cleaned, confidence, cleaned.isNotEmpty(), variant)
    }

    /** Normalizes to an integer-days string, "Complete", or leaves the raw text with formatValid = false. Never invents a value. */
    fun daysRemaining(rawText: String, confidence: Float, variant: String = "default"): FieldResult {
        val trimmed = rawText.trim()
        val lower = trimmed.lowercase()

        if (Regex("^(complete|done)\\.?$").matches(lower)) {
            return FieldResult(rawText, "Complete", confidence, true, variant)
        }
        if (lower == "unknown" || lower == "u" || trimmed.isEmpty()) {
            return FieldResult(rawText, "Unknown", confidence, false, variant)
        }
        val digitsOnly = trimmed.filter { it.isDigit() }
        if (digitsOnly.isNotEmpty() && digitsOnly == trimmed.filterNot { it.isWhitespace() }) {
            return FieldResult(rawText, digitsOnly, confidence, true, variant)
        }
        return FieldResult(rawText, trimmed, confidence, false, variant)
    }

    fun isOk(field: FieldResult, confidenceThreshold: Float): Boolean =
        field.formatValid && field.confidence >= confidenceThreshold
}
