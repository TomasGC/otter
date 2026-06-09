package app.otter.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.otter.domain.model.BrowsableItem
import app.otter.util.FileFormatters

/**
 * Row displaying a single browsable item (file, directory, or archive).
 *
 * @param item Browsable item to display
 * @param isSelectionMode Whether selection mode is active
 * @param isSelected Whether this item is selected
 * @param onClick Callback when item is clicked
 * @param onLongClick Callback when item is long-clicked
 * @param modifier Optional modifier
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowsableItemRow(
    item: BrowsableItem,
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
            imageVector = when (item) {
                is BrowsableItem.FileSystemDirectory,
                is BrowsableItem.ArchiveDirectory -> Icons.Default.Folder
                is BrowsableItem.ArchiveFile -> Icons.Default.FolderZip
                is BrowsableItem.FileSystemFile,
                is BrowsableItem.ArchiveFileEntry -> Icons.Default.InsertDriveFile
            },
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = when {
                item.canNavigateInto -> MaterialTheme.colorScheme.primary
                isSelected && isSelectionMode -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Only show size for files, not directories
                if (!item.canNavigateInto) {
                    Text(
                        text = FileFormatters.formatFileSize(item.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = FileFormatters.formatDate(item.lastModified),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
