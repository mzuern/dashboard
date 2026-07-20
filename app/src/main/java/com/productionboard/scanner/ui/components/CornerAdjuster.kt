package com.productionboard.scanner.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.productionboard.scanner.vision.Corner
import com.productionboard.scanner.vision.Quad
import kotlin.math.roundToInt

private val AccentColor = Color(0xFF646CFF)

/**
 * Lets the user drag the four detected board corners before perspective
 * correction runs. Auto-detection ([com.productionboard.scanner.vision.PerspectiveCorrector])
 * gets it right most of the time, but low-contrast boards can fool
 * contour detection, so this manual fallback is always shown rather than
 * being an error state.
 */
@Composable
fun CornerAdjuster(bitmap: Bitmap, quad: Quad, onChange: (Quad) -> Unit, modifier: Modifier = Modifier) {
    var displaySize by remember { mutableStateOf(IntSize.Zero) }
    val handleSizePx = with(LocalDensity.current) { 28.dp.toPx() }

    fun toScreen(corner: Corner): Offset {
        if (bitmap.width == 0 || bitmap.height == 0 || displaySize.width == 0) return Offset.Zero
        return Offset(
            (corner.x / bitmap.width * displaySize.width).toFloat(),
            (corner.y / bitmap.height * displaySize.height).toFloat(),
        )
    }

    // Screen-space pixel delta -> image-space delta, using the current display scale.
    fun screenDeltaToImageDelta(delta: Offset): Corner {
        if (displaySize.width == 0 || displaySize.height == 0) return Corner(0.0, 0.0)
        return Corner(
            delta.x.toDouble() / displaySize.width * bitmap.width,
            delta.y.toDouble() / displaySize.height * bitmap.height,
        )
    }

    val corners = quad.toList()

    Box(modifier = modifier.fillMaxWidth()) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Scanned board",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().onSizeChanged { displaySize = it },
        )

        if (displaySize.width > 0) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val pts = corners.map { toScreen(it) }
                val path = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    lineTo(pts[1].x, pts[1].y)
                    lineTo(pts[2].x, pts[2].y)
                    lineTo(pts[3].x, pts[3].y)
                    close()
                }
                drawPath(path, color = AccentColor.copy(alpha = 0.18f))
                drawPath(path, color = AccentColor, style = Stroke(width = 4f))
            }

            corners.forEachIndexed { index, corner ->
                val screenPos = toScreen(corner)
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (screenPos.x - handleSizePx / 2).roundToInt(),
                                (screenPos.y - handleSizePx / 2).roundToInt(),
                            )
                        }
                        .size(28.dp)
                        .clip(CircleShape)
                        .pointerInput(index, displaySize) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val imageDelta = screenDeltaToImageDelta(dragAmount)
                                val next = corners.toMutableList()
                                val current = next[index]
                                next[index] = Corner(
                                    (current.x + imageDelta.x).coerceIn(0.0, bitmap.width.toDouble()),
                                    (current.y + imageDelta.y).coerceIn(0.0, bitmap.height.toDouble()),
                                )
                                onChange(Quad(next[0], next[1], next[2], next[3]))
                            }
                        },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(color = Color.White)
                        drawCircle(color = AccentColor, style = Stroke(width = 4f))
                    }
                }
            }
        }
    }
}
