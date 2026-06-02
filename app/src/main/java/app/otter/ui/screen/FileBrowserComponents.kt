package app.otter.ui.screen

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.otter.domain.model.BrowsableItem
import app.otter.service.ExtractionEventBus
import app.otter.ui.component.BrowsableItemRow
import app.otter.ui.component.ExtractionScreen
import app.otter.ui.viewmodel.FileBrowserUiState
import app.otter.ui.viewmodel.SortOrder

/**
 * Top app bar for file browser with selection mode support.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserTopAppBar(
    uiState: FileBrowserUiState,
    onExtractAllVisible: () -> Unit,
    onNavigateUp: () -> Unit,
    onExitSelectionMode: () -> Unit,
    onSelectAll: () -> Unit,
    onExtractSelected: () -> Unit,
    onToggleFilter: () -> Unit,
    onCycleSortOrder: () -> Unit,
    onRefresh: () -> Unit,
) {
    TopAppBar(
        title = {
            when (val state = uiState) {
                is FileBrowserUiState.Success -> {
                    if (state.isSelectionMode) {
                        Text("${state.selectedCount} selected")
                    } else {
                        Text(
                            text = state.currentPath,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                else -> Text("File Browser")
            }
        },
        navigationIcon = {
            val state = uiState as? FileBrowserUiState.Success
            if (state?.isSelectionMode == true) {
                IconButton(onClick = onExitSelectionMode) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Exit selection mode"
                    )
                }
            } else {
                val canNavigateUp = state?.canNavigateUp == true
                IconButton(
                    onClick = onNavigateUp,
                    enabled = canNavigateUp
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Navigate up"
                    )
                }
            }
        },
        actions = {
            val state = uiState as? FileBrowserUiState.Success

            if (state?.isSelectionMode == true) {
                // Select All button
                TextButton(onClick = onSelectAll) {
                    Text("Select All")
                }

                // Extract button in selection mode
                IconButton(
                    onClick = onExtractSelected,
                    enabled = state.selectedCount > 0
                ) {
                    Icon(
                        imageVector = Icons.Default.Archive,
                        contentDescription = "Extract selected"
                    )
                }
            } else {
                // Filter archives only
                val isFilterActive = state?.filterArchivesOnly == true

                // Extract All button (only when browsing archive)
                val isInArchive = state?.let { st ->
                    (st as? FileBrowserUiState.Success)?.let { success ->
                        success.items.any { it is BrowsableItem.ArchiveFileEntry || it is BrowsableItem.ArchiveDirectory }
                    } ?: false
                } ?: false
                if (isInArchive) {
                    IconButton(onClick = onExtractAllVisible) {
                        Icon(
                            imageVector = Icons.Default.Archive,
                            contentDescription = "Extract all visible files"
                        )
                    }
                }

                IconButton(onClick = onToggleFilter) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Filter archives only",
                        tint = if (isFilterActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                // Sort options
                IconButton(onClick = onCycleSortOrder) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = "Sort order"
                    )
                }

                // Refresh
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh"
                    )
                }
            }
        }
    )
}

/**
 * Main content area displaying files or state-specific views.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileBrowserContent(
    uiState: FileBrowserUiState,
    eventBus: ExtractionEventBus,
    isFileSelected: (BrowsableItem) -> Boolean,
    onMoveExtractionToBackground: () -> Unit,
    onFileClick: (BrowsableItem) -> Unit,
    onFileLongClick: (BrowsableItem) -> Unit,
    onScrollPositionChanged: (Int) -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is FileBrowserUiState.Loading -> {
                // Loading indicator removed due to Compose version conflict
                Box(modifier = Modifier.fillMaxSize())
            }

            is FileBrowserUiState.Extracting -> {
                ExtractionScreen(
                    fileName = state.fileName,
                    eventBus = eventBus,
                    onComplete = onMoveExtractionToBackground
                )
            }

            is FileBrowserUiState.Success -> {
                if (state.items.isEmpty()) {
                    EmptyDirectoryView()
                } else {
                    FileList(
                        files = state.items,
                        isSelectionMode = state.isSelectionMode,
                        isFileSelected = isFileSelected,
                        onFileClick = onFileClick,
                        onFileLongClick = onFileLongClick,
                        onScrollPositionChanged = onScrollPositionChanged
                    )
                }
            }

            is FileBrowserUiState.Error -> {
                ErrorView(message = state.message)
            }
        }
    }
}

/**
 * List of files with selection support.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileList(
    files: List<app.otter.domain.model.BrowsableItem>,
    isSelectionMode: Boolean,
    isFileSelected: (app.otter.domain.model.BrowsableItem) -> Boolean,
    onFileClick: (app.otter.domain.model.BrowsableItem) -> Unit,
    onFileLongClick: (app.otter.domain.model.BrowsableItem) -> Unit,
    onScrollPositionChanged: (Int) -> Unit = {},
) {
    val listState = rememberLazyListState()

    // Detect scroll using layoutInfo to get the most accurate visible item.
    // layoutInfo.visibleItemsInfo changes on every scroll frame (unlike firstVisibleItemIndex
    // which can stay stable when cache cleanup shifts the list with stable keys).
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0 }
            .collect { index: Int -> onScrollPositionChanged(index) }
    }

    LazyColumn(state = listState) {
        items(
            items = files,
            key = { file -> file.path.toString() }
        ) { file ->
            BrowsableItemRow(
                item = file,
                isSelectionMode = isSelectionMode,
                isSelected = isFileSelected(file),
                onClick = { onFileClick(file) },
                onLongClick = { onFileLongClick(file) }
            )
        }
    }
}

/**
 * Empty directory placeholder view.
 */
@Composable
private fun EmptyDirectoryView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "No files to display",
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Open an archive file with Otter to browse its contents",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/**
 * Error state view.
 */
@Composable
private fun ErrorView(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Error",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
