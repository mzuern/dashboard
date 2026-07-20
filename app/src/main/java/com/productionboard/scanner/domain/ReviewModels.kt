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
    /** Which preprocessing variant produced this result, or "manual" for a user-typed value. */
    val preprocessingVariant: String = "default",
)

/**
 * One candidate project row extracted from a photo (or entered by hand).
 * [included] is the user's approval to put this row in the generated
 * email - unchecked rows (e.g. a confirmed duplicate) are simply left
 * out, never silently dropped from the review list itself.
 */
@Serializable
data class ReviewRow(
    val id: String,
    /** Which photo (by index in the selected-photos list) this row came from; -1 for manually-added rows. */
    val sourcePhotoIndex: Int = -1,
    val rowIndex: Int,
    val projectNumber: FieldResult,
    val customer: FieldResult,
    val daysRemaining: FieldResult,
    val included: Boolean = true,
    val needsReview: Boolean = true,
    val possibleDuplicate: Boolean = false,
    /** JPEG bytes of the row crop from the source photo, for the review list's source thumbnail. */
    val rowThumbnail: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReviewRow) return false
        return id == other.id && sourcePhotoIndex == other.sourcePhotoIndex && rowIndex == other.rowIndex &&
            projectNumber == other.projectNumber && customer == other.customer && daysRemaining == other.daysRemaining &&
            included == other.included && needsReview == other.needsReview && possibleDuplicate == other.possibleDuplicate
    }

    override fun hashCode(): Int = id.hashCode()
}
