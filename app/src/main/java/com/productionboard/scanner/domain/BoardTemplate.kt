package com.productionboard.scanner.domain

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

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
 * The three fields extracted from every project row, plus row geometry -
 * all stored as fractions (0-1), never fixed pixels, so one calibration
 * (done on a single sample photo) reuses correctly across photos of
 * different resolutions *and* different framing (a photo showing only
 * part of the board still uses the same row spacing/column layout).
 * Edited from CalibrationScreen; never hard-coded per-device.
 *
 * - [boardArea]: the region of a photo that contains organized rows,
 *   as a crop rectangle within the full photo.
 * - [firstRowTopPct]/[rowHeightPct]: row geometry as fractions of the
 *   *board area's* height - "first row" here means "the first row
 *   visible near the top of whatever board-area crop a given photo
 *   produces", not a fixed global row index, since different photos may
 *   frame different sections of the board.
 * - [regions]: the three field rectangles as fractions of a single row's
 *   own box.
 */
@Serializable
data class BoardTemplate(
    val boardArea: FractionalRect = FractionalRect(0.02f, 0.02f, 0.96f, 0.96f),
    val firstRowTopPct: Float = 0.02f,
    val rowHeightPct: Float = 0.11f,
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

/** How many full rows fit within a board-area crop of this height - varies per photo, so this is a function, not a stored value. */
fun BoardTemplate.maxRowCount(boardAreaHeightPx: Int): Int {
    val rowHeightPx = rowHeightPct * boardAreaHeightPx
    if (rowHeightPx < 1f) return 0
    val usableHeightPx = boardAreaHeightPx - firstRowTopPct * boardAreaHeightPx
    return max(0, (usableHeightPx / rowHeightPx).toInt())
}

/** Validates a template before it's persisted. */
fun BoardTemplate.validate(): List<String> {
    val errors = mutableListOf<String>()
    if (boardArea.wPct <= 0f || boardArea.hPct <= 0f) errors += "Board area must have positive width/height."
    if (boardArea.xPct < 0f || boardArea.xPct + boardArea.wPct > 1f) errors += "Board area extends outside the photo horizontally."
    if (boardArea.yPct < 0f || boardArea.yPct + boardArea.hPct > 1f) errors += "Board area extends outside the photo vertically."
    if (rowHeightPct <= 0.01f) errors += "Row height is too small."
    if (firstRowTopPct < 0f || firstRowTopPct >= 1f) errors += "First row position must be within the board area."
    for (field in FieldKey.entries) {
        val r = regions.get(field)
        if (r.wPct <= 0f || r.hPct <= 0f) errors += "${field.name} region must have positive width/height."
        if (r.xPct < 0f || r.xPct + r.wPct > 1f) errors += "${field.name} region extends outside the row horizontally."
        if (r.yPct < 0f || r.yPct + r.hPct > 1f) errors += "${field.name} region extends outside the row vertically."
    }
    return errors
}

/** Clamps a rect so it never extends past its parent's 0-1 bounds, keeping its size. Used while dragging in CalibrationScreen. */
fun FractionalRect.clampedTo(maxX: Float = 1f, maxY: Float = 1f): FractionalRect {
    val x = min(max(0f, xPct), maxX - wPct)
    val y = min(max(0f, yPct), maxY - hPct)
    return copy(xPct = max(0f, x), yPct = max(0f, y))
}
