package com.productionboard.scanner.photo

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Holds the current photo-capture session: selected photos, pending in-flight camera capture. Intentionally not persisted - a killed app just starts a fresh session. */
class PhotoCaptureViewModel(application: Application) : AndroidViewModel(application) {

    private val _photos = MutableStateFlow<List<SelectedPhoto>>(emptyList())
    val photos: StateFlow<List<SelectedPhoto>> = _photos.asStateFlow()

    private var pendingCameraFile: File? = null

    /** How many of the current photos (by list position) have already been processed - lets "Add More Photos" only process the new ones. */
    private var processedCount = 0

    /** Photos not yet processed, paired with their stable index in the full list (used as ReviewRow.sourcePhotoIndex). */
    fun photosPendingProcessing(): List<IndexedValue<SelectedPhoto>> =
        _photos.value.withIndex().drop(processedCount)

    fun markAllProcessed() {
        processedCount = _photos.value.size
    }

    /** Creates the destination file/Uri for a new camera capture; call before launching the TakePicture contract. */
    fun prepareCameraCapture(): Uri {
        val (file, uri) = PhotoStorage.createCameraOutputFile(getApplication())
        pendingCameraFile = file
        return uri
    }

    /** Call from the TakePicture result callback with whether the capture succeeded. */
    fun onCameraResult(success: Boolean) {
        val file = pendingCameraFile
        pendingCameraFile = null
        if (success && file != null && file.exists()) {
            _photos.update { it + SelectedPhoto(id = UUID.randomUUID().toString(), file = file) }
        } else {
            file?.delete()
        }
    }

    fun onPhotosPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val added = uris.map { uri ->
                val file = PhotoStorage.copyFromUri(getApplication(), uri)
                SelectedPhoto(id = UUID.randomUUID().toString(), file = file)
            }
            _photos.update { it + added }
        }
    }

    fun removePhoto(id: String) {
        val photo = _photos.value.find { it.id == id } ?: return
        _photos.update { list -> list.filterNot { it.id == id } }
        viewModelScope.launch { PhotoStorage.delete(photo) }
    }

    fun rotatePhoto(id: String) {
        _photos.update { list ->
            list.map { if (it.id == id) it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360) else it }
        }
    }

    fun clearSession() {
        val current = _photos.value
        _photos.value = emptyList()
        processedCount = 0
        viewModelScope.launch { current.forEach { PhotoStorage.delete(it) } }
    }
}
