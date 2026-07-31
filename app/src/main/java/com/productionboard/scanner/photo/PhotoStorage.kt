package com.productionboard.scanner.photo

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val FILE_PROVIDER_AUTHORITY = "com.productionboard.scanner.fileprovider"

/** All photo/scan files live in app-private cache storage and are never uploaded. */
object PhotoStorage {

    private fun photosDir(context: Context): File =
        File(context.cacheDir, "photos").apply { mkdirs() }

    fun createCameraOutputFile(context: Context): Pair<File, Uri> {
        val file = File(photosDir(context), "camera_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
        return file to uri
    }

    suspend fun copyFromUri(context: Context, sourceUri: Uri): File = withContext(Dispatchers.IO) {
        val file = File(photosDir(context), "picked_${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not read the selected photo.")
        file
    }

    suspend fun saveScanFrame(context: Context, bitmap: Bitmap): File = withContext(Dispatchers.IO) {
        val file = File(photosDir(context), "scan_${UUID.randomUUID()}.jpg")
        file.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) { "Could not save scan frame." }
        }
        file
    }

    suspend fun delete(photo: SelectedPhoto) = withContext(Dispatchers.IO) {
        photo.file.delete()
    }

    suspend fun clearAll(context: Context) = withContext(Dispatchers.IO) {
        photosDir(context).listFiles()?.forEach { it.delete() }
    }
}
