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

/** Holds the current capture session: manual photos and guided-scan frames share one processing queue. */
class PhotoCaptureViewModel(application: Application) : AndroidViewModel(application) {

    private val _photos = MutableStateFlow<List<SelectedPhoto>>(emptyList())
    val photos: StateFlow<List<SelectedPhoto>> = _photos.asStateFlow()

    private var pendingCameraFile: File? = null
    private var processedCount = 0

    fun photosPendingProcessing(): List<IndexedValue<SelectedPhoto>> =
        _photos.value.withIndex().drop(processedCount)

    fun markAllProcessed() {
        processedCount = _photos.value.size
    }

    fun prepareCameraCapture(): Uri {
        val (file, uri) = PhotoStorage.createCameraOutputFile(getApplication())
        pendingCameraFile = file
        return uri
    }

    fun onCameraResult(success: Boolean) {
        val file = pendingCameraFile
        pendingCameraFile = null
        if (success && file != null && file.exists()) {
            _photos.update { it + SelectedPhoto(id = UUID.randomUUID().toString(), file = file) }
        } else {
            file?.delete()
        }
    }

    fun addScanPhotos(scanPhotos: List<SelectedPhoto>) {
        if (scanPhotos.isEmpty()) return
        _photos.update { it + scanPhotos }
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
