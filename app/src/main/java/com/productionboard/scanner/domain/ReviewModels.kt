package com.productionboard.scanner.domain

import kotlinx.serialization.Serializable

@Serializable
data class PixelRect(val x: Int, val y: Int, val width: Int, val height: Int)

@Serializable
data class RowRect(val index: Int, val rect: PixelRect, val detected: Boolean)

/** OCR result for a single field, never silently discarded even when invalid. */
@Serializable
data class FieldResult(
    val rawText: String,
    val value: String,
    val confidence: Float,
    val formatValid: Boolean,
    /** Which preprocessing variant produced this result, for debug mode. */
    val preprocessingVariant: String = "default",
)

@Serializable
data class ReviewRow(
    val id: String,
    val rowIndex: Int,
    val projectNumber: FieldResult,
    val customer: FieldResult,
    val daysRemaining: FieldResult,
    val verified: Boolean = false,
    val needsReview: Boolean = true,
    /** Base64/JPEG thumbnail of the row crop from the stitched board, for the review list. */
    val rowThumbnail: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReviewRow) return false
        return id == other.id && rowIndex == other.rowIndex && projectNumber == other.projectNumber &&
            customer == other.customer && daysRemaining == other.daysRemaining &&
            verified == other.verified && needsReview == other.needsReview
    }

    override fun hashCode(): Int = id.hashCode()
}

enum class ScanIssueCode {
    MOTION_TOO_FAST,
    BLUR,
    LOW_LIGHT,
    GLARE,
    TRACKING_LOST,
    INSUFFICIENT_OVERLAP,
    INCOMPLETE_SCAN,
}

data class ScanIssue(val code: ScanIssueCode, val message: String, val recoverable: Boolean = true)

enum class ScanPhase {
    IDLE,
    SCANNING,
    CONFIRM_INCOMPLETE,
    STITCHING,
    ADJUST_CORNERS,
    CORRECTING,
    DETECTING,
    OCR,
    REVIEW,
}
