package com.productionboard.scanner.ocr

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tesseract's native library reads trained-data from a real filesystem
 * path, not an APK asset stream, so the bundled eng.traineddata (in
 * assets/tessdata/, see app/src/main/assets) is copied into app-private
 * storage once on first launch. Everything stays local; this never
 * touches the network.
 */
object TessDataInstaller {
    private const val LANG = "eng"

    /** Returns the directory tess-two should be initialized with (the parent of "tessdata/"). */
    suspend fun ensureInstalled(context: Context): File = withContext(Dispatchers.IO) {
        val dataRoot = File(context.filesDir, "tesseract")
        val tessdataDir = File(dataRoot, "tessdata")
        val target = File(tessdataDir, "$LANG.traineddata")

        val expectedSize = context.assets.openFd("tessdata/$LANG.traineddata").use { it.length }
        // A previous copy that was interrupted (app killed mid-extraction, low storage, etc.)
        // would otherwise leave a truncated file behind forever, since a plain exists()
        // check can't tell a partial copy from a complete one - re-extract if the size
        // doesn't match the asset's real size.
        if (!target.exists() || target.length() != expectedSize) {
            tessdataDir.mkdirs()
            context.assets.open("tessdata/$LANG.traineddata").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        dataRoot
    }
}
