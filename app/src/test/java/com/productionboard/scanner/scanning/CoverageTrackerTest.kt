package com.productionboard.scanner.scanning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverageTrackerTest {

    private fun sample(dx: Double, dy: Double, hasPrevious: Boolean) = MotionSample(
        dx = dx,
        dy = dy,
        speed = kotlin.math.hypot(dx, dy),
        sharpness = 100.0,
        trackingConfidence = 0.9,
        brightness = 150.0,
        glareRatio = 0.0,
        hasPrevious = hasPrevious,
    )

    @Test
    fun `fresh tracker reports zero coverage`() {
        val tracker = CoverageTracker()
        assertEquals(0f, tracker.snapshot().coverageFraction)
    }

    @Test
    fun `revisiting the same spot twice marks it covered`() {
        val tracker = CoverageTracker()
        tracker.update(sample(0.0, 0.0, hasPrevious = false), qualityOk = true)
        tracker.update(sample(0.0, 0.0, hasPrevious = true), qualityOk = true)

        val snapshot = tracker.snapshot()
        assertTrue(snapshot.cells.any { it.covered })
    }

    @Test
    fun `a single low-quality observation never turns a cell green`() {
        val tracker = CoverageTracker()
        tracker.update(sample(0.0, 0.0, hasPrevious = false), qualityOk = true)

        val snapshot = tracker.snapshot()
        assertTrue(snapshot.cells.none { it.covered })
    }

    @Test
    fun `poor-quality ticks do not contribute observations`() {
        val tracker = CoverageTracker()
        tracker.update(sample(0.0, 0.0, hasPrevious = false), qualityOk = false)
        tracker.update(sample(0.0, 0.0, hasPrevious = true), qualityOk = false)

        assertEquals(0, tracker.snapshot().cells.size)
    }

    @Test
    fun `reset clears all accumulated coverage`() {
        val tracker = CoverageTracker()
        tracker.update(sample(0.0, 0.0, hasPrevious = false), qualityOk = true)
        tracker.update(sample(0.0, 0.0, hasPrevious = true), qualityOk = true)
        tracker.reset()

        val snapshot = tracker.snapshot()
        assertEquals(0f, snapshot.coverageFraction)
        assertTrue(snapshot.cells.isEmpty())
    }
}
