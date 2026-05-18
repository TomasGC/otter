package app.otter.ui.screen

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
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
import app.otter.domain.model.FileItem
import app.otter.service.ExtractionEventBus
import app.otter.ui.component.ExtractionScreen
import app.otter.ui.component.FileItemRow
import app.otter.ui.viewmodel.FileBrowserUiState
import app.otter.ui.viewmodel.SortOrder

/**
 * Top app bar for file browser with selection mode support.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserTopAppBar(
    uiState: FileBrowserUiState,
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
    isFileSelected: (FileItem) -> Boolean,
    onMoveExtractionToBackground: () -> Unit,
    onFileClick: (FileItem) -> Unit,
    onFileLongClick: (FileItem) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is FileBrowserUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            is FileBrowserUiState.Extracting -> {
                ExtractionScreen(
                    fileName = state.fileName,
                    eventBus = eventBus,
                    onComplete = onMoveExtractionToBackground
                )
            }

            is FileBrowserUiState.Success -> {
                if (state.files.isEmpty()) {
                    EmptyDirectoryView()
                } else {
                    FileList(
                        files = state.files,
                        isSelectionMode = state.isSelectionMode,
                        isFileSelected = isFileSelected,
                        onFileClick = onFileClick,
                        onFileLongClick = onFileLongClick
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
    files: List<FileItem>,
    isSelectionMode: Boolean,
    isFileSelected: (FileItem) -> Boolean,
    onFileClick: (FileItem) -> Unit,
    onFileLongClick: (FileItem) -> Unit,
) {
    LazyColumn {
        items(
            items = files,
            key = { file -> file.path.toString() }
        ) { file ->
            FileItemRow(
                fileItem = file,
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
        Text(
            text = "Empty directory",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
