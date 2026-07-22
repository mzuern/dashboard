package com.productionboard.scanner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.productionboard.scanner.domain.ReviewRow
import com.productionboard.scanner.processing.ProcessingState

@Composable
fun ProcessingScreen(
    state: ProcessingState,
    onDone: (List<ReviewRow>) -> Unit,
    result: List<ReviewRow>?,
    onCancel: () -> Unit,
) {
    LaunchedEffect(state.done, result) {
        if (state.done && result != null) onDone(result)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        if (state.error != null) {
            Text("Something went wrong", style = MaterialTheme.typography.titleMedium)
            Text(state.error, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onCancel) { Text("Back") }
        } else {
            Text("Reading photo ${state.currentPhotoIndex} of ${state.totalPhotos}...", style = MaterialTheme.typography.titleMedium)
            val progress = if (state.totalPhotos > 0) state.currentPhotoIndex.toFloat() / state.totalPhotos else 0f
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text("Extracting Project Number, Customer, and Estimated Days Remaining locally on this device.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
