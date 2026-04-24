package app.otter.ui.screen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.runtime.DisposableEffect
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
    // TODO: Handle initialArchiveUri - navigate into archive
    // For Phase 2: implement archive browsing

    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showConfirmDialog by remember { mutableStateOf(false) }
    var fileToExtract by remember { mutableStateOf<FileItem?>(null) }

    // Collect extraction progress from EventBus (injected via ViewModel)
    LaunchedEffect(Unit) {
        try {
            viewModel.eventBus.progressEvents.collect { event ->
                try {
                    viewModel.updateExtractionProgress(
                        fileName = event.fileName,
                        currentFile = event.currentFile,
                        extractedCount = event.extractedCount,
                        totalCount = event.totalCount,
                        progress = event.progress
                    )
                } catch (e: Exception) {
                    Timber.tag("FileBrowserScreen").e(e, "Error updating progress")
                }
            }
        } catch (e: Exception) {
            Timber.tag("FileBrowserScreen").e(e, "Error collecting progress")
        }
    }

    LaunchedEffect(Unit) {
        try {
            viewModel.eventBus.completeEvents.collect {
                try {
                    viewModel.onExtractionComplete()
                } catch (e: Exception) {
                    Timber.tag("FileBrowserScreen").e(e, "Error on extraction complete")
                }
            }
        } catch (e: Exception) {
            Timber.tag("FileBrowserScreen").e(e, "Error collecting complete")
        }
    }

    // Register broadcast receiver for extraction progress (backup)
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ExtractionService.ACTION_EXTRACTION_PROGRESS -> {
                        val fileName = intent.getStringExtra("extra_file_name") ?: ""
                        val currentFile = intent.getStringExtra("extra_current_file") ?: ""
                        val extractedCount = intent.getIntExtra("extra_extracted_count", 0)
                        val totalCount = intent.getIntExtra("extra_total_count", 0)
                        val progress = intent.getFloatExtra("extra_progress", 0f)


                        viewModel.updateExtractionProgress(
                            fileName = fileName,
                            currentFile = currentFile,
                            extractedCount = extractedCount,
                            totalCount = totalCount,
                            progress = progress
                        )
                    }
                    ExtractionService.ACTION_EXTRACTION_COMPLETE -> {
                        viewModel.onExtractionComplete()
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(ExtractionService.ACTION_EXTRACTION_PROGRESS)
            addAction(ExtractionService.ACTION_EXTRACTION_COMPLETE)
        }

        // Register receiver with RECEIVER_NOT_EXPORTED flag (Android 13+)
        // For older versions, ContextCompat handles compatibility
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        onDispose {
            context.unregisterReceiver(receiver)
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
                                    viewModel.updateExtractionProgress(
                                        fileName = "${selected.size} archives",
                                        currentFile = "Starting ${selected.first().name}...",
                                        extractedCount = 0,
                                        totalCount = 0,
                                        progress = 0f
                                    )

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
                    // Log every recomposition

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Extracting ${state.fileName}",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        CircularProgressIndicator(
                            progress = state.progress,
                            modifier = Modifier
                                .size(120.dp)
                                .padding(bottom = 16.dp),
                        )

                        Text(
                            text = "${(state.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Text(
                            text = "${state.extractedCount} / ${state.totalCount} files",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Text(
                            text = state.currentFile,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 32.dp)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    context.startService(ExtractionService.newStopIntent(context))
                                    viewModel.moveExtractionToBackground()
                                }
                            ) {
                                Text("Stop")
                            }

                            TextButton(
                                onClick = {
                                    viewModel.moveExtractionToBackground()
                                }
                            ) {
                                Text("Background")
                            }
                        }
                    }
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

            // Confirmation dialog
            if (showConfirmDialog && fileToExtract != null) {
                AlertDialog(
                    onDismissRequest = {
                        showConfirmDialog = false
                        fileToExtract = null
                    },
                    title = { Text("Extract archive?") },
                    text = {
                        Column {
                            Text("Do you want to extract this archive?")
                            Text(
                                text = fileToExtract!!.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                fileToExtract?.let { file ->

                                    // Initialize UI to extracting state immediately
                                    viewModel.updateExtractionProgress(
                                        fileName = file.name,
                                        currentFile = "Starting...",
                                        extractedCount = 0,
                                        totalCount = 0,
                                        progress = 0f
                                    )

                                    val intent = ExtractionService.newIntent(
                                        context = context,
                                        archiveUri = file.path,
                                        fileName = file.name
                                    )
                                    context.startService(intent)
                                }
                                showConfirmDialog = false
                                fileToExtract = null
                            }
                        ) {
                            Text("Extract")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showConfirmDialog = false
                                fileToExtract = null
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileItemRow(
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
                        text = formatFileSize(fileItem.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = formatDate(fileItem.lastModified),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.1f GB", gb)
}

private fun formatDate(timestamp: Long): String {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}
