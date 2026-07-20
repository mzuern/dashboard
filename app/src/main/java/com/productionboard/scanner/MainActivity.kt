package com.productionboard.scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.productionboard.scanner.report.GeneratedReport
import com.productionboard.scanner.report.ReportViewModel
import com.productionboard.scanner.review.ReviewViewModel
import com.productionboard.scanner.scanning.ScanViewModel
import com.productionboard.scanner.settings.SettingsViewModel
import com.productionboard.scanner.storage.ReportHistoryEntity
import com.productionboard.scanner.storage.ReportHistoryRepository
import com.productionboard.scanner.ui.navigation.Destinations
import com.productionboard.scanner.ui.screens.CalibrationScreen
import com.productionboard.scanner.ui.screens.HomeScreen
import com.productionboard.scanner.ui.screens.ManualEntryScreen
import com.productionboard.scanner.ui.screens.ReportScreen
import com.productionboard.scanner.ui.screens.ReviewScreen
import com.productionboard.scanner.ui.screens.ScanScreen
import com.productionboard.scanner.ui.screens.SettingsScreen
import com.productionboard.scanner.ui.theme.BoardScannerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BoardScannerTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val navController = rememberNavController()
    val context = LocalContext.current

    val scanViewModel: ScanViewModel = viewModel()
    val reviewViewModel: ReviewViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val reportViewModel: ReportViewModel = viewModel()

    val settings by settingsViewModel.settings.collectAsState()
    val saveErrors by settingsViewModel.saveErrors.collectAsState()
    val reviewRows by reviewViewModel.rows.collectAsState()

    LaunchedEffect(settings) { scanViewModel.updateSettings(settings) }

    var previousReport by remember { mutableStateOf<ReportHistoryEntity?>(null) }
    var viewingPreviousReport by remember { mutableStateOf<GeneratedReport?>(null) }

    LaunchedEffect(Unit) {
        previousReport = ReportHistoryRepository(context).latest()
        reviewViewModel.loadDraftIfPresent { hasDraft ->
            // Resume an interrupted review on cold start (Storage's "draft report" requirement).
            if (hasDraft) navController.navigate(Destinations.REVIEW) { popUpTo(Destinations.HOME) }
        }
    }

    NavHost(navController = navController, startDestination = Destinations.HOME) {
        composable(Destinations.HOME) {
            HomeScreen(
                previousReport = previousReport,
                onScanBoard = { navController.navigate(Destinations.SCAN) },
                onManualEntry = { navController.navigate(Destinations.MANUAL_ENTRY) },
                onSettings = { navController.navigate(Destinations.SETTINGS) },
                onViewPreviousReport = {
                    previousReport?.let {
                        viewingPreviousReport = GeneratedReport(it.subject, it.body)
                        navController.navigate(Destinations.REPORT)
                    }
                },
            )
        }

        composable(Destinations.SCAN) {
            ScanScreen(
                viewModel = scanViewModel,
                debugMode = settings.debugMode,
                onComplete = { rows ->
                    reviewViewModel.setRows(rows)
                    navController.navigate(Destinations.REVIEW) { popUpTo(Destinations.HOME) }
                },
                onCancel = { navController.popBackStack() },
            )
        }

        composable(Destinations.MANUAL_ENTRY) {
            ManualEntryScreen(
                onContinue = { rows ->
                    reviewViewModel.setRows(rows)
                    navController.navigate(Destinations.REVIEW) { popUpTo(Destinations.HOME) }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Destinations.REVIEW) {
            ReviewScreen(
                rows = reviewRows,
                confidenceThreshold = settings.ocrConfidenceThreshold,
                onFieldEdited = { rowId, field, text -> reviewViewModel.updateField(rowId, field, text, settings.ocrConfidenceThreshold) },
                onToggleVerified = { reviewViewModel.toggleVerified(it) },
                onDeleteRow = { reviewViewModel.deleteRow(it) },
                onAddRow = { reviewViewModel.addBlankRow() },
                onProceed = {
                    viewingPreviousReport = null
                    navController.navigate(Destinations.REPORT)
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Destinations.REPORT) {
            ReportScreen(
                rows = reviewRows,
                precomputed = viewingPreviousReport,
                onGenerated = { subject, body -> reportViewModel.recordGenerated(subject, body, reviewRows.size) },
                onBack = { navController.popBackStack() },
                onStartOver = {
                    scanViewModel.cancelScan()
                    reviewViewModel.clearDraft()
                    viewingPreviousReport = null
                    navController.navigate(Destinations.HOME) { popUpTo(Destinations.HOME) { inclusive = true } }
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
                onSave = { template ->
                    settingsViewModel.save(settings.copy(boardTemplate = template)) {}
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
