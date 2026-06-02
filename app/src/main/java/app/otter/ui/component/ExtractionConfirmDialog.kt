package app.otter.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.otter.domain.model.BrowsableItem

/**
 * Dialog to confirm archive extraction.
 *
 * @param fileItem Archive file to extract
 * @param onConfirm Callback when extraction is confirmed
 * @param onDismiss Callback when dialog is dismissed
 */
@Composable
fun ExtractionConfirmDialog(
    fileItem: BrowsableItem.ArchiveFile,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Extract archive?") },
        text = {
            Column {
                Text("Do you want to extract this archive?")
                Text(
                    text = fileItem.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )

                // Version label
                VersionLabel(modifier = Modifier.padding(top = 16.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Extract")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
