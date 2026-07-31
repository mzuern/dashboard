package com.productionboard.scanner.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.productionboard.scanner.camera.CameraController
import com.productionboard.scanner.photo.SelectedPhoto
import com.productionboard.scanner.scanning.GuidedScanViewModel

/** Live, guided panorama capture. Good overlapping frames are retained automatically; the user only moves the phone and taps Use Scan. */
@Composable
fun GuidedScanScreen(
    viewModel: GuidedScanViewModel,
    onComplete: (List<SelectedPhoto>) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsState()
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val controller = remember { CameraController(context.applicationContext) }
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            controller.start(lifecycleOwner, previewView) { frame -> viewModel.acceptFrame(frame) }
        }
    }

    DisposableEffect(Unit) {
        onDispose { controller.shutdown() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            Column(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Guided Board Scan",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Move in strips. Keep roughly half of the previous view visible as you move.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            if (!hasPermission) {
                Text("Camera access is needed only while scanning the board.")
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text("Allow Camera") }
            } else {
                Text(state.instruction, style = MaterialTheme.typography.bodyLarge)
                Text("Useful frames captured: ${state.frameCount}", modifier = Modifier.padding(top = 6.dp))
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), enabled = !state.isSaving) {
                    Text("Cancel")
                }
                Button(
                    onClick = { viewModel.finish(onComplete) },
                    modifier = Modifier.weight(1f),
                    enabled = hasPermission && state.frameCount >= 2 && !state.isSaving,
                ) {
                    if (state.isSaving) CircularProgressIndicator() else Text("Use Scan")
                }
            }
        }
    }
}
