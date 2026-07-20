package com.productionboard.scanner.report

import com.productionboard.scanner.domain.FieldResult
import com.productionboard.scanner.domain.ReviewRow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class EmailGeneratorTest {

    private fun field(value: String) = FieldResult(rawText = value, value = value, confidence = 100f, formatValid = true)

    private fun row(index: Int, project: String, customer: String, days: String, included: Boolean = true) = ReviewRow(
        id = "row-$index",
        sourcePhotoIndex = 0,
        rowIndex = index,
        projectNumber = field(project),
        customer = field(customer),
        daysRemaining = field(days),
        included = included,
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

        val email = EmailGenerator.generate(rows, date)

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

        assertEquals(expectedBody, email.body)
        assertEquals("Daily Production Status - July 19, 2026", email.subject)
    }

    @Test
    fun `rows left unchecked (not included) are left out of the email`() {
        val rows = listOf(
            row(0, "66825", "ABC Manufacturing", "5", included = true),
            row(1, "66201", "XYZ Electric", "2", included = false),
        )
        val email = EmailGenerator.generate(rows)
        assertEquals(false, email.body.contains("66201"))
        assertEquals(true, email.body.contains("66825"))
    }

    @Test
    fun `empty row list still produces a well-formed email`() {
        val email = EmailGenerator.generate(emptyList())
        assertEquals("", email.body.split("Project Updates\n\n")[1].split("\n\nEnd of Report")[0])
    }
}
