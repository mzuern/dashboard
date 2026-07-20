package com.productionboard.scanner.scanning

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class CoverageCell(val gx: Int, val gy: Int, var observations: Int, var covered: Boolean)

data class CoverageSnapshot(
    val cells: List<CoverageCell>,
    val minGx: Int,
    val maxGx: Int,
    val minGy: Int,
    val maxGy: Int,
    val cursorGx: Int,
    val cursorGy: Int,
    val coverageFraction: Float,
)

private const val CELL_SIZE = 80.0 // downsampled-frame px per cell (see MotionTracker.SAMPLE_WIDTH)
private const val FOOTPRINT_RADIUS_CELLS = 2
private const val REQUIRED_OBSERVATIONS = 2

/**
 * Builds a sparse coverage grid from accumulated camera motion. There's
 * no true camera pose/SLAM here - frame-to-frame shifts from
 * [MotionTracker] are integrated into a running camera-center
 * trajectory, and a footprint around that center is marked "observed" on
 * every good-quality tick. A cell only turns green once it's been seen
 * by at least two overlapping frames, mirroring the overlap the stitcher
 * itself needs.
 */
class CoverageTracker {
    private val cells = HashMap<Long, CoverageCell>()
    private var camX = 0.0
    private var camY = 0.0
    private var minGx = 0
    private var maxGx = 0
    private var minGy = 0
    private var maxGy = 0
    private var touched = false

    private fun key(gx: Int, gy: Int): Long = (gx.toLong() shl 32) xor (gy.toLong() and 0xffffffffL)

    fun update(sample: MotionSample, qualityOk: Boolean) {
        if (sample.hasPrevious) {
            camX += sample.dx
            camY += sample.dy
        }
        val gx = (camX / CELL_SIZE).roundToInt()
        val gy = (camY / CELL_SIZE).roundToInt()

        if (!touched) {
            minGx = gx; maxGx = gx; minGy = gy; maxGy = gy; touched = true
        } else {
            minGx = min(minGx, gx); maxGx = max(maxGx, gx)
            minGy = min(minGy, gy); maxGy = max(maxGy, gy)
        }

        if (qualityOk) {
            for (dgx in -FOOTPRINT_RADIUS_CELLS..FOOTPRINT_RADIUS_CELLS) {
                for (dgy in -FOOTPRINT_RADIUS_CELLS..FOOTPRINT_RADIUS_CELLS) {
                    val cx = gx + dgx
                    val cy = gy + dgy
                    val cell = cells.getOrPut(key(cx, cy)) { CoverageCell(cx, cy, 0, false) }
                    cell.observations += 1
                    cell.covered = cell.observations >= REQUIRED_OBSERVATIONS
                }
            }
        }
    }

    fun snapshot(): CoverageSnapshot {
        val list = cells.values.toList()
        val totalCells = max(1, (maxGx - minGx + 1) * (maxGy - minGy + 1))
        val coveredCount = list.count { it.covered }
        val cursorGx = (camX / CELL_SIZE).roundToInt()
        val cursorGy = (camY / CELL_SIZE).roundToInt()
        val fraction = if (touched) min(1f, coveredCount.toFloat() / totalCells) else 0f
        return CoverageSnapshot(list, minGx, maxGx, minGy, maxGy, cursorGx, cursorGy, fraction)
    }

    fun reset() {
        cells.clear()
        camX = 0.0
        camY = 0.0
        touched = false
    }
}
