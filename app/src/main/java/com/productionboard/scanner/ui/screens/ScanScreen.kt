package com.productionboard.scanner.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.productionboard.scanner.domain.ReviewRow
import com.productionboard.scanner.domain.ScanIssueCode
import com.productionboard.scanner.domain.ScanPhase
import com.productionboard.scanner.scanning.ScanViewModel
import com.productionboard.scanner.ui.components.CornerAdjuster
import com.productionboard.scanner.ui.components.CoverageOverlay

@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    debugMode: Boolean,
    onComplete: (List<ReviewRow>) -> Unit,
    onCancel: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    var permissionPermanentlyDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        if (!granted) permissionPermanentlyDenied = true
    }

    LaunchedEffect(state.phase, state.reviewRows) {
        if (state.phase == ScanPhase.REVIEW && state.reviewRows != null) {
            onComplete(state.reviewRows!!)
        }
    }

    val previewView = remember { PreviewView(context) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when {
                !hasCameraPermission -> CameraPermissionRequest(
                    permanentlyDenied = permissionPermanentlyDenied,
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    },
                )

                state.phase == ScanPhase.IDLE -> {
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                    Column(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    ) {
                        Text("Position the camera over the whiteboard, then start scanning.", color = Color.White)
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Button(onClick = { viewModel.startScanning(lifecycleOwner, previewView) }) { Text("Scan Board") }
                        OutlinedButton(onClick = onCancel) { Text("Cancel") }
                    }
                }

                state.phase == ScanPhase.SCANNING || state.phase == ScanPhase.CONFIRM_INCOMPLETE -> {
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                    CoverageOverlay(
                        snapshot = state.coverageSnapshot,
                        trackingLost = state.activeIssue?.code == ScanIssueCode.TRACKING_LOST,
                        modifier = Modifier.fillMaxSize(),
                    )
                    ScanHud(
                        coveragePct = (state.coverageFraction * 100).toInt(),
                        guidance = state.guidance ?: "Board Coverage",
                        isWarning = state.activeIssue != null,
                        onCancel = { viewModel.cancelScan(); onCancel() },
                        onDone = { viewModel.requestStop() },
                    )

                    if (state.phase == ScanPhase.CONFIRM_INCOMPLETE) {
                        IncompleteScanDialog(
                            message = state.activeIssue?.message ?: "",
                            onKeepScanning = { viewModel.resumeScanning(lifecycleOwner, previewView) },
                            onProcessAnyway = { viewModel.forceProcessAnyway() },
                        )
                    }
                }

                state.phase == ScanPhase.STITCHING || state.phase == ScanPhase.CORRECTING ||
                    state.phase == ScanPhase.DETECTING || state.phase == ScanPhase.OCR -> {
                    ProcessingOverlay(label = processingLabel(state.phase), progress = state.processingProgress)
                }

                state.phase == ScanPhase.ADJUST_CORNERS -> {
                    val mosaic = state.mosaicBitmap
                    val quad = state.quad
                    if (mosaic != null && quad != null) {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Text("Confirm board edges", style = MaterialTheme.typography.titleLarge)
                            Text("Drag the corners so they line up with the whiteboard's edges.", style = MaterialTheme.typography.bodyMedium)
                            CornerAdjuster(
                                bitmap = mosaic,
                                quad = quad,
                                onChange = { viewModel.updateQuad(it) },
                                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                            )
                            Button(onClick = { viewModel.confirmCorners(quad) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Looks Good - Continue")
                            }
                        }
                    }
                }

                else -> Unit
            }
        }

        if (debugMode) {
            com.productionboard.scanner.ui.components.DebugPanel(state)
        }
    }
}

@Composable
private fun CameraPermissionRequest(permanentlyDenied: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text("Camera access is needed to scan the whiteboard. Nothing is recorded or uploaded - frames are processed on-device and discarded.")
        if (permanentlyDenied) {
            Text("Camera permission was denied. Enable it from app settings to continue.", color = MaterialTheme.colorScheme.error)
            Button(onClick = onOpenSettings) { Text("Open App Settings") }
        } else {
            Button(onClick = onRequest) { Text("Grant Camera Access") }
        }
    }
}

@Composable
private fun ScanHud(coveragePct: Int, guidance: String, isWarning: Boolean, onCancel: () -> Unit, onDone: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.background(Color.Black.copy(alpha = 0.55f)).padding(12.dp)) {
                Text("BOARD COVERAGE", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                Text("$coveragePct%", color = Color.White, style = MaterialTheme.typography.headlineMedium)
            }
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                guidance,
                color = if (isWarning) Color(0xFFF5A623) else Color.White,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = onDone) { Text("Done Scanning") }
        }
    }
}

@Composable
private fun IncompleteScanDialog(message: String, onKeepScanning: () -> Unit, onProcessAnyway: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = {},
        title = { Text("Board scan looks incomplete") },
        text = { Text("$message\nYou can keep scanning to fill in the missing (red) areas, or process what's captured so far.") },
        confirmButton = { Button(onClick = onProcessAnyway) { Text("Process Anyway") } },
        dismissButton = { OutlinedButton(onClick = onKeepScanning) { Text("Keep Scanning") } },
    )
}

@Composable
private fun ProcessingOverlay(label: String, progress: Float) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(label)
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
}

private fun processingLabel(phase: ScanPhase): String = when (phase) {
    ScanPhase.STITCHING -> "Stitching frames into one board image..."
    ScanPhase.CORRECTING -> "Correcting perspective..."
    ScanPhase.DETECTING -> "Detecting project rows..."
    ScanPhase.OCR -> "Reading project numbers, customers, and days remaining..."
    else -> "Processing..."
}
