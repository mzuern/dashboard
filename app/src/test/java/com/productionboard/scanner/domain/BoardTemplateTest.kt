package com.productionboard.scanner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardTemplateTest {

    @Test
    fun `computeRowCount derives from board and row height when rowCount is unset`() {
        val template = BoardTemplate(boardHeightPx = 1600, marginTopPx = 80, marginBottomPx = 40, rowHeightPx = 90)
        // (1600 - 80 - 40) / 90 = 16.7 -> 16
        assertEquals(16, template.computeRowCount())
    }

    @Test
    fun `computeRowCount respects an explicit override`() {
        val template = BoardTemplate(rowCount = 5)
        assertEquals(5, template.computeRowCount())
    }

    @Test
    fun `default template is valid`() {
        assertTrue(BoardTemplate.DEFAULT.validate().isEmpty())
    }

    @Test
    fun `region extending past 100 percent is flagged`() {
        val template = BoardTemplate.DEFAULT.copy(
            regions = FieldRegions(projectNumber = FractionalRect(0.9f, 0f, 0.5f, 1f)),
        )
        val errors = template.validate()
        assertTrue(errors.any { it.contains("PROJECT_NUMBER") })
    }

    @Test
    fun `zero-size region is flagged`() {
        val template = BoardTemplate.DEFAULT.copy(
            regions = FieldRegions(customer = FractionalRect(0.2f, 0.1f, 0f, 0.8f)),
        )
        val errors = template.validate()
        assertTrue(errors.any { it.contains("CUSTOMER") })
    }

    @Test
    fun `too-small row height is flagged`() {
        val template = BoardTemplate.DEFAULT.copy(rowHeightPx = 5)
        assertTrue(template.validate().any { it.contains("Row height") })
    }
}
