package app.otter.ui.viewmodel

import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otter.domain.model.FileItem
import app.otter.domain.usecase.BrowseFilesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Stack
import javax.inject.Inject

/**
 * ViewModel for the file browser screen.
 */
@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    private val browseFilesUseCase: BrowseFilesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<FileBrowserUiState>(FileBrowserUiState.Loading)
    val uiState: StateFlow<FileBrowserUiState> = _uiState.asStateFlow()

    private val navigationStack = Stack<Uri>()
    private var currentUri: Uri = getDefaultStartUri()
    private var allFiles: List<FileItem> = emptyList()
    private var filterArchivesOnly: Boolean = false
    private var sortOrder: SortOrder = SortOrder.ARCHIVES_FIRST
    private var isSelectionMode: Boolean = false
    private val selectedFiles = mutableSetOf<Uri>()
    private var previousSuccessState: FileBrowserUiState.Success? = null

    init {
        navigationStack.push(currentUri)
        browseDirectory(currentUri)
    }

    /**
     * Updates extraction progress in UI.
     */
    fun updateExtractionProgress(
        fileName: String,
        currentFile: String,
        extractedCount: Int,
        totalCount: Int,
        progress: Float
    ) {
        _uiState.value = FileBrowserUiState.Extracting(
            fileName = fileName,
            currentFile = currentFile,
            extractedCount = extractedCount,
            totalCount = totalCount,
            progress = progress
        )
    }

    /**
     * Moves extraction to background and returns to file browser.
     */
    fun moveExtractionToBackground() {
        previousSuccessState?.let {
            _uiState.value = it
        } ?: refresh()
    }

    /**
     * Extraction completed, refresh directory.
     */
    fun onExtractionComplete() {
        refresh()
    }

    /**
     * Toggles archive-only filter.
     */
    fun toggleArchiveFilter() {
        filterArchivesOnly = !filterArchivesOnly
        applyFilterAndSort()
    }

    /**
     * Changes sort order.
     */
    fun setSortOrder(order: SortOrder) {
        sortOrder = order
        applyFilterAndSort()
    }

    /**
     * Navigates into a directory.
     */
    fun navigateInto(fileItem: FileItem) {
        if (!fileItem.isDirectory) return

        navigationStack.push(fileItem.uri)
        currentUri = fileItem.uri
        browseDirectory(currentUri)
    }

    /**
     * Navigates up to the parent directory.
     */
    fun navigateUp() {
        if (navigationStack.size <= 1) return

        navigationStack.pop()
        currentUri = navigationStack.peek()
        browseDirectory(currentUri)
    }

    /**
     * Checks if navigation up is possible.
     */
    fun canNavigateUp(): Boolean = navigationStack.size > 1

    /**
     * Refreshes the current directory.
     */
    fun refresh() {
        browseDirectory(currentUri)
    }

    /**
     * Enters selection mode.
     */
    fun enterSelectionMode() {
        isSelectionMode = true
        selectedFiles.clear()
        applyFilterAndSort()
    }

    /**
     * Exits selection mode.
     */
    fun exitSelectionMode() {
        isSelectionMode = false
        selectedFiles.clear()
        applyFilterAndSort()
    }

    /**
     * Toggles selection for a file.
     */
    fun toggleFileSelection(fileItem: FileItem) {
        if (selectedFiles.contains(fileItem.uri)) {
            selectedFiles.remove(fileItem.uri)
        } else {
            selectedFiles.add(fileItem.uri)
        }
        applyFilterAndSort()
    }

    /**
     * Checks if a file is selected.
     */
    fun isFileSelected(fileItem: FileItem): Boolean {
        return selectedFiles.contains(fileItem.uri)
    }

    /**
     * Gets the list of selected files.
     */
    fun getSelectedFiles(): List<FileItem> {
        return allFiles.filter { selectedFiles.contains(it.uri) }
    }

    private fun browseDirectory(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = FileBrowserUiState.Loading

            browseFilesUseCase(uri)
                .onSuccess { files ->
                    allFiles = files
                    applyFilterAndSort()
                }
                .onFailure { error ->
                    _uiState.value = FileBrowserUiState.Error(
                        message = error.message ?: "Failed to browse directory"
                    )
                }
        }
    }

    private fun applyFilterAndSort() {
        val filtered = if (filterArchivesOnly) {
            allFiles.filter { it.isArchive }
        } else {
            allFiles
        }

        val sorted = when (sortOrder) {
            SortOrder.ARCHIVES_FIRST -> filtered.sortedWith(
                compareBy<FileItem> { !it.isArchive }
                    .thenBy { !it.isDirectory }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
            SortOrder.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            SortOrder.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
            SortOrder.SIZE_ASC -> filtered.sortedBy { it.sizeBytes ?: Long.MAX_VALUE }
            SortOrder.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes ?: 0 }
        }

        val successState = FileBrowserUiState.Success(
            files = sorted,
            currentPath = getCurrentPathDisplay(currentUri),
            canNavigateUp = canNavigateUp(),
            filterArchivesOnly = filterArchivesOnly,
            sortOrder = sortOrder,
            isSelectionMode = isSelectionMode,
            selectedCount = selectedFiles.size
        )
        previousSuccessState = successState
        _uiState.value = successState
    }

    private fun getCurrentPathDisplay(uri: Uri): String {
        return when (uri.scheme) {
            "file" -> {
                val path = uri.path ?: "/"
                if (path == Environment.getExternalStorageDirectory().path) {
                    "Internal Storage"
                } else {
                    path
                }
            }
            "content" -> uri.lastPathSegment ?: "Storage"
            else -> uri.toString()
        }
    }

    private fun getDefaultStartUri(): Uri {
        return Uri.fromFile(Environment.getExternalStorageDirectory())
    }
}

/**
 * UI state for the file browser screen.
 */
sealed class FileBrowserUiState {
    data object Loading : FileBrowserUiState()

    data class Success(
        val files: List<FileItem>,
        val currentPath: String,
        val canNavigateUp: Boolean,
        val filterArchivesOnly: Boolean = false,
        val sortOrder: SortOrder = SortOrder.ARCHIVES_FIRST,
        val isSelectionMode: Boolean = false,
        val selectedCount: Int = 0,
    ) : FileBrowserUiState()

    data class Extracting(
        val fileName: String,
        val currentFile: String,
        val extractedCount: Int,
        val totalCount: Int,
        val progress: Float,
    ) : FileBrowserUiState()

    data class Error(
        val message: String,
    ) : FileBrowserUiState()
}

/**
 * Sort order options for file list.
 */
enum class SortOrder {
    ARCHIVES_FIRST,  // Archives → Directories → Files (alphabetical)
    NAME_ASC,        // A → Z
    NAME_DESC,       // Z → A
    SIZE_ASC,        // Smallest → Largest
    SIZE_DESC,       // Largest → Smallest
}
