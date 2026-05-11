package app.otter.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.otter.R

/**
 * Reusable extraction progress view.
 * Displays circular progress indicator, percentage, file count, and current file name.
 *
 * @param fileName Name of the archive being extracted
 * @param progress Progress value (0.0 to 1.0)
 * @param extractedCount Number of files extracted so far
 * @param totalCount Total number of files to extract
 * @param currentFile Current file being extracted
 * @param onStop Callback when Stop button is clicked
 * @param onBackground Callback when Background button is clicked
 */
@Composable
fun ExtractionProgressView(
    fileName: String,
    progress: Float,
    extractedCount: Int,
    totalCount: Int,
    currentFile: String,
    onStop: () -> Unit,
    onBackground: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.extraction_title, fileName),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier
                .size(120.dp)
                .padding(bottom = 16.dp),
        )

        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (totalCount > 0) {
            Text(
                text = stringResource(R.string.extraction_progress_files, extractedCount, totalCount),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (currentFile.isNotEmpty()) {
            Text(
                text = currentFile,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(onClick = onStop) {
                Text(stringResource(R.string.extraction_button_stop))
            }

            TextButton(onClick = onBackground) {
                Text(stringResource(R.string.extraction_button_background))
            }
        }
    }
}
