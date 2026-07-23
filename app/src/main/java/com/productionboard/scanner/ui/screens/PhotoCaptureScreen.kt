package com.productionboard.scanner.ui.screens

import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.productionboard.scanner.domain.BoardTemplate
import com.productionboard.scanner.photo.PhotoCaptureViewModel
import com.productionboard.scanner.photo.SelectedPhoto

/**
 * The app's single main screen: take/choose photos of the board, review
 * thumbnails, then process. No live camera preview, no scanning session -
 * just ordinary photos, taken however many are needed to cover the board.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoCaptureScreen(
    viewModel: PhotoCaptureViewModel,
    boardTemplate: BoardTemplate,
    onProcessPhotos: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCalibration: () -> Unit,
) {
    val photos by viewModel.photos.collectAsState()
    val context = LocalContext.current
    var previewPhoto by remember { mutableStateOf<SelectedPhoto?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        viewModel.onCameraResult(success)
    }
    val pickPhotosLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        viewModel.onPhotosPicked(uris)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Board Photos") },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "Take or choose photos covering the whole board. It's fine to use several overlapping photos instead of one.",
                style = MaterialTheme.typography.bodySmall,
            )

            if (boardTemplate == BoardTemplate.DEFAULT) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Board not calibrated yet",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            "Without calibration, the app doesn't know where your board's rows and columns are, " +
                                "and processing will produce garbage. Take one photo of your board, then calibrate.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        TextButton(onClick = onOpenCalibration, modifier = Modifier.padding(top = 4.dp)) {
                            Text("Calibrate Now")
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { takePictureLauncher.launch(viewModel.prepareCameraCapture()) },
                    modifier = Modifier.weight(1f),
                ) { Text("Take Photo") }
                OutlinedButton(
                    onClick = { pickPhotosLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f),
                ) { Text("Choose Photos") }
            }

            if (photos.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No photos yet.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    items(photos, key = { it.id }) { photo ->
                        PhotoThumbnail(
                            photo = photo,
                            onClick = { previewPhoto = photo },
                            onRemove = { viewModel.removePhoto(photo.id) },
                            onRotate = { viewModel.rotatePhoto(photo.id) },
                        )
                    }
                }
            }

            Button(
                onClick = onProcessPhotos,
                enabled = photos.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text("Process Photos (${photos.size})") }
        }
    }

    previewPhoto?.let { photo ->
        PhotoPreviewDialog(photo = photo, onDismiss = { previewPhoto = null })
    }
}

@Composable
private fun PhotoThumbnail(photo: SelectedPhoto, onClick: () -> Unit, onRemove: () -> Unit, onRotate: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        val bitmap = remember(photo.id, photo.rotationDegrees) { decodeThumbnail(photo) }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Selected photo",
                modifier = Modifier.fillMaxSize(),
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Remove photo", tint = MaterialTheme.colorScheme.error)
        }
        IconButton(onClick = onRotate, modifier = Modifier.align(Alignment.BottomEnd).size(28.dp)) {
            Icon(Icons.Default.RotateRight, contentDescription = "Rotate photo")
        }
    }
}

@Composable
private fun PhotoPreviewDialog(photo: SelectedPhoto, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        val bitmap = remember(photo.id, photo.rotationDegrees) { decodeThumbnail(photo, maxDimension = 1600) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onDismiss),
        ) {
            if (bitmap != null) {
                Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Photo preview", modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Downsampled preview bitmap with the user's manual rotation applied (EXIF correction happens later, at processing time). */
private fun decodeThumbnail(photo: SelectedPhoto, maxDimension: Int = 400): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(photo.file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while ((bounds.outWidth / sample) > maxDimension || (bounds.outHeight / sample) > maxDimension) sample *= 2

    val decoded = BitmapFactory.decodeFile(photo.file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return null
    if (photo.rotationDegrees == 0) return decoded

    val matrix = Matrix().apply { postRotate(photo.rotationDegrees.toFloat()) }
    return android.graphics.Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
}
