package com.productionboard.scanner.domain

import kotlinx.serialization.Serializable

/** A rectangle expressed as fractions (0f-1f) of its parent's width/height. */
@Serializable
data class FractionalRect(
    val xPct: Float,
    val yPct: Float,
    val wPct: Float,
    val hPct: Float,
)

enum class FieldKey { PROJECT_NUMBER, CUSTOMER, DAYS_REMAINING }

/**
 * The three fields extracted from every project row, plus row geometry.
 * Normalized (0-1) region coordinates, not fixed pixels, so the same
 * template works regardless of stitched-image resolution. Edited from
 * CalibrationScreen; never hard-coded per-device.
 */
@Serializable
data class BoardTemplate(
    val boardWidthPx: Int = 2400,
    val boardHeightPx: Int = 1600,
    val marginTopPx: Int = 80,
    val marginBottomPx: Int = 40,
    val rowHeightPx: Int = 90,
    /** Optional hard cap on rows; null means "derive from board/row height". */
    val rowCount: Int? = null,
    val regions: FieldRegions = FieldRegions(),
) {
    companion object {
        val DEFAULT = BoardTemplate()
    }
}

@Serializable
data class FieldRegions(
    val projectNumber: FractionalRect = FractionalRect(0.02f, 0.10f, 0.16f, 0.80f),
    val customer: FractionalRect = FractionalRect(0.20f, 0.10f, 0.55f, 0.80f),
    val daysRemaining: FractionalRect = FractionalRect(0.78f, 0.10f, 0.20f, 0.80f),
) {
    fun get(field: FieldKey): FractionalRect = when (field) {
        FieldKey.PROJECT_NUMBER -> projectNumber
        FieldKey.CUSTOMER -> customer
        FieldKey.DAYS_REMAINING -> daysRemaining
    }

    fun with(field: FieldKey, rect: FractionalRect): FieldRegions = when (field) {
        FieldKey.PROJECT_NUMBER -> copy(projectNumber = rect)
        FieldKey.CUSTOMER -> copy(customer = rect)
        FieldKey.DAYS_REMAINING -> copy(daysRemaining = rect)
    }
}

fun BoardTemplate.computeRowCount(): Int {
    rowCount?.let { return it }
    val usable = boardHeightPx - marginTopPx - marginBottomPx
    return maxOf(0, usable / rowHeightPx)
}

/** Validates a template before it's persisted; mirrors the web app's rules. */
fun BoardTemplate.validate(): List<String> {
    val errors = mutableListOf<String>()
    if (boardWidthPx < 200) errors += "Board width is too small."
    if (boardHeightPx < 200) errors += "Board height is too small."
    if (rowHeightPx < 20) errors += "Row height is too small."
    if (marginTopPx < 0) errors += "Top margin cannot be negative."
    for (field in FieldKey.entries) {
        val r = regions.get(field)
        if (r.wPct <= 0f || r.hPct <= 0f) errors += "${field.name} region must have positive width/height."
        if (r.xPct < 0f || r.xPct + r.wPct > 1f) errors += "${field.name} region extends outside the row horizontally."
        if (r.yPct < 0f || r.yPct + r.hPct > 1f) errors += "${field.name} region extends outside the row vertically."
    }
    return errors
}
