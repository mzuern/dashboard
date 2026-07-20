package com.productionboard.scanner.photo

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val FILE_PROVIDER_AUTHORITY = "com.productionboard.scanner.fileprovider"

/**
 * All photo files live in app-private cache storage
 * (`context.cacheDir/photos/`) - never shared storage, never uploaded.
 * The camera intent needs a `content://` Uri to write into (Android
 * doesn't allow apps to hand raw file:// paths across process
 * boundaries), which [FileProvider] supplies for that same private
 * directory.
 */
object PhotoStorage {

    private fun photosDir(context: Context): File =
        File(context.cacheDir, "photos").apply { mkdirs() }

    /** Creates an empty file + content Uri for the camera intent to write a new photo into. */
    fun createCameraOutputFile(context: Context): Pair<File, Uri> {
        val file = File(photosDir(context), "camera_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
        return file to uri
    }

    /** Copies a Photo Picker (or any content://) Uri's bytes into a new app-private file. */
    suspend fun copyFromUri(context: Context, sourceUri: Uri): File = withContext(Dispatchers.IO) {
        val file = File(photosDir(context), "picked_${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not read the selected photo.")
        file
    }

    suspend fun delete(photo: SelectedPhoto) = withContext(Dispatchers.IO) {
        photo.file.delete()
    }

    suspend fun clearAll(context: Context) = withContext(Dispatchers.IO) {
        photosDir(context).listFiles()?.forEach { it.delete() }
    }
}
