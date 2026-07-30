package com.productionboard.scanner.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.productionboard.scanner.domain.BoardTemplate
import com.productionboard.scanner.domain.FieldKey
import com.productionboard.scanner.domain.FieldRegions
import com.productionboard.scanner.domain.FractionalRect
import com.productionboard.scanner.domain.clampedTo
import com.productionboard.scanner.domain.validate
import com.productionboard.scanner.photo.PhotoStorage
import com.productionboard.scanner.processing.ImageLoader
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

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

private val FieldShortLabels = mapOf(
    FieldKey.PROJECT_NUMBER to "Proj #",
    FieldKey.CUSTOMER to "Customer",
    FieldKey.DAYS_REMAINING to "Days",
)

/**
 * Visual calibration against one sample photo (if one has been taken/
 * chosen yet - falls back to a plain rectangle otherwise): drag a
 * rectangle over the board area, drag two lines to mark the first row's
 * top and the row spacing, then drag three rectangles onto the Project
 * Number/Customer/Days Remaining columns of that row. Everything is
 * stored as fractions (0-1), so the same calibration reapplies correctly
 * to photos of any size or framing.
 */
@Composable
fun CalibrationScreen(template: BoardTemplate, samplePhotoFile: File?, onSave: (BoardTemplate) -> Unit, onBack: () -> Unit) {
    var draft by remember(template) { mutableStateOf(template) }
    val errors = draft.validate()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sampleOverrideFile by remember { mutableStateOf<File?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    val effectiveSampleFile = sampleOverrideFile ?: samplePhotoFile

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) sampleOverrideFile = pendingCameraFile
    }
    val pickPhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) scope.launch { sampleOverrideFile = PhotoStorage.copyFromUri(context, uri) }
    }

    val sample by produceState<Bitmap?>(initialValue = null, effectiveSampleFile) {
        value = effectiveSampleFile?.let { runCatching { ImageLoader.loadUpright(it) }.getOrNull() }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Calibration", style = MaterialTheme.typography.titleLarge)
            if (effectiveSampleFile == null) {
                Text(
                    "Take or choose a photo of your board to calibrate against - showing a placeholder until you do.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        val (file, uri) = PhotoStorage.createCameraOutputFile(context)
                        pendingCameraFile = file
                        takePictureLauncher.launch(uri)
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Take Photo") }
                OutlinedButton(
                    onClick = { pickPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f),
                ) { Text("Choose Photo") }
            }
        }

        item {
            Text("1. Board area", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            Text("Drag the rectangle over the part of the photo that contains the project rows.", style = MaterialTheme.typography.bodySmall)
            BoardAreaPicker(sample = sample, area = draft.boardArea, onAreaChanged = { draft = draft.copy(boardArea = it) })
        }

        item {
            Text("2. First row position & row height", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            Text("Drag the top line to the top of the first visible row, and the second line to the top of the next row.", style = MaterialTheme.typography.bodySmall)
            RowSpacingPicker(
                sample = sample,
                boardArea = draft.boardArea,
                firstRowTopPct = draft.firstRowTopPct,
                rowHeightPct = draft.rowHeightPct,
                onChanged = { top, height -> draft = draft.copy(firstRowTopPct = top, rowHeightPct = height) },
            )
        }

        item {
            Text("3. Field columns", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            Text("Drag each colored box onto that field within the row.", style = MaterialTheme.typography.bodySmall)
            RowColumnsPicker(
                sample = sample,
                boardArea = draft.boardArea,
                firstRowTopPct = draft.firstRowTopPct,
                rowHeightPct = draft.rowHeightPct,
                regions = draft.regions,
                onRegionDragged = { field, rect -> draft = draft.copy(regions = draft.regions.with(field, rect)) },
            )
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

/** Crops [sample] to a fractional sub-rect, or returns null if there's no sample photo. */
private fun cropSample(sample: Bitmap?, rect: FractionalRect): Bitmap? {
    if (sample == null) return null
    val x = (rect.xPct * sample.width).toInt().coerceIn(0, sample.width - 1)
    val y = (rect.yPct * sample.height).toInt().coerceIn(0, sample.height - 1)
    val w = (rect.wPct * sample.width).toInt().coerceAtMost(sample.width - x).coerceAtLeast(1)
    val h = (rect.hPct * sample.height).toInt().coerceAtMost(sample.height - y).coerceAtLeast(1)
    return Bitmap.createBitmap(sample, x, y, w, h)
}

@Composable
private fun BoardAreaPicker(sample: Bitmap?, area: FractionalRect, onAreaChanged: (FractionalRect) -> Unit) {
    val photoAspect = sample?.let { it.width.toFloat() / it.height.toFloat() } ?: (4f / 3f)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().aspectRatio(photoAspect).padding(vertical = 8.dp)) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }

        Backdrop(sample)

        Box(
            modifier = Modifier
                .offset { IntOffset((area.xPct * wPx).roundToInt(), (area.yPct * hPx).roundToInt()) }
                .size(with(density) { (area.wPct * wPx).toDp() }, with(density) { (area.hPct * hPx).toDp() })
                .background(Color(0xFF646CFF).copy(alpha = 0.25f))
                .border(3.dp, Color(0xFF646CFF))
                .pointerInput(wPx, hPx) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        onAreaChanged(area.copy(xPct = area.xPct + drag.x / wPx, yPct = area.yPct + drag.y / hPx).clampedTo())
                    }
                },
        ) {
            LabelChip("Board Area", Color(0xFF646CFF))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledNumberField("Width%", area.wPct * 100, Modifier.weight(1f)) { onAreaChanged(area.copy(wPct = it / 100f).clampedTo()) }
        LabeledNumberField("Height%", area.hPct * 100, Modifier.weight(1f)) { onAreaChanged(area.copy(hPct = it / 100f).clampedTo()) }
    }
}

@Composable
private fun RowSpacingPicker(
    sample: Bitmap?,
    boardArea: FractionalRect,
    firstRowTopPct: Float,
    rowHeightPct: Float,
    onChanged: (top: Float, rowHeight: Float) -> Unit,
) {
    val boardCrop = remember(sample, boardArea) { cropSample(sample, boardArea) }
    val aspect = boardCrop?.let { it.width.toFloat() / it.height.toFloat() } ?: (3f / 2f)
    val secondLinePct = (firstRowTopPct + rowHeightPct).coerceAtMost(0.98f)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().aspectRatio(aspect).padding(vertical = 8.dp)) {
        Backdrop(boardCrop)

        DraggableHLine(yPct = firstRowTopPct, color = Color(0xFF646CFF), label = "Row 1 top") { newY ->
            val clampedTop = newY.coerceIn(0f, secondLinePct - 0.01f)
            onChanged(clampedTop, secondLinePct - clampedTop)
        }
        DraggableHLine(yPct = secondLinePct, color = Color(0xFF2ECC71), label = "Row 2 top") { newY ->
            val clampedSecond = newY.coerceIn(firstRowTopPct + 0.01f, 1f)
            onChanged(firstRowTopPct, clampedSecond - firstRowTopPct)
        }
    }
}

@Composable
private fun RowColumnsPicker(
    sample: Bitmap?,
    boardArea: FractionalRect,
    firstRowTopPct: Float,
    rowHeightPct: Float,
    regions: FieldRegions,
    onRegionDragged: (FieldKey, FractionalRect) -> Unit,
) {
    val rowSlice = remember(sample, boardArea, firstRowTopPct, rowHeightPct) {
        cropSample(sample, boardArea)?.let { cropSample(it, FractionalRect(0f, firstRowTopPct, 1f, rowHeightPct.coerceAtMost(1f - firstRowTopPct))) }
    }
    val aspect = rowSlice?.let { it.width.toFloat() / it.height.toFloat() } ?: 6f

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().aspectRatio(aspect.coerceIn(1f, 12f)).padding(vertical = 8.dp)) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }

        Backdrop(rowSlice)

        for (field in FieldKey.entries) {
            val r = regions.get(field)
            val color = FieldColors.getValue(field)
            Box(
                modifier = Modifier
                    .offset { IntOffset((r.xPct * wPx).roundToInt(), (r.yPct * hPx).roundToInt()) }
                    .size(with(density) { (r.wPct * wPx).toDp() }, with(density) { (r.hPct * hPx).toDp() })
                    .background(color.copy(alpha = 0.35f))
                    .border(3.dp, color)
                    .pointerInput(field, wPx, hPx) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            onRegionDragged(field, r.copy(xPct = r.xPct + drag.x / wPx, yPct = r.yPct + drag.y / hPx).clampedTo())
                        }
                    },
            ) {
                LabelChip(FieldShortLabels.getValue(field), color)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
        FieldKey.entries.forEach { field ->
            Text("● ${FieldLabels.getValue(field)}", color = FieldColors.getValue(field), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Backdrop(bitmap: Bitmap?) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF2C2C32)))
    }
}

@Composable
private fun DraggableHLine(yPct: Float, color: Color, label: String, onDragged: (Float) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val hPx = with(density) { maxHeight.toPx() }
        Box(
            modifier = Modifier
                .offset { IntOffset(0, (yPct * hPx).roundToInt() - 16) }
                .fillMaxWidth()
                .height(32.dp)
                .pointerInput(hPx) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        onDragged(yPct + drag.y / hPx)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawLine(color = color, start = Offset(0f, size.height / 2), end = Offset(size.width, size.height / 2), strokeWidth = 8f)
            }
            LabelChip(label, color, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/** A small solid-background label so text stays legible over any photo backdrop. */
@Composable
private fun LabelChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .background(color, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun LabeledNumberField(label: String, value: Float, modifier: Modifier = Modifier, onChange: (Float) -> Unit) {
    var text by remember(value) { mutableStateOf(if (value == value.toInt().toFloat()) value.toInt().toString() else "%.1f".format(value)) }
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
