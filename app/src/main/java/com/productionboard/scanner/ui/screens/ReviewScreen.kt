package com.productionboard.scanner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.productionboard.scanner.domain.FieldKey
import com.productionboard.scanner.domain.ReviewRow

/**
 * Table for reviewing/editing/verifying/deleting OCR results before the
 * report is generated. Never auto-guesses: low-confidence rows stay
 * flagged until a human edits or verifies them.
 */
@Composable
fun ReviewScreen(
    rows: List<ReviewRow>,
    confidenceThreshold: Float,
    onFieldEdited: (rowId: String, field: FieldKey, text: String) -> Unit,
    onToggleVerified: (rowId: String) -> Unit,
    onDeleteRow: (rowId: String) -> Unit,
    onAddRow: () -> Unit,
    onProceed: () -> Unit,
    onBack: () -> Unit,
) {
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Review Scanned Projects", style = MaterialTheme.typography.titleLarge)
        Text(
            "Rows highlighted in red had low-confidence or unrecognized OCR results. Edit the value and check Verified once it's correct.",
            style = MaterialTheme.typography.bodySmall,
        )

        LazyColumn(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            items(rows, key = { it.id }) { row ->
                ReviewRowCard(
                    row = row,
                    onFieldEdited = { field, text -> onFieldEdited(row.id, field, text) },
                    onToggleVerified = { onToggleVerified(row.id) },
                    onDelete = { onDeleteRow(row.id) },
                )
            }
        }

        OutlinedButton(onClick = onAddRow) { Text("+ Add Row") }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp)) }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Button(
                onClick = {
                    val unverified = rows.count { !it.verified }
                    if (unverified > 0) {
                        error = "$unverified row(s) still need review before generating the report."
                    } else {
                        error = null
                        onProceed()
                    }
                },
                enabled = rows.isNotEmpty(),
            ) { Text("Generate Report") }
        }
    }
}

@Composable
private fun ReviewRowCard(
    row: ReviewRow,
    onFieldEdited: (FieldKey, String) -> Unit,
    onToggleVerified: () -> Unit,
    onDelete: () -> Unit,
) {
    val borderColor = if (row.needsReview) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(if (row.needsReview) MaterialTheme.colorScheme.error.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        OutlinedTextField(
            value = row.projectNumber.value,
            onValueChange = { onFieldEdited(FieldKey.PROJECT_NUMBER, it) },
            label = { Text("Project Number") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = row.customer.value,
            onValueChange = { onFieldEdited(FieldKey.CUSTOMER, it) },
            label = { Text("Customer") },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            singleLine = true,
        )
        OutlinedTextField(
            value = row.daysRemaining.value,
            onValueChange = { onFieldEdited(FieldKey.DAYS_REMAINING, it) },
            label = { Text("Estimated Days Remaining") },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            singleLine = true,
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Row {
                Checkbox(checked = row.verified, onCheckedChange = { onToggleVerified() })
                Text("Verified", modifier = Modifier.padding(top = 12.dp))
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete row") }
        }
    }
}
