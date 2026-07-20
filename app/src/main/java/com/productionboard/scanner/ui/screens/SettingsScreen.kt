package com.productionboard.scanner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
                "Board layout (which part of a photo is the board, row spacing, and the three field columns) is edited from Calibration.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            OutlinedButton(onClick = onOpenCalibration, modifier = Modifier.fillMaxWidth()) { Text("Open Calibration") }
        }

        item {
            Text("OCR Confidence Threshold: ${draft.ocrConfidenceThreshold.toInt()}%", modifier = Modifier.padding(top = 16.dp))
            Text("Rows with a field below this confidence are flagged Needs Review.", style = MaterialTheme.typography.bodySmall)
            NumberField(draft.ocrConfidenceThreshold, 0f, 100f) { draft = draft.copy(ocrConfidenceThreshold = it) }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Checkbox(checked = draft.clearPhotosAfterEmail, onCheckedChange = { draft = draft.copy(clearPhotosAfterEmail = it) })
                Text("Delete photos from this device after generating the email", modifier = Modifier.padding(top = 12.dp))
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
