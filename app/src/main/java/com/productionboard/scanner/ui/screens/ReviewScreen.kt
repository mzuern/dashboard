package com.productionboard.scanner.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.productionboard.scanner.domain.FieldKey
import com.productionboard.scanner.domain.ReviewRow

/**
 * All candidate project rows extracted from every processed photo,
 * combined into one list. Never auto-guesses or silently discards: rows
 * with low-confidence OCR are highlighted, possible duplicates across
 * photos are flagged (not removed), and only rows the user leaves
 * Included go into the generated email.
 */
@Composable
fun ReviewScreen(
    rows: List<ReviewRow>,
    onFieldEdited: (rowId: String, field: FieldKey, text: String) -> Unit,
    onToggleIncluded: (rowId: String) -> Unit,
    onDeleteRow: (rowId: String) -> Unit,
    onAddRow: () -> Unit,
    onProceed: () -> Unit,
    onBack: () -> Unit,
    onAddMorePhotos: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Review Extracted Projects", style = MaterialTheme.typography.titleLarge)
        Text(
            "Rows highlighted in red had low-confidence or unrecognized OCR results. \"Possible duplicate\" rows may be the same project seen in more than one photo - keep only one. Uncheck Include to leave a row out of the email.",
            style = MaterialTheme.typography.bodySmall,
        )

        LazyColumn(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            items(rows, key = { it.id }) { row ->
                ReviewRowCard(
                    row = row,
                    onFieldEdited = { field, text -> onFieldEdited(row.id, field, text) },
                    onToggleIncluded = { onToggleIncluded(row.id) },
                    onDelete = { onDeleteRow(row.id) },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onAddRow) { Text("+ Add Row") }
            OutlinedButton(onClick = onAddMorePhotos) { Text("+ Add More Photos") }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Button(onClick = onProceed, enabled = rows.any { it.included }) { Text("Generate Email") }
        }
    }
}

@Composable
private fun ReviewRowCard(
    row: ReviewRow,
    onFieldEdited: (FieldKey, String) -> Unit,
    onToggleIncluded: () -> Unit,
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceThumbnail(row)
            Column(modifier = Modifier.weight(1f)) {
                if (row.possibleDuplicate) {
                    Text("Possible duplicate", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
                if (row.needsReview) {
                    Text("Needs review", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        OutlinedTextField(
            value = row.projectNumber.value,
            onValueChange = { onFieldEdited(FieldKey.PROJECT_NUMBER, it) },
            label = { Text("Project Number") },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
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
                Checkbox(checked = row.included, onCheckedChange = { onToggleIncluded() })
                Text("Include", modifier = Modifier.padding(top = 12.dp))
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete row") }
        }
    }
}

@Composable
private fun SourceThumbnail(row: ReviewRow) {
    val bitmap = remember(row.id) {
        row.rowThumbnail?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Source crop",
            modifier = Modifier.size(width = 96.dp, height = 48.dp).clip(RoundedCornerShape(4.dp)),
        )
    }
}
