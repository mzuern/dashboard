package com.productionboard.scanner.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OCRValidatorTest {

    @Test
    fun `project number strips non-digits and stays valid`() {
        val result = OCRValidator.projectNumber("6 6 8 2 5\n", 60f)
        assertEquals("66825", result.value)
        assertTrue(result.formatValid)
    }

    @Test
    fun `project number too short is flagged but never dropped`() {
        val result = OCRValidator.projectNumber("##", 40f)
        assertEquals("##", result.value) // raw text kept, never guessed
        assertFalse(result.formatValid)
    }

    @Test
    fun `customer collapses whitespace and trims`() {
        val result = OCRValidator.customer("  ABC   Manufacturing\n", 88f)
        assertEquals("ABC Manufacturing", result.value)
        assertTrue(result.formatValid)
    }

    @Test
    fun `customer blank is invalid`() {
        val result = OCRValidator.customer("   ", 88f)
        assertFalse(result.formatValid)
    }

    @Test
    fun `days remaining accepts plain integer`() {
        val result = OCRValidator.daysRemaining("5", 92f)
        assertEquals("5", result.value)
        assertTrue(result.formatValid)
    }

    @Test
    fun `days remaining normalizes Complete and Done variants`() {
        assertEquals("Complete", OCRValidator.daysRemaining("complete", 70f).value)
        assertEquals("Complete", OCRValidator.daysRemaining("Complete.", 70f).value)
        assertEquals("Complete", OCRValidator.daysRemaining("done", 70f).value)
    }

    @Test
    fun `days remaining never invents a value for garbage text`() {
        val result = OCRValidator.daysRemaining("xyz", 30f)
        assertEquals("xyz", result.value)
        assertFalse(result.formatValid)
    }

    @Test
    fun `isOk requires both format validity and confidence threshold`() {
        val lowConfidence = OCRValidator.projectNumber("66825", 40f)
        assertFalse(OCRValidator.isOk(lowConfidence, 65f))

        val highConfidence = OCRValidator.projectNumber("66825", 90f)
        assertTrue(OCRValidator.isOk(highConfidence, 65f))
    }
}
