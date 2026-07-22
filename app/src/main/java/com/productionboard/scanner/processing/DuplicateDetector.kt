package com.productionboard.scanner.processing

import com.productionboard.scanner.domain.ReviewRow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Flags rows that look like the same project seen in more than one
 * photo (overlapping shots of the board naturally produce this). Never
 * removes anything - duplicates are only marked, and the user decides
 * during review which copy (if any) to keep by unchecking Include.
 */
object DuplicateDetector {

    fun markPossibleDuplicates(rows: List<ReviewRow>): List<ReviewRow> {
        val flagged = BooleanArray(rows.size)
        for (i in rows.indices) {
            for (j in i + 1 until rows.size) {
                if (rows[i].sourcePhotoIndex == rows[j].sourcePhotoIndex && rows[i].sourcePhotoIndex >= 0) continue // same photo, same row was already deduped by BoardDetector
                if (looksLikeSameProject(rows[i], rows[j])) {
                    flagged[i] = true
                    flagged[j] = true
                }
            }
        }
        return rows.mapIndexed { i, row -> if (flagged[i]) row.copy(possibleDuplicate = true) else row }
    }

    private fun looksLikeSameProject(a: ReviewRow, b: ReviewRow): Boolean {
        val projectA = a.projectNumber.value.trim()
        val projectB = b.projectNumber.value.trim()
        if (projectA.isNotEmpty() && projectA == projectB) return true

        return similarCustomer(a.customer.value, b.customer.value) && daysAreClose(a.daysRemaining.value, b.daysRemaining.value)
    }

    private fun similarCustomer(x: String, y: String): Boolean {
        val nx = normalize(x)
        val ny = normalize(y)
        if (nx.isEmpty() || ny.isEmpty()) return false
        if (nx == ny) return true
        val maxLen = max(nx.length, ny.length)
        return levenshtein(nx, ny) <= max(1, maxLen / 4) // allow roughly 25% character difference (OCR noise)
    }

    private fun daysAreClose(x: String, y: String): Boolean {
        if (x.isBlank() || y.isBlank()) return false
        if (x.equals(y, ignoreCase = true)) return true
        val nx = x.toIntOrNull()
        val ny = y.toIntOrNull()
        return nx != null && ny != null && abs(nx - ny) <= 2
    }

    private fun normalize(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = min(min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost)
            }
        }
        return dp[a.length][b.length]
    }
}
