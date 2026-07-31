package com.productionboard.scanner.processing

import android.content.Context
import android.graphics.Bitmap
import com.productionboard.scanner.photo.SelectedPhoto
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.stitching.Stitcher

/**
 * Builds one high-resolution board mosaic from a sequence of overlapping
 * scan frames. Frames should be captured in board order with roughly
 * 40-60% overlap. Stitching is deliberately separated from OCR: once a
 * mosaic exists, the normal board/row/field pipeline can process it as a
 * single image.
 */
class BoardStitcher(private val context: Context) {

    data class Result(
        val photo: SelectedPhoto,
        val inputCount: Int,
    )

    fun stitch(photos: List<SelectedPhoto>): Result {
        require(photos.isNotEmpty()) { "No scan frames were provided." }
        if (photos.size == 1) return Result(photos.first(), 1)

        val mats = mutableListOf<Mat>()
        val bitmaps = mutableListOf<Bitmap>()
        val panorama = Mat()

        try {
            for (photo in photos) {
                var bitmap = ImageLoader.loadUpright(photo.file)
                if (photo.rotationDegrees != 0) {
                    bitmap = ImageLoader.rotate(bitmap, photo.rotationDegrees)
                }
                bitmaps += bitmap
                val mat = Mat()
                Utils.bitmapToMat(bitmap, mat)
                mats += mat
            }

            val stitcher = Stitcher.create(Stitcher.PANORAMA)
            val status = stitcher.stitch(mats, panorama)
            check(status == Stitcher.OK) {
                "The scan frames could not be stitched (OpenCV status $status). Retake them in order with about half of each frame overlapping the previous one."
            }
            check(!panorama.empty()) { "The stitched board image was empty." }

            val output = Bitmap.createBitmap(panorama.cols(), panorama.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(panorama, output)

            val dir = File(context.cacheDir, "photos").apply { mkdirs() }
            val file = File(dir, "board_scan_${UUID.randomUUID()}.jpg")
            FileOutputStream(file).use { stream ->
                check(output.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                    "Could not save the stitched board image."
                }
            }
            output.recycle()

            return Result(
                photo = SelectedPhoto(id = "scan-${UUID.randomUUID()}", file = file),
                inputCount = photos.size,
            )
        } finally {
            mats.forEach { it.release() }
            panorama.release()
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
        }
    }
}
