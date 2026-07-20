package com.productionboard.scanner.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.productionboard.scanner.domain.BoardTemplate
import com.productionboard.scanner.domain.FieldKey
import com.productionboard.scanner.domain.FractionalRect
import com.productionboard.scanner.domain.validate

private val FieldColors = mapOf(
    FieldKey.PROJECT_NUMBER to Color(0xFF646CFF),
    FieldKey.CUSTOMER to Color(0xFF2ECC71),
    FieldKey.DAYS_REMAINING to Color(0xFFF5A623),
)

private val FieldLabels = mapOf(
    FieldKey.PROJECT_NUMBER to "Project Number",
    FieldKey.CUSTOMER to "Customer",
    FieldKey.DAYS_REMAINING to "Estimated Days Remaining",
)

/**
 * Visual calibration for a new board layout: drag each field's box into
 * place on a representative row, adjust its size numerically, and tune
 * row/board geometry - all normalized 0-1 coordinates, so this works for
 * any board resolution. There's no code-level per-device layout; this
 * screen is the entire calibration story.
 */
@Composable
fun CalibrationScreen(template: BoardTemplate, onSave: (BoardTemplate) -> Unit, onBack: () -> Unit) {
    var draft by remember(template) { mutableStateOf(template) }
    val errors = draft.validate()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Calibration", style = MaterialTheme.typography.titleLarge)
            Text(
                "Drag each colored box to line it up with where that field sits within one project row. This rectangle represents one row at the board's aspect ratio.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        item {
            RowPreview(
                template = draft,
                onRegionDragged = { field, rect -> draft = draft.copy(regions = draft.regions.with(field, rect)) },
            )
        }

        item {
            Text("Board geometry", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            LabeledNumberField("Board Width (px)", draft.boardWidthPx.toFloat()) { draft = draft.copy(boardWidthPx = it.toInt()) }
            LabeledNumberField("Board Height (px)", draft.boardHeightPx.toFloat()) { draft = draft.copy(boardHeightPx = it.toInt()) }
            LabeledNumberField("Row Height (px)", draft.rowHeightPx.toFloat()) { draft = draft.copy(rowHeightPx = it.toInt()) }
            LabeledNumberField("Top Margin (px)", draft.marginTopPx.toFloat()) { draft = draft.copy(marginTopPx = it.toInt()) }
        }

        item {
            Text("Field sizes (% of row)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            FieldKey.entries.forEach { field ->
                val r = draft.regions.get(field)
                Text(FieldLabels.getValue(field), modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledNumberField("Width%", r.wPct * 100, Modifier.weight(1f)) { draft = draft.copy(regions = draft.regions.with(field, r.copy(wPct = it / 100f))) }
                    LabeledNumberField("Height%", r.hPct * 100, Modifier.weight(1f)) { draft = draft.copy(regions = draft.regions.with(field, r.copy(hPct = it / 100f))) }
                }
            }
        }

        if (errors.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    errors.forEach { Text("- $it", color = MaterialTheme.colorScheme.error) }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBack) { Text("Cancel") }
                Button(onClick = { onSave(draft) }, enabled = errors.isEmpty()) { Text("Save Template") }
            }
        }
    }
}

@Composable
private fun RowPreview(template: BoardTemplate, onRegionDragged: (FieldKey, FractionalRect) -> Unit) {
    val aspect = (template.boardWidthPx.toFloat() / template.rowHeightPx.toFloat()).coerceIn(1f, 12f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .padding(vertical = 8.dp),
    ) {
        val density = LocalDensity.current
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = Color(0xFF2C2C32))
            for (field in FieldKey.entries) {
                val r = template.regions.get(field)
                drawRect(
                    color = FieldColors.getValue(field).copy(alpha = 0.35f),
                    topLeft = Offset(r.xPct * size.width, r.yPct * size.height),
                    size = Size(r.wPct * size.width, r.hPct * size.height),
                )
            }
        }

        // Each region only owns pointer input over its own rectangle, so
        // overlapping/adjacent boxes don't steal each other's drags.
        for (field in FieldKey.entries) {
            val r = template.regions.get(field)
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset((r.xPct * containerWidthPx).toInt(), (r.yPct * containerHeightPx).toInt())
                    }
                    .size(
                        with(density) { (r.wPct * containerWidthPx).toDp() },
                        with(density) { (r.hPct * containerHeightPx).toDp() },
                    )
                    .pointerInput(field, containerWidthPx, containerHeightPx) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val current = template.regions.get(field)
                            val next = current.copy(
                                xPct = (current.xPct + dragAmount.x / containerWidthPx).coerceIn(0f, 1f - current.wPct),
                                yPct = (current.yPct + dragAmount.y / containerHeightPx).coerceIn(0f, 1f - current.hPct),
                            )
                            onRegionDragged(field, next)
                        }
                    },
            )
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
        FieldKey.entries.forEach { field ->
            Text("● ${FieldLabels.getValue(field)}", color = FieldColors.getValue(field), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun LabeledNumberField(label: String, value: Float, modifier: Modifier = Modifier, onChange: (Float) -> Unit) {
    var text by remember(value) { mutableStateOf(if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            input.toFloatOrNull()?.let(onChange)
        },
        label = { Text(label) },
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
        singleLine = true,
    )
}
