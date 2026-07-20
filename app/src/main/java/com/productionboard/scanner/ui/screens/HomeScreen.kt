package com.productionboard.scanner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.productionboard.scanner.storage.ReportHistoryEntity

@Composable
fun HomeScreen(
    previousReport: ReportHistoryEntity?,
    onScanBoard: () -> Unit,
    onManualEntry: () -> Unit,
    onSettings: () -> Unit,
    onViewPreviousReport: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Board Scanner") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(
                "Scan the production whiteboard to generate today's status email. Everything runs on this device - no internet needed.",
                style = MaterialTheme.typography.bodyLarge,
            )

            Button(onClick = onScanBoard, modifier = Modifier.fillMaxWidth()) {
                Text("Scan Board")
            }
            OutlinedButton(onClick = onManualEntry, modifier = Modifier.fillMaxWidth()) { Text("Manual Entry") }
            TextButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("Settings") }

            if (previousReport != null) {
                TextButton(onClick = onViewPreviousReport) {
                    Text("Previous Report (${previousReport.rowCount} projects)")
                }
            }
        }
    }
}
