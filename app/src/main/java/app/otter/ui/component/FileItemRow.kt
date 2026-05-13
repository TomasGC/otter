package app.otter.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.otter.domain.model.FileItem
import app.otter.util.FileFormatters

/**
 * Row displaying a single file or directory item.
 *
 * @param fileItem File or directory to display
 * @param isSelectionMode Whether selection mode is active
 * @param isSelected Whether this item is selected
 * @param onClick Callback when item is clicked
 * @param onLongClick Callback when item is long-clicked
 * @param modifier Optional modifier
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemRow(
    fileItem: FileItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox in selection mode
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier.size(24.dp)
            )
        }

        Icon(
            imageVector = if (fileItem.isDirectory) {
                Icons.Default.Folder
            } else {
                Icons.Default.InsertDriveFile
            },
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (fileItem.isDirectory) {
                MaterialTheme.colorScheme.primary
            } else if (isSelected && isSelectionMode) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fileItem.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (fileItem.sizeBytes != null) {
                    Text(
                        text = FileFormatters.formatFileSize(fileItem.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = FileFormatters.formatDate(fileItem.lastModified),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
