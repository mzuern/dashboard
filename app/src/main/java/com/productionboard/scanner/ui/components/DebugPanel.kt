package com.productionboard.scanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.productionboard.scanner.scanning.ScanUiState

/**
 * Surfaces internals that make calibrating a new board layout tractable:
 * selected keyframes, stitch matches/fallbacks, coverage numbers, row
 * detection results, and OCR confidence per field. Only mounted when
 * Settings > Debug Mode is on.
 */
@Composable
fun DebugPanel(state: ScanUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D0D10))
            .padding(12.dp),
    ) {
        Text("DEBUG", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        DebugLine("Phase", state.phase.name)
        DebugLine("Frames captured", state.frameCount.toString())
        DebugLine("Coverage", "${(state.coverageFraction * 100).toInt()}%")
        state.quadAreaRatio?.let { DebugLine("Board quad area ratio", "%.2f".format(it)) }
        state.rows?.let { rows -> DebugLine("Rows detected", "${rows.size} (${rows.count { it.detected }} from grid lines)") }

        state.stitchDebug?.let { debug ->
            Text("Stitching", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                items(debug) { d ->
                    DebugLine("#${d.frameIndex}", "matched=${d.matchedFeatures} fallback=${d.usedFallback}")
                }
            }
        }

        state.reviewRows?.let { rows ->
            Text("OCR confidence", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            rows.forEach { row ->
                DebugLine(
                    "Row ${row.rowIndex}",
                    "proj=${row.projectNumber.confidence.toInt()} cust=${row.customer.confidence.toInt()} days=${row.daysRemaining.confidence.toInt()}",
                )
            }
        }
    }
}

@Composable
private fun DebugLine(label: String, value: String) {
    Text("$label: $value", color = Color(0xFF7FD67A), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
}
