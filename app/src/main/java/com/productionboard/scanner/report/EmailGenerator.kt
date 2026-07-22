package com.productionboard.scanner.report

import com.productionboard.scanner.domain.ReviewRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GeneratedEmail(val subject: String, val body: String)

/**
 * Builds the daily status email subject/body from the rows the user has
 * approved (checked Include). Never sends anything - EmailScreen only
 * offers copy-to-clipboard and Android's share sheet (ACTION_SEND),
 * which hands the text to whatever app the user picks (Gmail, Outlook,
 * Teams, ...).
 */
object EmailGenerator {

    fun generate(rows: List<ReviewRow>, date: Date = Date()): GeneratedEmail {
        val included = rows.filter { it.included }
        val dateStr = SimpleDateFormat("MMMM d, yyyy", Locale.US).format(date)
        val subject = "Daily Production Status - $dateStr"

        val projectBlocks = included.map { row ->
            listOf(
                "Project ${row.projectNumber.value}",
                "Customer: ${row.customer.value}",
                "Estimated Days Remaining: ${row.daysRemaining.value}",
            ).joinToString("\n")
        }

        val body = listOf(
            "Daily Production Status",
            "",
            "Project Updates",
            "",
            projectBlocks.joinToString("\n\n"),
            "",
            "End of Report",
        ).joinToString("\n")

        return GeneratedEmail(subject, body)
    }
}
