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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.productionboard.scanner.domain.FieldResult
import com.productionboard.scanner.domain.ReviewRow

/**
 * Fallback entry point that keeps the app useful even when a scan fails
 * or isn't practical: the same three fields, typed in directly, feeding
 * the same review/report flow as a scan would.
 */
@Composable
fun ManualEntryScreen(onContinue: (List<ReviewRow>) -> Unit, onBack: () -> Unit) {
    var rows by remember { mutableStateOf(listOf<ReviewRow>()) }
    var projectNumber by remember { mutableStateOf("") }
    var customer by remember { mutableStateOf("") }
    var daysRemaining by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Manual Entry", style = MaterialTheme.typography.titleLarge)
        Text("Add project rows by hand - useful if a scan didn't work out.", style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(value = projectNumber, onValueChange = { projectNumber = it }, label = { Text("Project Number") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true)
        OutlinedTextField(value = customer, onValueChange = { customer = it }, label = { Text("Customer") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), singleLine = true)
        OutlinedTextField(value = daysRemaining, onValueChange = { daysRemaining = it }, label = { Text("Estimated Days Remaining") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), singleLine = true)

        Button(
            onClick = {
                if (projectNumber.isNotBlank() || customer.isNotBlank()) {
                    val field = { text: String -> FieldResult(rawText = text, value = text.trim(), confidence = 100f, formatValid = text.isNotBlank(), preprocessingVariant = "manual") }
                    rows = rows + ReviewRow(
                        id = "manual-${System.currentTimeMillis()}",
                        rowIndex = rows.size,
                        projectNumber = field(projectNumber),
                        customer = field(customer),
                        daysRemaining = field(daysRemaining),
                        verified = true,
                        needsReview = false,
                    )
                    projectNumber = ""; customer = ""; daysRemaining = ""
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text("Add Row") }

        LazyColumn(modifier = Modifier.weight(1f).padding(top = 12.dp)) {
            items(rows, key = { it.id }) { row ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("${row.projectNumber.value} - ${row.customer.value} - ${row.daysRemaining.value}", modifier = Modifier.weight(1f))
                    TextButton(onClick = { rows = rows.filterNot { it.id == row.id } }) { Text("Remove") }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Button(onClick = { onContinue(rows) }, enabled = rows.isNotEmpty()) { Text("Continue to Review") }
        }
    }
}
