package com.productionboard.scanner.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/**
 * Converts a CameraX YUV_420_888 [ImageProxy] frame to an upright ARGB
 * [Bitmap]. Implemented manually (NV21 + YuvImage JPEG round-trip)
 * rather than relying on a specific AndroidX convenience method, since
 * that API has moved between camera-core versions.
 */
object YuvToBitmap {

    fun convert(image: ImageProxy, jpegQuality: Int = 90): Bitmap {
        val nv21 = yuv420ToNv21(image)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), jpegQuality, out)
        val bytes = out.toByteArray()
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("Failed to decode camera frame.")

        val rotation = image.imageInfo.rotationDegrees
        if (rotation == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun yuv420ToNv21(image: ImageProxy): ByteArray {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val ySize = yPlane.buffer.remaining()
        val uSize = uPlane.buffer.remaining()
        val vSize = vPlane.buffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)

        yPlane.buffer.get(nv21, 0, ySize)

        // NV21 expects interleaved VU; CameraX's U/V planes are usually
        // already laid out with the right pixel stride for this to hold on
        // most devices, but we rebuild it explicitly to be safe rather than
        // assume plane byte layout matches NV21 exactly.
        val chromaRowStride = vPlane.rowStride
        val chromaPixelStride = vPlane.pixelStride
        var offset = ySize
        val width = image.width
        val height = image.height
        val vBuffer = vPlane.buffer
        val uBuffer = uPlane.buffer
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val vIndex = row * chromaRowStride + col * chromaPixelStride
                val uIndex = row * chromaRowStride + col * chromaPixelStride
                if (offset + 1 < nv21.size && vIndex < vBuffer.remaining() && uIndex < uBuffer.remaining()) {
                    nv21[offset++] = vBuffer.get(vIndex)
                    nv21[offset++] = uBuffer.get(uIndex)
                }
            }
        }
        return nv21
    }
}
