package com.productionboard.scanner.processing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

private const val MAX_DIMENSION = 3000

/** Decodes a photo file into an upright Bitmap: EXIF orientation is read and applied, and the image is downsampled if huge (keeps memory bounded on a mid-range phone without destroying OCR-relevant detail). */
object ImageLoader {

    fun loadUpright(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while ((bounds.outWidth / sample) > MAX_DIMENSION || (bounds.outHeight / sample) > MAX_DIMENSION) sample *= 2

        val decoded = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Could not read photo: ${file.name}")

        val exifDegrees = readExifRotationDegrees(file)
        return if (exifDegrees == 0) decoded else rotate(decoded, exifDegrees)
    }

    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun readExifRotationDegrees(file: File): Int {
        return runCatching {
            val exif = ExifInterface(file.absolutePath)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }.getOrDefault(0)
    }
}
