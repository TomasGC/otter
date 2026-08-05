package app.otter.ui.screen

import android.content.Intent
import android.net.Uri
import timber.log.Timber
import app.otter.domain.model.ResourcePath
import app.otter.service.ExtractionQueue
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otter.domain.model.BrowsableItem
import app.otter.service.ExtractionEventBus
import app.otter.ui.component.ExtractionScreen
import app.otter.ui.component.BrowsableItemRow
import app.otter.ui.viewmodel.FileBrowserUiState
import app.otter.ui.viewmodel.FileBrowserViewModel
import app.otter.ui.viewmodel.SortOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import app.otter.data.util.ResourcePathConverter

/**
 * File browser screen for navigating the file system.
 *
 * @param initialArchiveUri Optional URI to an archive file to open immediately
 * @param viewModel ViewModel for managing browser state
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileBrowserScreen(
    initialArchiveUri: Uri? = null,
    viewModel: FileBrowserViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val folderCounts by viewModel.folderCounts.collectAsStateWithLifecycle()

    // Navigate directly into archive when opened with "Open with Browser"
    LaunchedEffect(initialArchiveUri) {
        initialArchiveUri?.let { uri ->
            Timber.d("FileBrowserScreen: initialArchiveUri = $uri")
            try {
                val fileSystemPath = ResourcePathConverter.fromUri(uri, context)
                Timber.d("FileBrowserScreen: Archive path = $fileSystemPath")

                // Convert FileSystem path to ArchiveEntry to browse archive contents
                val archivePath = when (fileSystemPath) {
                    is ResourcePath.FileSystem -> {
                        // Create ArchiveEntry with empty entryPath (root of archive)
                        ResourcePath.ArchiveEntry(
                            archivePath = fileSystemPath.path,
                            entryPath = ""
                        )
                    }
                    else -> fileSystemPath
                }

                Timber.d("FileBrowserScreen: Navigating to archive root = $archivePath")
                viewModel.navigateToPath(archivePath)
            } catch (e: Exception) {
                Timber.e(e, "Failed to browse archive")
            }
        }
    }

    Scaffold(
        topBar = {
            FileBrowserTopAppBar(
                uiState = uiState,
                onExtractAllVisible = {
                    val state = uiState as? FileBrowserUiState.Success
                    val archiveFiles = state?.items?.filterIsInstance<BrowsableItem.ArchiveFile>()
                    if (!archiveFiles.isNullOrEmpty()) {
                        // Add all visible archive files to extraction queue
                        val tasks = archiveFiles.map { file ->
                            ExtractionQueue.ExtractionTask(
                                archiveUri = file.archivePath,
                                fileName = file.name
                            )
                        }
                        viewModel.extractionQueue.enqueueAll(tasks)

                        // Show extraction UI
                        viewModel.startExtraction(fileName = "${archiveFiles.size} files")

                        // Start processing queue
                        viewModel.extractionQueue.processNext(context)
                    }
                },
                onNavigateUp = { viewModel.navigateUp() },
                onExitSelectionMode = { viewModel.exitSelectionMode() },
                onSelectAll = { viewModel.selectAllArchives() },
                onExtractSelected = {
                    val selectedPaths = viewModel.getSelectedPaths()
                        .filterIsInstance<ResourcePath.ArchiveEntry>()

                    if (selectedPaths.isNotEmpty()) {
                        // Group selected entries by their parent archive
                        val byArchive = selectedPaths.groupBy { it.archivePath }

                        val tasks = byArchive.map { (archivePath, entries) ->
                            // If entryPath is empty for all entries, it means they are archive files themselves
                            // → extract full archive (selectedItems = null)
                            // If any has a non-empty entryPath, they are entries inside the archive
                            // → extract selectively
                            val entryPaths = entries.map { it.entryPath }.filter { it.isNotEmpty() }
                            val archiveResourcePath = ResourcePath.ArchiveEntry(archivePath = archivePath)

                            app.otter.service.ExtractionQueue.ExtractionTask(
                                archiveUri = archiveResourcePath,
                                fileName = archivePath.substringAfterLast("/"),
                                selectedItems = entryPaths.ifEmpty { null }
                            )
                        }

                        // Take persistent permissions for content:// URIs
                        tasks.forEach { task ->
                            val uri = ResourcePathConverter.toUri(task.archiveUri)
                            if (uri.scheme == "content") {
                                try {
                                    context.contentResolver.takePersistableUriPermission(
                                        uri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    )
                                } catch (e: Exception) {
                                    // Permission already held or not available
                                }
                            }
                        }

                        viewModel.extractionQueue.enqueueAll(tasks)
                        viewModel.startExtraction(fileName = "${selectedPaths.size} items")
                        viewModel.extractionQueue.processNext(context)
                        viewModel.exitSelectionMode()
                    }
                },
                onToggleFilter = { viewModel.toggleArchiveFilter() },
                onCycleSortOrder = {
                    val state = uiState as? FileBrowserUiState.Success
                    val currentOrder = state?.sortOrder ?: SortOrder.ARCHIVES_FIRST
                    val nextOrder = when (currentOrder) {
                        SortOrder.ARCHIVES_FIRST -> SortOrder.NAME_ASC
                        SortOrder.NAME_ASC -> SortOrder.NAME_DESC
                        SortOrder.NAME_DESC -> SortOrder.SIZE_DESC
                        SortOrder.SIZE_DESC -> SortOrder.ARCHIVES_FIRST
                        SortOrder.SIZE_ASC -> SortOrder.ARCHIVES_FIRST
                    }
                    viewModel.setSortOrder(nextOrder)
                },
                onRefresh = { viewModel.refresh() }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            FileBrowserContent(
                uiState = uiState,
                eventBus = viewModel.eventBus,
                isFileSelected = { file -> viewModel.isFileSelected(file) },
                onMoveExtractionToBackground = { viewModel.moveExtractionToBackground() },
                onScrollPositionChanged = { firstVisibleIndex -> viewModel.onScrollPositionChanged(firstVisibleIndex) },
                folderCounts = folderCounts,
                onFileClick = { item ->
                    val state = uiState as? FileBrowserUiState.Success
                    if (state?.isSelectionMode == true) {
                        viewModel.toggleFileSelection(item)
                    } else {
                        when (item) {
                            is app.otter.domain.model.BrowsableItem.FileSystemDirectory,
                            is app.otter.domain.model.BrowsableItem.ArchiveDirectory,
                            is app.otter.domain.model.BrowsableItem.ArchiveFile -> viewModel.navigateInto(item)
                            else -> {} // FileSystemFile or ArchiveFileEntry - do nothing
                        }
                    }
                },
                onFileLongClick = { file ->
                    val state = uiState as? FileBrowserUiState.Success
                    if (state?.isSelectionMode == false) {
                        viewModel.enterSelectionMode()
                        viewModel.toggleFileSelection(file)
                    }
                }
            )

            // Version label in bottom-left corner
            app.otter.ui.component.VersionLabel(
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
    }
}
