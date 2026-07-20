package com.productionboard.scanner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.productionboard.scanner.domain.AppSettings
import com.productionboard.scanner.domain.CAMERA_RESOLUTION_PRESETS

@Composable
fun SettingsScreen(
    settings: AppSettings,
    saveErrors: List<String>,
    onSave: (AppSettings) -> Unit,
    onReset: () -> Unit,
    onOpenCalibration: () -> Unit,
    onBack: () -> Unit,
) {
    var draft by remember(settings) { mutableStateOf(settings) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Settings", style = MaterialTheme.typography.titleLarge)
            Text(
                "Board Width ${draft.boardTemplate.boardWidthPx}px x Height ${draft.boardTemplate.boardHeightPx}px, " +
                    "Row Height ${draft.boardTemplate.rowHeightPx}px. Field regions and row layout are edited from Calibration.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            OutlinedButton(onClick = onOpenCalibration, modifier = Modifier.fillMaxWidth()) { Text("Open Calibration") }
        }

        item {
            Text("OCR Confidence Threshold: ${draft.ocrConfidenceThreshold.toInt()}%", modifier = Modifier.padding(top = 16.dp))
            NumberField(draft.ocrConfidenceThreshold, 0f, 100f) { draft = draft.copy(ocrConfidenceThreshold = it) }
        }

        item {
            Text("Coverage Threshold: ${(draft.coverageThreshold * 100).toInt()}%", modifier = Modifier.padding(top = 12.dp))
            NumberField(draft.coverageThreshold * 100, 1f, 100f) { draft = draft.copy(coverageThreshold = it / 100f) }
        }

        item {
            Text("Camera Resolution", modifier = Modifier.padding(top = 12.dp))
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = draft.cameraResolution.label,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.material3.ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    CAMERA_RESOLUTION_PRESETS.forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(preset.label) },
                            onClick = { draft = draft.copy(cameraResolution = preset); expanded = false },
                        )
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Checkbox(checked = draft.debugMode, onCheckedChange = { draft = draft.copy(debugMode = it) })
                Text("Debug Mode", modifier = Modifier.padding(top = 12.dp))
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = draft.deleteImagesAfterReport, onCheckedChange = { draft = draft.copy(deleteImagesAfterReport = it) })
                Text("Delete stitched images after report is generated", modifier = Modifier.padding(top = 12.dp))
            }
        }

        if (saveErrors.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    saveErrors.forEach { Text("- $it", color = MaterialTheme.colorScheme.error) }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBack) { Text("Back") }
                OutlinedButton(onClick = onReset) { Text("Reset to Defaults") }
                Button(onClick = { onSave(draft) }) { Text("Save Settings") }
            }
        }
    }
}

@Composable
private fun NumberField(value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toInt().toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            input.toFloatOrNull()?.let { onChange(it.coerceIn(min, max)) }
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}
