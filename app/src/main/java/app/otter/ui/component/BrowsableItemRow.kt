package app.otter.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.FolderCounts
import app.otter.util.FileFormatters
import app.otter.util.FileTypeIconInfo

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowsableItemRow(
    item: BrowsableItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    folderCounts: FolderCounts? = null,
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
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier.size(24.dp)
            )
        }

        val iconInfo = when (item) {
            is BrowsableItem.FileSystemFile -> FileTypeIconInfo.forMimeType(item.mimeType)
            is BrowsableItem.ArchiveFileEntry -> FileTypeIconInfo.forMimeType(item.mimeType)
            else -> null
        }

        Icon(
            imageVector = when (item) {
                is BrowsableItem.FileSystemDirectory,
                is BrowsableItem.ArchiveDirectory -> Icons.Default.Folder
                is BrowsableItem.ArchiveFile -> Icons.Default.FolderZip
                is BrowsableItem.FileSystemFile,
                is BrowsableItem.ArchiveFileEntry -> iconInfo?.icon ?: Icons.Default.InsertDriveFile
            },
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = when {
                item.canNavigateInto -> MaterialTheme.colorScheme.primary
                isSelected && isSelectionMode -> MaterialTheme.colorScheme.primary
                iconInfo != null -> iconInfo.tint.toColor()
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
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item is BrowsableItem.FileSystemDirectory && folderCounts != null) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = folderCounts.folderCount.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = folderCounts.fileCount.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (!item.canNavigateInto) {
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

@Composable
private fun FileTypeIconInfo.TintKey.toColor(): Color = when (this) {
    FileTypeIconInfo.TintKey.Green -> Color(0xFF4CAF50)
    FileTypeIconInfo.TintKey.Red -> Color(0xFFF44336)
    FileTypeIconInfo.TintKey.Blue -> Color(0xFF2196F3)
    FileTypeIconInfo.TintKey.Orange -> Color(0xFFFF9800)
    FileTypeIconInfo.TintKey.Surface -> MaterialTheme.colorScheme.onSurfaceVariant
}
