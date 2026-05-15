package app.otter.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import timber.log.Timber
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otter.domain.model.FileItem
import app.otter.service.ExtractionEventBus
import app.otter.service.ExtractionService
import app.otter.ui.component.ExtractionScreen
import app.otter.ui.component.FileItemRow
import app.otter.ui.component.ExtractionConfirmDialog
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
    var showConfirmDialog by remember { mutableStateOf(false) }
    var fileToExtract by remember { mutableStateOf<FileItem?>(null) }

    // Navigate to archive's parent directory when opened with "Open with Browser"
    LaunchedEffect(initialArchiveUri) {
        initialArchiveUri?.let { uri ->
            try {
                val archivePath = ResourcePathConverter.fromUri(uri)
                val archiveFileUri = ResourcePathConverter.toUri(archivePath)

                // Navigate to parent directory
                when (archiveFileUri.scheme) {
                    "file" -> {
                        archiveFileUri.path?.let { filePath ->
                            java.io.File(filePath).parent?.let { parentPath ->
                                val parentUri = android.net.Uri.fromFile(java.io.File(parentPath))
                                val parentResourcePath = ResourcePathConverter.fromUri(parentUri)
                                viewModel.navigateToPath(parentResourcePath)
                            }
                        }
                    }
                    "content" -> {
                        // Try to get real path from content URI
                        val realPath = getRealPathFromContentUri(context, archiveFileUri)
                        if (realPath != null) {
                            val parentPath = java.io.File(realPath).parent
                            if (parentPath != null) {
                                val parentUri = android.net.Uri.fromFile(java.io.File(parentPath))
                                val parentResourcePath = ResourcePathConverter.fromUri(parentUri)
                                viewModel.navigateToPath(parentResourcePath)
                                return@let
                            }
                        }

                        // Fallback: navigate to Downloads folder
                        val downloadsPath = android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_DOWNLOADS
                        )
                        val downloadsUri = android.net.Uri.fromFile(downloadsPath)
                        val downloadsResourcePath = ResourcePathConverter.fromUri(downloadsUri)
                        viewModel.navigateToPath(downloadsResourcePath)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to navigate to archive location")
            }
        }
    }

    Scaffold(
        topBar = {
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
                        IconButton(onClick = { viewModel.exitSelectionMode() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Exit selection mode"
                            )
                        }
                    } else {
                        val canNavigateUp = state?.canNavigateUp == true
                        IconButton(
                            onClick = { viewModel.navigateUp() },
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
                        TextButton(
                            onClick = {
                                viewModel.selectAllArchives()
                            }
                        ) {
                            Text("Select All")
                        }

                        // Extract button in selection mode
                        IconButton(
                            onClick = {
                                val selected = viewModel.getSelectedFiles().filter { it.isArchive }
                                if (selected.isNotEmpty()) {

                                    // Take persistent permissions for content:// URIs
                                    selected.forEach { file ->
                                        val uri = ResourcePathConverter.toUri(file.path)
                                        if (uri.scheme == "content") {
                                            try {
                                                context.contentResolver.takePersistableUriPermission(
                                                    uri,
                                                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                )
                                            } catch (e: Exception) {
                                            }
                                        }
                                    }

                                    // Add all to queue
                                    val tasks = selected.map { file ->
                                        app.otter.service.ExtractionQueue.ExtractionTask(
                                            archiveUri = file.path,
                                            fileName = file.name
                                        )
                                    }
                                    viewModel.extractionQueue.enqueueAll(tasks)

                                    // Show extraction UI immediately
                                    viewModel.startExtraction(fileName = "${selected.size} archives")

                                    // Start processing queue
                                    viewModel.extractionQueue.processNext(context)

                                    viewModel.exitSelectionMode()
                                }
                            },
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
                        IconButton(onClick = { viewModel.toggleArchiveFilter() }) {
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

                        // Sort options (cycle through)
                        IconButton(onClick = {
                            val currentOrder = state?.sortOrder ?: SortOrder.ARCHIVES_FIRST
                            val nextOrder = when (currentOrder) {
                                SortOrder.ARCHIVES_FIRST -> SortOrder.NAME_ASC
                                SortOrder.NAME_ASC -> SortOrder.NAME_DESC
                                SortOrder.NAME_DESC -> SortOrder.SIZE_DESC
                                SortOrder.SIZE_DESC -> SortOrder.ARCHIVES_FIRST
                                SortOrder.SIZE_ASC -> SortOrder.ARCHIVES_FIRST
                            }
                            viewModel.setSortOrder(nextOrder)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort order"
                            )
                        }

                        // Refresh
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is FileBrowserUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is FileBrowserUiState.Extracting -> {
                    ExtractionScreen(
                        fileName = state.fileName,
                        eventBus = viewModel.eventBus,
                        onComplete = {
                            viewModel.moveExtractionToBackground()
                        }
                    )
                }

                is FileBrowserUiState.Success -> {
                    if (state.files.isEmpty()) {
                        Text(
                            text = "Empty directory",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn {
                            items(
                                items = state.files,
                                key = { file -> file.path.toString() }
                            ) { file ->
                                FileItemRow(
                                    fileItem = file,
                                    isSelectionMode = state.isSelectionMode,
                                    isSelected = viewModel.isFileSelected(file),
                                    onClick = {
                                        if (state.isSelectionMode) {
                                            viewModel.toggleFileSelection(file)
                                        } else {
                                            if (file.isDirectory) {
                                                viewModel.navigateInto(file)
                                            } else if (file.isArchive) {
                                                // Show confirmation dialog
                                                fileToExtract = file
                                                showConfirmDialog = true
                                            } else {
                                                // Regular files - do nothing for now
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        if (!state.isSelectionMode) {
                                            viewModel.enterSelectionMode()
                                            viewModel.toggleFileSelection(file)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                is FileBrowserUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
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
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Version label in bottom-left corner
            app.otter.ui.component.VersionLabel(
                modifier = Modifier.align(Alignment.BottomStart)
            )

            // Confirmation dialog
            if (showConfirmDialog && fileToExtract != null) {
                ExtractionConfirmDialog(
                    fileItem = fileToExtract!!,
                    onConfirm = {
                        fileToExtract?.let { file ->
                            // Initialize UI to extracting state immediately
                            viewModel.startExtraction(fileName = file.name)

                            val intent = ExtractionService.newIntent(
                                context = context,
                                archiveUri = file.path,
                                fileName = file.name
                            )
                            context.startService(intent)
                        }
                        showConfirmDialog = false
                        fileToExtract = null
                    },
                    onDismiss = {
                        showConfirmDialog = false
                        fileToExtract = null
                    }
                )
            }
        }
    }
}

/**
 * Tries to resolve a real file path from a content:// URI.
 * Returns null if the path cannot be resolved.
 */
private fun getRealPathFromContentUri(context: Context, uri: Uri): String? {
    // Try different DocumentFile approaches
    try {
        // Method 1: Query DATA column (works for some providers)
        context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                if (columnIndex >= 0) {
                    val path = cursor.getString(columnIndex)
                    if (!path.isNullOrBlank() && java.io.File(path).exists()) {
                        return path
                    }
                }
            }
        }

        // Method 2: For DocumentFile URIs (Documents Provider)
        if (android.provider.DocumentsContract.isDocumentUri(context, uri)) {
            val docId = android.provider.DocumentsContract.getDocumentId(uri)

            // ExternalStorageProvider
            if (uri.authority == "com.android.externalstorage.documents") {
                val split = docId.split(":")
                val type = split[0]
                if ("primary".equals(type, ignoreCase = true)) {
                    return "${android.os.Environment.getExternalStorageDirectory()}/${split[1]}"
                }
            }

            // DownloadsProvider
            if (uri.authority == "com.android.providers.downloads.documents") {
                val contentUri = android.content.ContentUris.withAppendedId(
                    Uri.parse("content://downloads/public_downloads"),
                    docId.toLongOrNull() ?: return null
                )
                return getRealPathFromContentUri(context, contentUri)
            }

            // MediaProvider
            if (uri.authority == "com.android.providers.media.documents") {
                val split = docId.split(":")
                val type = split[0]

                val contentUri = when (type) {
                    "image" -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else -> return null
                }

                val selection = "_id=?"
                val selectionArgs = arrayOf(split[1])
                return getRealPathFromContentUri(context, contentUri)
            }
        }
    } catch (e: Exception) {
        Timber.e(e, "Failed to resolve real path from content URI")
    }

    return null
}
