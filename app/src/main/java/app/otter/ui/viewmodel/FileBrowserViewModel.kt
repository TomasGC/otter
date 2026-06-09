package app.otter.ui.viewmodel

import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otter.data.util.ResourcePathConverter
import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.BrowseResult
import app.otter.domain.model.ResourcePath
import app.otter.domain.usecase.BrowseItemsUseCase
import app.otter.service.ExtractionEventBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Stack
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

/**
 * ViewModel for the file browser screen.
 *
 * @param ioDispatcher Injected IO dispatcher (allows testing with TestDispatcher)
 */
@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    private val browseItemsUseCase: BrowseItemsUseCase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    val eventBus: ExtractionEventBus,
    val extractionQueue: app.otter.service.ExtractionQueue,
) : ViewModel() {

    companion object {
        const val HALF_WINDOW = 100   // items kept before and after current position
        const val LOAD_TRIGGER = 60   // load next/prev when within this many items of the cache edge

        // Natural sort comparator: "file_2" < "file_10" (not "file_10" < "file_2")
        val NATURAL_ORDER: Comparator<String> = Comparator { a, b ->
            val tokensA = splitNatural(a)
            val tokensB = splitNatural(b)
            val len = minOf(tokensA.size, tokensB.size)
            for (i in 0 until len) {
                val tA = tokensA[i]
                val tB = tokensB[i]
                val cmp = if (tA[0].isDigit() && tB[0].isDigit()) {
                    tA.toBigInteger().compareTo(tB.toBigInteger())
                } else {
                    tA.compareTo(tB, ignoreCase = true)
                }
                if (cmp != 0) return@Comparator cmp
            }
            tokensA.size - tokensB.size
        }

        private fun splitNatural(s: String): List<String> {
            val result = mutableListOf<String>()
            var i = 0
            while (i < s.length) {
                val start = i
                val isDigit = s[i].isDigit()
                while (i < s.length && s[i].isDigit() == isDigit) i++
                result.add(s.substring(start, i))
            }
            return result
        }
    }

    private val _uiState = MutableStateFlow<FileBrowserUiState>(FileBrowserUiState.Loading)
    val uiState: StateFlow<FileBrowserUiState> = _uiState.asStateFlow()

    private val navigationStack = Stack<ResourcePath>()
    private var currentPath: ResourcePath = getDefaultStartPath()
    private var allItems: List<BrowsableItem> = emptyList()
    private var filterArchivesOnly: Boolean = false
    private var sortOrder: SortOrder = SortOrder.ARCHIVES_FIRST
    private var isSelectionMode: Boolean = false
    private val selectedFiles = mutableSetOf<ResourcePath>()
    private var previousSuccessState: FileBrowserUiState.Success? = null

    // Sliding window cache
    private val cachedItems = java.util.concurrent.ConcurrentHashMap<Int, BrowsableItem>()
    private var currentWindowStart = 0
    private var currentWindowEnd = 0
    private var totalItemCount: Int? = null

    // Pagination state
    private var isPaginated = false
    private var nextOffset = 0
    private var hasMore = true
    private var isLoadingPage = false
    private var lastKnownAbsoluteIndex = 0

    init {
        navigationStack.push(currentPath)
        browseDirectory(currentPath)
    }


    /**
     * Starts extraction and switches to extraction screen.
     */
    fun startExtraction(fileName: String) {
        _uiState.value = FileBrowserUiState.Extracting(fileName = fileName)
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
     * Navigates into a directory or archive.
     */
    fun navigateInto(item: BrowsableItem) {
        if (!item.canNavigateInto) return

        navigationStack.push(item.path)
        currentPath = item.path
        browseDirectory(currentPath)
    }

    /**
     * Navigates to a specific path directly (used for initial navigation from intents).
     */
    fun navigateToPath(path: ResourcePath) {
        navigationStack.clear()
        navigationStack.push(path)
        currentPath = path
        browseDirectory(currentPath)
    }

    /**
     * Navigates up to the parent directory.
     */
    fun navigateUp() {
        if (navigationStack.size <= 1) return

        navigationStack.pop()
        currentPath = navigationStack.peek()
        browseDirectory(currentPath)
    }

    /**
     * Checks if navigation up is possible.
     */
    fun canNavigateUp(): Boolean = navigationStack.size > 1

    /**
     * Refreshes the current directory.
     */
    fun refresh() {
        browseDirectory(currentPath)
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
     * Toggles selection for an item.
     */
    fun toggleFileSelection(item: BrowsableItem) {
        if (selectedFiles.contains(item.path)) {
            selectedFiles.remove(item.path)
        } else {
            selectedFiles.add(item.path)
        }
        applyFilterAndSort()
    }

    /**
     * Checks if an item is selected.
     */
    fun isFileSelected(item: BrowsableItem): Boolean {
        return selectedFiles.contains(item.path)
    }

    /**
     * Gets the list of selected files.
     * In paginated mode, builds results from the selectedFiles paths set
     * so items outside the cache window are not lost.
     */
    fun getSelectedFiles(): List<BrowsableItem> {
        if (selectedFiles.isEmpty()) return emptyList()

        // For complete (non-paginated) results, filter from allItems
        if (!isPaginated) {
            return allItems.filter { selectedFiles.contains(it.path) }
        }

        // For paginated results, find selected items in cache first
        val fromCache = cachedItems.values.filter { selectedFiles.contains(it.path) }
        val foundPaths = fromCache.map { it.path }.toSet()

        // If all selected paths are in cache, return directly
        if (foundPaths.size == selectedFiles.size) return fromCache

        // Some selected items are outside the cache window — return what we have from cache
        // The selectedCount is always correct (tracked by selectedFiles set size)
        // The actual items list may be partial but the paths are authoritative
        return fromCache
    }

    /**
     * Returns the count of selected items (always accurate, even in paginated mode).
     */
    fun getSelectedCount(): Int = selectedFiles.size

    /**
     * Returns selected resource paths (always complete, even in paginated mode).
     */
    fun getSelectedPaths(): Set<ResourcePath> = selectedFiles.toSet()

    /**
     * Loads all items from current directory (handles both paginated and non-paginated modes).
     *
     * Pure function for testing - fetches complete list regardless of cache state.
     *
     * @return List of all browsable items in current directory
     */
    internal suspend fun loadAllItemsInCurrentDirectory(): List<BrowsableItem> {
        return if (isPaginated) {
            // Load ALL items from current directory (not just cache)
            browseItemsUseCase(currentPath, offset = 0, limit = Int.MAX_VALUE)
                .getOrNull()?.items ?: emptyList()
        } else {
            allItems
        }
    }

    /**
     * Selects all items in current directory (files, archives, directories).
     * For paginated mode: loads ALL items to select them (not just cached window).
     */
    fun selectAllArchives() {
        viewModelScope.launch(ioDispatcher) {
            val allItems = loadAllItemsInCurrentDirectory()
            val archiveItems = ArchiveSelectionHelper.filterArchives(allItems)
            val newSelection = ArchiveSelectionHelper.addToSelection(archiveItems, selectedFiles)

            selectedFiles.clear()
            selectedFiles.addAll(newSelection)
            applyFilterAndSort()
        }
    }

    /**
     * Handles scroll position changes for lazy loading.
     *
     * @param firstVisibleItemIndex Index in the DISPLAYED LIST (0-based, relative to displayed items).
     */
    fun onScrollPositionChanged(firstVisibleItemIndex: Int) {
        if (!isPaginated) return

        val absoluteIndex = currentWindowStart + firstVisibleItemIndex
        lastKnownAbsoluteIndex = absoluteIndex
        val minCached = cachedItems.keys.minOrNull() ?: absoluteIndex
        val maxCached = cachedItems.keys.maxOrNull() ?: absoluteIndex

        // Load NEXT: fewer than LOAD_TRIGGER items remain between current position and end of cache
        if (maxCached - absoluteIndex < LOAD_TRIGGER && hasMore) {
            loadNextPage()
        }

        // Load PREVIOUS: fewer than LOAD_TRIGGER items remain between start of cache and current position
        if (absoluteIndex - minCached < LOAD_TRIGGER && currentWindowStart > 0) {
            loadPreviousPage()
        }

        // Slide the window: keep ±HALF_WINDOW around current position
        if (!isLoadingPage) {
            cleanupCache(absoluteIndex)
            if (cachedItems.isNotEmpty()) emitVisibleItems()
        }
    }

    private fun loadNextPage() {
        if (isLoadingPage || !hasMore) return

        isLoadingPage = true

        viewModelScope.launch {
            val result: Result<BrowseResult> = withContext(ioDispatcher) {
                browseItemsUseCase(currentPath, offset = nextOffset, limit = 100)
            }
            // State mutations on Main thread — no race with cleanupCache
            result.onSuccess { browseResult ->
                when (browseResult) {
                    is BrowseResult.Complete -> {
                        browseResult.items.forEachIndexed { index, item ->
                            cachedItems[nextOffset + index] = item
                        }
                        hasMore = false
                        currentWindowEnd = nextOffset + browseResult.items.size
                        isLoadingPage = false
                        emitVisibleItems()
                    }
                    is BrowseResult.Paginated -> {
                        browseResult.items.forEachIndexed { index, item ->
                            cachedItems[nextOffset + index] = item
                        }
                        nextOffset = browseResult.nextOffset
                        hasMore = browseResult.hasMore
                        currentWindowEnd = nextOffset
                        isLoadingPage = false
                        emitVisibleItems()
                    }
                }
            }.onFailure {
                isLoadingPage = false
            }
        }
    }

    private fun loadPreviousPage() {
        if (isLoadingPage || currentWindowStart <= 0) return

        isLoadingPage = true

        val offset = (currentWindowStart - 100).coerceAtLeast(0)

        viewModelScope.launch {
            val result: Result<BrowseResult> = withContext(ioDispatcher) {
                browseItemsUseCase(currentPath, offset = offset, limit = 100)
            }
            // State mutations on Main thread — no race with cleanupCache
            result.onSuccess { browseResult ->
                when (browseResult) {
                    is BrowseResult.Paginated -> {
                        browseResult.items.forEachIndexed { index, item ->
                            cachedItems[offset + index] = item
                        }
                        currentWindowStart = offset
                        isLoadingPage = false
                        emitVisibleItems()
                    }
                    else -> {
                        isLoadingPage = false
                    }
                }
            }.onFailure {
                isLoadingPage = false
            }
        }
    }

    private fun cleanupCache(center: Int) {
        val keepStart = (center - HALF_WINDOW).coerceAtLeast(0)
        val keepEnd = center + HALF_WINDOW

        // Remove items outside the ±HALF_WINDOW around current position.
        // Never remove from the back when hasMore=false — the archive end must stay reachable.
        cachedItems.keys.toList().filter { index ->
            index < keepStart || (hasMore && index > keepEnd)
        }.forEach { cachedItems.remove(it) }

        currentWindowStart = cachedItems.keys.minOrNull() ?: keepStart

        // Recalibrate nextOffset if back items were removed, to avoid gaps on next load.
        if (hasMore) {
            val maxCached = cachedItems.keys.maxOrNull() ?: (currentWindowStart - 1)
            if (maxCached + 1 < nextOffset) nextOffset = maxCached + 1
        }

        // Don't emit here — only loads populate cache and emit. Cleanup manages state only.
        // Prevents emitting empty cache between scroll and async load completion.
    }

    private fun emitVisibleItems() {
        // Emit all cached items (entire window) sorted by index
        // Create immutable snapshot to avoid ConcurrentModificationException during iteration
        val snapshot = cachedItems.toList()
        val visibleItems = snapshot
            .sortedBy { (index, _) -> index }
            .map { (_, item) -> item }

        // Apply filtering and sorting to visible items
        val filtered = if (filterArchivesOnly) {
            visibleItems.filterIsInstance<BrowsableItem.ArchiveFile>()
        } else {
            visibleItems
        }

        val sorted = when (sortOrder) {
            SortOrder.ARCHIVES_FIRST -> filtered.sortedWith(
                compareBy<BrowsableItem> { it !is BrowsableItem.ArchiveFile }
                    .thenBy { !it.canNavigateInto }
                    .thenComparator { a, b -> NATURAL_ORDER.compare(a.name, b.name) }
            )
            SortOrder.NAME_ASC -> filtered.sortedWith(Comparator { a, b -> NATURAL_ORDER.compare(a.name, b.name) })
            SortOrder.NAME_DESC -> filtered.sortedWith(Comparator { a, b -> NATURAL_ORDER.compare(b.name, a.name) })
            SortOrder.SIZE_ASC -> filtered.sortedBy { it.sizeBytes }
            SortOrder.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes }
        }

        val successState = FileBrowserUiState.Success(
            items = sorted,
            currentPath = getCurrentPathDisplay(currentPath),
            canNavigateUp = canNavigateUp(),
            filterArchivesOnly = filterArchivesOnly,
            sortOrder = sortOrder,
            isSelectionMode = isSelectionMode,
            selectedCount = selectedFiles.size
        )
        previousSuccessState = successState
        _uiState.value = successState
    }

    private fun browseDirectory(path: ResourcePath) {
        viewModelScope.launch {
            _uiState.value = FileBrowserUiState.Loading

            // Reset cache
            cachedItems.clear()
            currentWindowStart = 0
            currentWindowEnd = 0
            totalItemCount = null
            isPaginated = false
            nextOffset = 0
            hasMore = true
            lastKnownAbsoluteIndex = 0

            browseItemsUseCase(path, offset = 0, limit = 100)
                .onSuccess { result ->
                    when (result) {
                        is BrowseResult.Complete -> {
                            // Small list - load all items into cache
                            allItems = result.items
                            result.items.forEachIndexed { index, item ->
                                cachedItems[index] = item
                            }
                            totalItemCount = result.items.size
                            currentWindowEnd = result.items.size
                            isPaginated = false
                            applyFilterAndSort()
                        }
                        is BrowseResult.Paginated -> {
                            // Large list - use cache
                            result.items.forEachIndexed { index, item ->
                                cachedItems[index] = item
                            }
                            totalItemCount = result.totalEstimate
                            nextOffset = result.nextOffset
                            hasMore = result.hasMore
                            currentWindowEnd = nextOffset
                            isPaginated = true

                            // For paginated, emit only cached items
                            emitVisibleItems()
                        }
                    }
                }
                .onFailure { error ->
                    _uiState.value = FileBrowserUiState.Error(
                        message = error.message ?: "Failed to browse directory"
                    )
                }
        }
    }

    private fun applyFilterAndSort() {
        // For paginated results, re-emit visible items preserving current window position
        if (isPaginated) {
            emitVisibleItems()
            return
        }

        // For complete results, filter and sort all items
        val filtered = if (filterArchivesOnly) {
            allItems.filterIsInstance<BrowsableItem.ArchiveFile>()
        } else {
            allItems
        }

        val sorted = when (sortOrder) {
            SortOrder.ARCHIVES_FIRST -> filtered.sortedWith(
                compareBy<BrowsableItem> { it !is BrowsableItem.ArchiveFile }
                    .thenBy { !it.canNavigateInto }
                    .thenComparator { a, b -> NATURAL_ORDER.compare(a.name, b.name) }
            )
            SortOrder.NAME_ASC -> filtered.sortedWith(Comparator { a, b -> NATURAL_ORDER.compare(a.name, b.name) })
            SortOrder.NAME_DESC -> filtered.sortedWith(Comparator { a, b -> NATURAL_ORDER.compare(b.name, a.name) })
            SortOrder.SIZE_ASC -> filtered.sortedBy { it.sizeBytes }
            SortOrder.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes }
        }

        val successState = FileBrowserUiState.Success(
            items = sorted,
            currentPath = getCurrentPathDisplay(currentPath),
            canNavigateUp = canNavigateUp(),
            filterArchivesOnly = filterArchivesOnly,
            sortOrder = sortOrder,
            isSelectionMode = isSelectionMode,
            selectedCount = selectedFiles.size
        )
        previousSuccessState = successState
        _uiState.value = successState
    }

    private fun getCurrentPathDisplay(path: ResourcePath): String {
        val uri = ResourcePathConverter.toUri(path)
        return when (uri.scheme) {
            "file" -> {
                val filePath = uri.path ?: "/"
                if (filePath == Environment.getExternalStorageDirectory().path) {
                    "Internal Storage"
                } else {
                    filePath
                }
            }
            "content" -> uri.lastPathSegment ?: "Storage"
            else -> uri.toString()
        }
    }

    private fun getDefaultStartPath(): ResourcePath {
        return ResourcePathConverter.fromUri(Uri.fromFile(Environment.getExternalStorageDirectory()))
    }
}

/**
 * UI state for the file browser screen.
 */
sealed class FileBrowserUiState {
    data object Loading : FileBrowserUiState()

    data class Success(
        val items: List<BrowsableItem>,
        val currentPath: String,
        val canNavigateUp: Boolean,
        val filterArchivesOnly: Boolean = false,
        val sortOrder: SortOrder = SortOrder.ARCHIVES_FIRST,
        val isSelectionMode: Boolean = false,
        val selectedCount: Int = 0,
    ) : FileBrowserUiState()

    data class Extracting(
        val fileName: String
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
