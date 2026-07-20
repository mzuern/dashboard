package com.productionboard.scanner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.productionboard.scanner.scanning.CoverageSnapshot
import com.productionboard.scanner.ui.theme.Danger
import com.productionboard.scanner.ui.theme.Success
import com.productionboard.scanner.ui.theme.Warning

/**
 * Renders the coverage heatmap (green = sufficiently overlapped, red =
 * still needed) over the live camera preview, from [CoverageTracker]'s
 * sparse grid directly - no separate rendering model to keep in sync.
 */
@Composable
fun CoverageOverlay(snapshot: CoverageSnapshot?, trackingLost: Boolean, modifier: Modifier = Modifier) {
    if (snapshot == null || snapshot.cells.isEmpty()) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val padding = 2
        val minGx = snapshot.minGx - padding
        val maxGx = snapshot.maxGx + padding
        val minGy = snapshot.minGy - padding
        val maxGy = snapshot.maxGy + padding
        val gridW = (maxGx - minGx + 1).coerceAtLeast(1)
        val gridH = (maxGy - minGy + 1).coerceAtLeast(1)

        val cellW = size.width / gridW
        val cellH = size.height / gridH

        fun cellOffset(gx: Int, gy: Int) = Offset((gx - minGx) * cellW, (gy - minGy) * cellH)

        if (trackingLost) {
            val topLeft = cellOffset(snapshot.minGx, snapshot.minGy)
            val bottomRight = cellOffset(snapshot.maxGx + 1, snapshot.maxGy + 1)
            drawRect(
                color = Warning,
                topLeft = topLeft,
                size = Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y),
                style = Stroke(width = 6f),
            )
        }

        for (cell in snapshot.cells) {
            val color = if (cell.covered) Success.copy(alpha = 0.32f) else Danger.copy(alpha = 0.18f)
            drawRect(color = color, topLeft = cellOffset(cell.gx, cell.gy), size = Size(cellW, cellH))
        }

        val cursor = cellOffset(snapshot.cursorGx, snapshot.cursorGy) + Offset(cellW / 2, cellH / 2)
        drawCircle(color = Color.White, radius = minOf(cellW, cellH) * 0.3f, center = cursor)
        drawCircle(color = Color.Black, radius = minOf(cellW, cellH) * 0.3f, center = cursor, style = Stroke(width = 3f))
    }
}
