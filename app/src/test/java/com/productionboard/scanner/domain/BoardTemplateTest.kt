package com.productionboard.scanner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardTemplateTest {

    @Test
    fun `maxRowCount scales with the crop's actual height, so the same template works at any photo resolution`() {
        val template = BoardTemplate(firstRowTopPct = 0.05f, rowHeightPct = 0.10f)
        // (1000 - 50) / 100 = 9.5 -> 9 full rows fit in a 1000px-tall crop
        assertEquals(9, template.maxRowCount(1000))
        // Same row count at half the resolution - row height is a fraction of the crop, not a fixed pixel value.
        assertEquals(9, template.maxRowCount(500))
    }

    @Test
    fun `maxRowCount is smaller for a photo framed to show less of the board vertically`() {
        // A row here occupies twice the fraction of the board area's height (photo zoomed in / framed tighter).
        val zoomedIn = BoardTemplate(firstRowTopPct = 0.05f, rowHeightPct = 0.20f)
        assertEquals(4, zoomedIn.maxRowCount(1000))
    }

    @Test
    fun `default template is valid`() {
        assertTrue(BoardTemplate.DEFAULT.validate().isEmpty())
    }

    @Test
    fun `board area extending past the photo is flagged`() {
        val template = BoardTemplate.DEFAULT.copy(boardArea = FractionalRect(0.5f, 0f, 0.8f, 1f))
        assertTrue(template.validate().any { it.contains("Board area") })
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
        val template = BoardTemplate.DEFAULT.copy(rowHeightPct = 0.001f)
        assertTrue(template.validate().any { it.contains("Row height") })
    }

    @Test
    fun `clampedTo keeps a rect within 0-1 bounds while preserving its size`() {
        val rect = FractionalRect(xPct = 0.95f, yPct = 0.95f, wPct = 0.2f, hPct = 0.2f)
        val clamped = rect.clampedTo()
        assertTrue(clamped.xPct + clamped.wPct <= 1.001f)
        assertTrue(clamped.yPct + clamped.hPct <= 1.001f)
        assertEquals(0.2f, clamped.wPct)
        assertEquals(0.2f, clamped.hPct)
    }
}
