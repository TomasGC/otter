package app.otter.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.otter.BuildConfig

/**
 * Displays the app version in small text.
 * Used in bottom-left corner of screens and dialogs.
 */
@Composable
fun VersionLabel(modifier: Modifier = Modifier) {
    Text(
        text = "v${BuildConfig.VERSION_NAME}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = modifier.padding(8.dp)
    )
}
