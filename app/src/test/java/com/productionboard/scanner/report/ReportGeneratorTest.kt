package com.productionboard.scanner.report

import com.productionboard.scanner.domain.FieldResult
import com.productionboard.scanner.domain.ReviewRow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class ReportGeneratorTest {

    private fun field(value: String) = FieldResult(rawText = value, value = value, confidence = 100f, formatValid = true)

    private fun row(index: Int, project: String, customer: String, days: String) = ReviewRow(
        id = "row-$index",
        rowIndex = index,
        projectNumber = field(project),
        customer = field(customer),
        daysRemaining = field(days),
        verified = true,
        needsReview = false,
    )

    @Test
    fun `body matches the exact spec format`() {
        val rows = listOf(
            row(0, "66825", "ABC Manufacturing", "5"),
            row(1, "66201", "XYZ Electric", "2"),
            row(2, "66490", "Acme Industries", "Complete"),
        )
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2026-07-19")!!

        val report = ReportGenerator.generate(rows, date)

        val expectedBody = """
            Daily Production Status

            Project Updates

            Project 66825
            Customer: ABC Manufacturing
            Estimated Days Remaining: 5

            Project 66201
            Customer: XYZ Electric
            Estimated Days Remaining: 2

            Project 66490
            Customer: Acme Industries
            Estimated Days Remaining: Complete

            End of Report
        """.trimIndent()

        assertEquals(expectedBody, report.body)
        assertEquals("Daily Production Status - July 19, 2026", report.subject)
    }

    @Test
    fun `empty row list still produces a well-formed report`() {
        val report = ReportGenerator.generate(emptyList())
        assertEquals("", report.body.split("Project Updates\n\n")[1].split("\n\nEnd of Report")[0])
    }
}
