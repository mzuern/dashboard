package com.productionboard.scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.productionboard.scanner.photo.PhotoCaptureViewModel
import com.productionboard.scanner.processing.ProcessingViewModel
import com.productionboard.scanner.review.ReviewViewModel
import com.productionboard.scanner.scanning.GuidedScanViewModel
import com.productionboard.scanner.settings.SettingsViewModel
import com.productionboard.scanner.ui.navigation.Destinations
import com.productionboard.scanner.ui.screens.CalibrationScreen
import com.productionboard.scanner.ui.screens.EmailScreen
import com.productionboard.scanner.ui.screens.GuidedScanScreen
import com.productionboard.scanner.ui.screens.PhotoCaptureScreen
import com.productionboard.scanner.ui.screens.ProcessingScreen
import com.productionboard.scanner.ui.screens.ReviewScreen
import com.productionboard.scanner.ui.screens.SettingsScreen
import com.productionboard.scanner.ui.theme.BoardScannerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BoardScannerTheme { AppRoot() } }
    }
}

@Composable
private fun AppRoot() {
    val navController = rememberNavController()

    val photoCaptureViewModel: PhotoCaptureViewModel = viewModel()
    val guidedScanViewModel: GuidedScanViewModel = viewModel()
    val processingViewModel: ProcessingViewModel = viewModel()
    val reviewViewModel: ReviewViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    val settings by settingsViewModel.settings.collectAsState()
    val saveErrors by settingsViewModel.saveErrors.collectAsState()
    val reviewRows by reviewViewModel.rows.collectAsState()
    val photos by photoCaptureViewModel.photos.collectAsState()
    val processingState by processingViewModel.state.collectAsState()
    val processingResult by processingViewModel.result.collectAsState()

    LaunchedEffect(Unit) {
        reviewViewModel.loadDraftIfPresent { hasDraft ->
            if (hasDraft) navController.navigate(Destinations.REVIEW) { popUpTo(Destinations.PHOTO_CAPTURE) }
        }
    }

    NavHost(navController = navController, startDestination = Destinations.PHOTO_CAPTURE) {
        composable(Destinations.PHOTO_CAPTURE) {
            PhotoCaptureScreen(
                viewModel = photoCaptureViewModel,
                boardTemplate = settings.boardTemplate,
                onGuidedScan = { navController.navigate(Destinations.GUIDED_SCAN) },
                onProcessPhotos = { navController.navigate(Destinations.PROCESSING) },
                onOpenSettings = { navController.navigate(Destinations.SETTINGS) },
                onOpenCalibration = { navController.navigate(Destinations.CALIBRATION) },
            )
        }

        composable(Destinations.GUIDED_SCAN) {
            GuidedScanScreen(
                viewModel = guidedScanViewModel,
                onComplete = { scanPhotos ->
                    photoCaptureViewModel.addScanPhotos(scanPhotos)
                    guidedScanViewModel.reset()
                    navController.navigate(Destinations.PROCESSING) {
                        popUpTo(Destinations.PHOTO_CAPTURE)
                    }
                },
                onCancel = {
                    guidedScanViewModel.reset()
                    navController.popBackStack()
                },
            )
        }

        composable(Destinations.PROCESSING) {
            LaunchedEffect(Unit) {
                processingViewModel.start(photoCaptureViewModel.photosPendingProcessing())
            }
            ProcessingScreen(
                state = processingState,
                result = processingResult,
                onDone = { rows ->
                    photoCaptureViewModel.markAllProcessed()
                    if (reviewRows.isEmpty()) reviewViewModel.setRows(rows) else reviewViewModel.addRows(rows)
                    navController.navigate(Destinations.REVIEW) { popUpTo(Destinations.PHOTO_CAPTURE) }
                },
                onCancel = { navController.popBackStack() },
            )
        }

        composable(Destinations.REVIEW) {
            ReviewScreen(
                rows = reviewRows,
                onFieldEdited = { rowId, field, text -> reviewViewModel.updateField(rowId, field, text, settings.ocrConfidenceThreshold) },
                onToggleIncluded = { reviewViewModel.toggleIncluded(it) },
                onDeleteRow = { reviewViewModel.deleteRow(it) },
                onAddRow = { reviewViewModel.addBlankRow() },
                onProceed = { navController.navigate(Destinations.EMAIL) },
                onBack = { navController.popBackStack() },
                onAddMorePhotos = { navController.navigate(Destinations.PHOTO_CAPTURE) },
            )
        }

        composable(Destinations.EMAIL) {
            EmailScreen(
                rows = reviewRows,
                onBack = { navController.popBackStack() },
                onStartOver = {
                    if (settings.clearPhotosAfterEmail) photoCaptureViewModel.clearSession()
                    reviewViewModel.clearDraft()
                    navController.navigate(Destinations.PHOTO_CAPTURE) { popUpTo(Destinations.PHOTO_CAPTURE) { inclusive = true } }
                },
            )
        }

        composable(Destinations.SETTINGS) {
            SettingsScreen(
                settings = settings,
                saveErrors = saveErrors,
                onSave = { updated -> settingsViewModel.save(updated) {} },
                onReset = { settingsViewModel.resetToDefaults {} },
                onOpenCalibration = { navController.navigate(Destinations.CALIBRATION) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Destinations.CALIBRATION) {
            CalibrationScreen(
                template = settings.boardTemplate,
                samplePhotoFile = photos.firstOrNull()?.file,
                onSave = { template ->
                    settingsViewModel.save(settings.copy(boardTemplate = template)) {}
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
