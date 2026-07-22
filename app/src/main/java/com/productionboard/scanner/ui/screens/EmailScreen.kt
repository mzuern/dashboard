package com.productionboard.scanner.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.productionboard.scanner.domain.ReviewRow
import com.productionboard.scanner.report.EmailGenerator

/**
 * Generates the email text from the rows the user approved (Include
 * checked) and lets them copy it or hand it to whatever app they pick
 * via Android's share sheet. Nothing is ever sent automatically or
 * directly from this app.
 */
@Composable
fun EmailScreen(rows: List<ReviewRow>, onBack: () -> Unit, onStartOver: () -> Unit) {
    val context = LocalContext.current
    val email = remember(rows) { EmailGenerator.generate(rows) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Daily Production Status Email", style = MaterialTheme.typography.titleLarge)
        Text("Never sent automatically - copy it or share it to your email app.", style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(
            value = email.subject,
            onValueChange = {},
            readOnly = true,
            label = { Text("Subject") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        OutlinedTextField(
            value = email.body,
            onValueChange = {},
            readOnly = true,
            label = { Text("Body") },
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp),
        )

        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { copyToClipboard(context, "Subject", email.subject) }) { Text("Copy Subject") }
            OutlinedButton(onClick = { copyToClipboard(context, "Body", email.body) }) { Text("Copy Body") }
        }
        Button(onClick = { shareEmail(context, email.subject, email.body) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Share via Email / Other App")
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back to Review") }
            OutlinedButton(onClick = onStartOver) { Text("Start Over") }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService<ClipboardManager>()
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun shareEmail(context: Context, subject: String, body: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(Intent.createChooser(intent, "Share email"))
}
