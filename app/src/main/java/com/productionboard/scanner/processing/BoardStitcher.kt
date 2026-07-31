package com.productionboard.scanner.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.productionboard.scanner.photo.SelectedPhoto
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val TRACK_WIDTH = 320.0
private const val MAX_MOSAIC_DIMENSION = 7000

/**
 * Builds a planar board mosaic from guided-scan frames using the motion
 * positions captured during the live scan. A whiteboard is essentially a
 * flat document, so this avoids spherical panorama projection and keeps the
 * original text geometry much friendlier to OCR.
 */
class BoardStitcher(private val context: Context) {

    data class Result(val photo: SelectedPhoto, val inputCount: Int)

    fun stitch(photos: List<SelectedPhoto>): Result {
        require(photos.size >= 2) { "At least two scan frames are required." }
        require(photos.all { it.scanX != null && it.scanY != null }) {
            "Only guided-scan frames can be stitched automatically."
        }

        val bitmaps = photos.map { photo ->
            var bitmap = ImageLoader.loadUpright(photo.file)
            if (photo.rotationDegrees != 0) bitmap = ImageLoader.rotate(bitmap, photo.rotationDegrees)
            bitmap
        }

        try {
            val frameW = bitmaps.first().width
            val frameH = bitmaps.first().height
            val pxPerTrack = frameW / TRACK_WIDTH
            val rawXs = photos.map { (it.scanX ?: 0.0) * pxPerTrack }
            val rawYs = photos.map { (it.scanY ?: 0.0) * pxPerTrack }

            val minX = rawXs.minOrNull() ?: 0.0
            val minY = rawYs.minOrNull() ?: 0.0
            val maxX = rawXs.maxOrNull() ?: 0.0
            val maxY = rawYs.maxOrNull() ?: 0.0
            var canvasW = (maxX - minX + frameW).roundToInt().coerceAtLeast(frameW)
            var canvasH = (maxY - minY + frameH).roundToInt().coerceAtLeast(frameH)

            val scale = min(1.0, MAX_MOSAIC_DIMENSION.toDouble() / max(canvasW, canvasH))
            canvasW = (canvasW * scale).roundToInt().coerceAtLeast(1)
            canvasH = (canvasH * scale).roundToInt().coerceAtLeast(1)

            val mosaic = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(mosaic)
            canvas.drawColor(android.graphics.Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            photos.indices.forEach { i ->
                val x = ((rawXs[i] - minX) * scale).toFloat()
                val y = ((rawYs[i] - minY) * scale).toFloat()
                val bitmap = bitmaps[i]
                if (scale == 1.0) {
                    canvas.drawBitmap(bitmap, x, y, paint)
                } else {
                    val destW = (bitmap.width * scale).roundToInt()
                    val destH = (bitmap.height * scale).roundToInt()
                    val dest = android.graphics.RectF(x, y, x + destW, y + destH)
                    canvas.drawBitmap(bitmap, null, dest, paint)
                }
            }

            val dir = File(context.cacheDir, "photos").apply { mkdirs() }
            val file = File(dir, "board_scan_${UUID.randomUUID()}.jpg")
            FileOutputStream(file).use { stream ->
                check(mosaic.compress(Bitmap.CompressFormat.JPEG, 96, stream)) { "Could not save stitched board image." }
            }
            mosaic.recycle()

            return Result(
                photo = SelectedPhoto(id = "mosaic-${UUID.randomUUID()}", file = file),
                inputCount = photos.size,
            )
        } finally {
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
        }
    }
}
