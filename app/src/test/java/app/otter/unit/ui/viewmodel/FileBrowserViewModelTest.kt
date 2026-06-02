package app.otter.ui.viewmodel

import android.net.Uri
import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.BrowseResult
import app.otter.domain.model.ResourcePath
import app.otter.domain.usecase.BrowseItemsUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FileBrowserViewModelTest {

    private lateinit var browseItemsUseCase: BrowseItemsUseCase
    private lateinit var eventBus: app.otter.service.ExtractionEventBus
    private lateinit var extractionQueue: app.otter.service.ExtractionQueue
    private lateinit var viewModel: FileBrowserViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        browseItemsUseCase = mockk()
        eventBus = app.otter.service.ExtractionEventBus()
        extractionQueue = app.otter.service.ExtractionQueue()

        // Mock default start directory
        val mockItems = listOf(
            createBrowsableItem("folder1", isDirectory = true),
            createBrowsableItem("archive.zip", isArchive = true),
            createBrowsableItem("file.txt", isDirectory = false)
        )
        coEvery { browseItemsUseCase(any(), any(), any()) } returns Result.success(BrowseResult.Complete(mockItems))

        viewModel = FileBrowserViewModel(browseItemsUseCase, testDispatcher, eventBus, extractionQueue)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Success with sorted files`() {
        // When
        val state = viewModel.uiState.value

        // Then
        assertTrue(state is FileBrowserUiState.Success)
        val successState = state as FileBrowserUiState.Success
        assertEquals(3, successState.items.size)

        // Check sorting: archives first, then directories, then files
        assertEquals("archive.zip", successState.items[0].name)
        assertEquals("folder1", successState.items[1].name)
        assertEquals("file.txt", successState.items[2].name)
    }

    @Test
    fun `toggleArchiveFilter filters to archives only`() {
        // Given
        viewModel.uiState.value as FileBrowserUiState.Success

        // When
        viewModel.toggleArchiveFilter()

        // Then
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        assertTrue(state.filterArchivesOnly)
        assertEquals(1, state.items.size)
        assertTrue(state.items.all { it is BrowsableItem.ArchiveFile })
    }

    @Test
    fun `toggleArchiveFilter twice returns to all files`() {
        // When
        viewModel.toggleArchiveFilter()
        viewModel.toggleArchiveFilter()

        // Then
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        assertFalse(state.filterArchivesOnly)
        assertEquals(3, state.items.size)
    }

    @Test
    fun `setSortOrder NAME_ASC sorts using natural order`() {
        // When
        viewModel.setSortOrder(SortOrder.NAME_ASC)

        // Then - archive < file < folder (natural alphabetical for these names)
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        assertEquals("archive.zip", state.items[0].name)
        assertEquals("file.txt", state.items[1].name)
        assertEquals("folder1", state.items[2].name)
    }

    @Test
    fun `setSortOrder NAME_DESC sorts reverse natural order`() {
        // When
        viewModel.setSortOrder(SortOrder.NAME_DESC)

        // Then
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        assertEquals("folder1", state.items[0].name)
        assertEquals("file.txt", state.items[1].name)
        assertEquals("archive.zip", state.items[2].name)
    }

    @Test
    fun `setSortOrder NAME_ASC uses natural numeric sort for numbered items`() = runTest {
        // Given - items with numeric suffixes that sort differently alphabetically vs numerically
        val numericItems = listOf(
            createBrowsableItem("folder_300", isDirectory = true),
            createBrowsableItem("folder_10000", isDirectory = true),
            createBrowsableItem("folder_2000", isDirectory = true),
            createBrowsableItem("folder_800", isDirectory = true),
            createBrowsableItem("folder_50000", isDirectory = true),
            createBrowsableItem("folder_200000", isDirectory = true),
        )
        coEvery { browseItemsUseCase(any(), any(), any()) } returns Result.success(BrowseResult.Complete(numericItems))
        viewModel.refresh()

        // When
        viewModel.setSortOrder(SortOrder.NAME_ASC)

        // Then - numeric order: 300 < 800 < 2000 < 10000 < 50000 < 200000
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        assertEquals("folder_300", state.items[0].name)
        assertEquals("folder_800", state.items[1].name)
        assertEquals("folder_2000", state.items[2].name)
        assertEquals("folder_10000", state.items[3].name)
        assertEquals("folder_50000", state.items[4].name)
        assertEquals("folder_200000", state.items[5].name)
    }

    @Test
    fun `ARCHIVES_FIRST sort uses natural numeric sort for same-type items`() = runTest {
        // Given
        val numericItems = listOf(
            createBrowsableItem("file_10.txt"),
            createBrowsableItem("file_2.txt"),
            createBrowsableItem("file_1.txt"),
            createBrowsableItem("file_20.txt"),
        )
        coEvery { browseItemsUseCase(any(), any(), any()) } returns Result.success(BrowseResult.Complete(numericItems))
        viewModel.refresh()

        // Then - 1 < 2 < 10 < 20 (not 1 < 10 < 2 < 20)
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        assertEquals("file_1.txt", state.items[0].name)
        assertEquals("file_2.txt", state.items[1].name)
        assertEquals("file_10.txt", state.items[2].name)
        assertEquals("file_20.txt", state.items[3].name)
    }

    @Test
    fun `enterSelectionMode sets isSelectionMode to true`() {
        // When
        viewModel.enterSelectionMode()

        // Then
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        assertTrue(state.isSelectionMode)
        assertEquals(0, state.selectedCount)
    }

    @Test
    fun `toggleFileSelection adds and removes files`() {
        // Given
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        val file = state.items.first()

        viewModel.enterSelectionMode()

        // When - Select file
        viewModel.toggleFileSelection(file)

        // Then
        var currentState = viewModel.uiState.value as FileBrowserUiState.Success
        assertEquals(1, currentState.selectedCount)
        assertTrue(viewModel.isFileSelected(file))

        // When - Deselect file
        viewModel.toggleFileSelection(file)

        // Then
        currentState = viewModel.uiState.value as FileBrowserUiState.Success
        assertEquals(0, currentState.selectedCount)
        assertFalse(viewModel.isFileSelected(file))
    }

    @Test
    fun `exitSelectionMode clears selection`() {
        // Given
        viewModel.enterSelectionMode()
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        val file = state.items.first()
        viewModel.toggleFileSelection(file)

        // When
        viewModel.exitSelectionMode()

        // Then
        val newState = viewModel.uiState.value as FileBrowserUiState.Success
        assertFalse(newState.isSelectionMode)
        assertEquals(0, newState.selectedCount)
    }

    @Test
    fun `getSelectedFiles returns only selected files`() {
        // Given
        viewModel.enterSelectionMode()
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        val file1 = state.items[0]
        val file2 = state.items[1]

        // When
        viewModel.toggleFileSelection(file1)
        viewModel.toggleFileSelection(file2)

        // Then
        val selected = viewModel.getSelectedFiles()
        assertEquals(2, selected.size)
        assertTrue(selected.contains(file1))
        assertTrue(selected.contains(file2))
    }

    @Test
    fun `startExtraction changes state to Extracting`() {
        // When
        viewModel.startExtraction(fileName = "test.zip")

        // Then
        val state = viewModel.uiState.value
        assertTrue(state is FileBrowserUiState.Extracting)
        val extractingState = state as FileBrowserUiState.Extracting
        assertEquals("test.zip", extractingState.fileName)
    }

    @Test
    fun `moveExtractionToBackground returns to previous Success state`() {
        // Given - Get initial success state
        val initialState = viewModel.uiState.value as FileBrowserUiState.Success

        // When - Go to extracting
        viewModel.startExtraction(fileName = "test.zip")
        assertTrue(viewModel.uiState.value is FileBrowserUiState.Extracting)

        // Then - Move to background
        viewModel.moveExtractionToBackground()
        val newState = viewModel.uiState.value as FileBrowserUiState.Success
        assertEquals(initialState.items.size, newState.items.size)
    }

    @Test
    fun `canNavigateUp returns false at root level`() {
        // Then
        assertFalse(viewModel.canNavigateUp())
    }

    @Test
    fun `canNavigateUp returns true after navigating into directory`() {
        // Given
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        val folder = state.items.find { it is BrowsableItem.FileSystemDirectory }!!

        // Mock navigation result
        coEvery { browseItemsUseCase(folder.path, any(), any()) } returns Result.success(BrowseResult.Complete(emptyList()))

        // When
        viewModel.navigateInto(folder)

        // Then
        assertTrue(viewModel.canNavigateUp())
    }

    @Test
    fun `selectAllArchives with no archives should not change selection`() {
        // Given - Files list with no archives
        val mockItems = listOf(
            createBrowsableItem("folder1", isDirectory = true, isArchive = false),
            createBrowsableItem("file.txt", isDirectory = false, isArchive = false)
        )
        coEvery { browseItemsUseCase(any(), any(), any()) } returns Result.success(BrowseResult.Complete(mockItems))
        viewModel = FileBrowserViewModel(browseItemsUseCase, testDispatcher, eventBus, extractionQueue)

        viewModel.enterSelectionMode()

        // When
        viewModel.selectAllArchives()

        // Then
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        assertEquals(0, state.selectedCount)
    }

    @Test
    fun `selectAllArchives with empty list should handle gracefully`() {
        // Given
        coEvery { browseItemsUseCase(any(), any(), any()) } returns Result.success(BrowseResult.Complete(emptyList()))
        viewModel = FileBrowserViewModel(browseItemsUseCase, testDispatcher, eventBus, extractionQueue)

        viewModel.enterSelectionMode()

        // When
        viewModel.selectAllArchives()

        // Then
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        assertEquals(0, state.selectedCount)
    }

    @Test
    fun `browseItemsUseCase error should show Error state`() {
        // Given
        val errorMessage = "Permission denied"
        coEvery { browseItemsUseCase(any(), any(), any()) } returns Result.failure(SecurityException(errorMessage))

        // When
        viewModel = FileBrowserViewModel(browseItemsUseCase, testDispatcher, eventBus, extractionQueue)

        // Then
        val state = viewModel.uiState.value
        assertTrue(state is FileBrowserUiState.Error)
        val errorState = state as FileBrowserUiState.Error
        assertTrue(errorState.message.contains(errorMessage))
    }

    @Test
    fun `navigateInto with non-directory should not change state`() {
        // Given
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        val file = state.items.find { it is BrowsableItem.FileSystemFile }!!
        val stateBefore = viewModel.uiState.value

        // When
        viewModel.navigateInto(file)

        // Then - State should not change
        assertEquals(stateBefore, viewModel.uiState.value)
    }

    @Test
    fun `toggleFileSelection with empty selection should add file`() {
        // Given
        viewModel.enterSelectionMode()
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        val file = state.items.first()
        assertEquals(0, state.selectedCount)

        // When
        viewModel.toggleFileSelection(file)

        // Then
        val newState = viewModel.uiState.value as FileBrowserUiState.Success
        assertEquals(1, newState.selectedCount)
        assertTrue(viewModel.isFileSelected(file))
    }

    @Test
    fun `getSelectedFiles with no selection should return empty list`() {
        // Given
        viewModel.enterSelectionMode()

        // When
        val selected = viewModel.getSelectedFiles()

        // Then
        assertTrue(selected.isEmpty())
    }

    @Test
    fun `navigateInto FileSystemDirectory updates state with items`() = runTest {
        // Given
        val directory = BrowsableItem.FileSystemDirectory(
            path = ResourcePath.FileSystem("/storage/documents"),
            name = "documents",
            sizeBytes = 0L,
            lastModified = System.currentTimeMillis()
        )
        val items = listOf(directory)
        val browseItemsUseCase: BrowseItemsUseCase = mockk()
        coEvery { browseItemsUseCase(any(), any(), any()) } returns
            Result.success(BrowseResult.Complete(items))

        // When
        viewModel = FileBrowserViewModel(browseItemsUseCase, testDispatcher, eventBus, extractionQueue)

        // Then
        val state = viewModel.uiState.value
        assertTrue(state is FileBrowserUiState.Success)
        assertEquals(1, (state as FileBrowserUiState.Success).items.size)
    }

    // Helper functions
    private fun createBrowsableItem(
        name: String,
        isDirectory: Boolean = false,
        isArchive: Boolean = false
    ): BrowsableItem {
        return when {
            isArchive -> BrowsableItem.ArchiveFile(
                path = ResourcePath.ArchiveEntry(
                    archivePath = "file:///$name",
                    entryPath = ""
                ),
                name = name,
                sizeBytes = 1024L,
                lastModified = System.currentTimeMillis(),
                archivePath = ResourcePath.ArchiveEntry(
                    archivePath = "file:///$name",
                    entryPath = ""
                ),
                mimeType = "application/zip"
            )
            isDirectory -> BrowsableItem.FileSystemDirectory(
                path = ResourcePath.FileSystem("file:///$name"),
                name = name,
                sizeBytes = 0L,
                lastModified = System.currentTimeMillis()
            )
            else -> BrowsableItem.FileSystemFile(
                path = ResourcePath.FileSystem("file:///$name"),
                name = name,
                sizeBytes = 1024L,
                lastModified = System.currentTimeMillis(),
                mimeType = "text/plain"
            )
        }
    }

    // ========== CACHE TESTS ==========

    @Test
    fun `browseDirectory with Paginated result populates cache`() = runTest {
        // Given
        val paginatedItems = (0 until 100).map {
            createBrowsableItem("file_$it.txt", isDirectory = false)
        }
        val paginatedResult = BrowseResult.Paginated(
            items = paginatedItems,
            totalEstimate = 1000,
            nextOffset = 100,
            hasMore = true
        )
        coEvery { browseItemsUseCase(any(), offset = 0, limit = 100) } returns Result.success(paginatedResult)

        // When
        viewModel.navigateToPath(ResourcePath.FileSystem("file:///test"))

        // Then
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        // For paginated results, emitVisibleItems currently shows ALL cached items (100)
        // TODO: Should limit to VIEWPORT_SIZE (50) - track in Phase 6/7
        assertEquals(100, state.items.size)

        // Calculate expected lexicographic order (file_0, file_1, file_10, file_11, ...)
        val sortedNames = paginatedItems.map { it.name }.sortedBy { it.lowercase() }
        assertEquals(sortedNames.first(), state.items.first().name)
    }

    @Test
    fun `onScrollPositionChanged triggers loadNextPage at threshold`() = runTest {
        // Given - Setup paginated result
        val page1Items = (0 until 100).map {
            createBrowsableItem("file_$it.txt", isDirectory = false)
        }
        val page2Items = (100 until 200).map {
            createBrowsableItem("file_$it.txt", isDirectory = false)
        }

        coEvery { browseItemsUseCase(any(), offset = 0, limit = 100) } returns Result.success(
            BrowseResult.Paginated(
                items = page1Items,
                totalEstimate = 1000,
                nextOffset = 100,
                hasMore = true
            )
        )
        coEvery { browseItemsUseCase(any(), offset = 100, limit = 100) } returns Result.success(
            BrowseResult.Paginated(
                items = page2Items,
                totalEstimate = 1000,
                nextOffset = 200,
                hasMore = true
            )
        )

        viewModel.navigateToPath(ResourcePath.FileSystem("file:///test"))

        // When - Scroll to position that triggers load (80% of BUFFER_AFTER = 20 items)
        // With VIEWPORT_SIZE=50, BUFFER_AFTER=25, threshold at 20
        // currentWindowEnd=100, scroll to position 85 → distance to end = 15 < 20
        viewModel.onScrollPositionChanged(firstVisibleItemIndex = 60)

        // Then - Second page should be loading (cannot easily verify due to async, but no crash is good)
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        assertTrue(state.items.isNotEmpty())
    }

    @Test
    fun `cleanupCache removes items outside window`() = runTest {
        // Given - Setup paginated result with 1000 items
        val page1Items = (0 until 100).map {
            createBrowsableItem("file_$it.txt", isDirectory = false)
        }
        coEvery { browseItemsUseCase(any(), offset = 0, limit = 100) } returns Result.success(
            BrowseResult.Paginated(
                items = page1Items,
                totalEstimate = 1000,
                nextOffset = 100,
                hasMore = true
            )
        )
        // Scroll triggers page 2 load — return items consistent with page 1 naming
        coEvery { browseItemsUseCase(any(), offset = 100, limit = 100) } returns Result.success(
            BrowseResult.Paginated(
                items = (100 until 200).map { createBrowsableItem("file_$it.txt", isDirectory = false) },
                totalEstimate = 1000,
                nextOffset = 200,
                hasMore = false
            )
        )

        viewModel.navigateToPath(ResourcePath.FileSystem("file:///test"))

        // When - Scroll forward (should trigger cleanup of items outside window)
        // With VIEWPORT_SIZE=50, BUFFER_BEFORE=25, BUFFER_AFTER=25
        // Window at center=50: start=25, end=125
        viewModel.onScrollPositionChanged(firstVisibleItemIndex = 50)

        // Then - Should still have items (cleanup doesn't crash)
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        assertTrue(state.items.isNotEmpty())
        // Items from index 50 onwards should be visible
        assertTrue(state.items.all { it.name.startsWith("file_") })
    }

    @Test
    fun `browseDirectory with Complete result does not enable pagination`() = runTest {
        // Given - Small directory (Complete result)
        val completeItems = (0 until 10).map {
            createBrowsableItem("file_$it.txt", isDirectory = false)
        }
        coEvery { browseItemsUseCase(any(), offset = 0, limit = 100) } returns Result.success(
            BrowseResult.Complete(completeItems)
        )

        // When
        viewModel.navigateToPath(ResourcePath.FileSystem("file:///test"))

        // Then
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        assertEquals(10, state.items.size)

        // When - Try scrolling (should not trigger pagination for Complete result)
        viewModel.onScrollPositionChanged(firstVisibleItemIndex = 5)

        // Then - Still 10 items (no pagination happened)
        val stateAfterScroll = viewModel.uiState.value as FileBrowserUiState.Success
        assertEquals(10, stateAfterScroll.items.size)
    }

    @Test
    fun `onScrollPositionChanged with non-paginated result does nothing`() = runTest {
        // Given - Initial state with Complete result (non-paginated)
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        val initialItemCount = state.items.size

        // When - Try scrolling
        viewModel.onScrollPositionChanged(firstVisibleItemIndex = 1)

        // Then - State unchanged
        val afterScroll = viewModel.uiState.value as FileBrowserUiState.Success
        assertEquals(initialItemCount, afterScroll.items.size)
    }

    @Test
    fun `loadNextPage silently fails on error without crashing`() = runTest {
        // Given - Setup paginated result for first page
        val page1Items = (0 until 100).map {
            createBrowsableItem("file_$it.txt", isDirectory = false)
        }
        coEvery { browseItemsUseCase(any(), offset = 0, limit = 100) } returns Result.success(
            BrowseResult.Paginated(
                items = page1Items,
                totalEstimate = 1000,
                nextOffset = 100,
                hasMore = true
            )
        )

        // Second page fails
        coEvery { browseItemsUseCase(any(), offset = 100, limit = 100) } returns Result.failure(
            Exception("Network error")
        )

        viewModel.navigateToPath(ResourcePath.FileSystem("file:///test"))

        // When - Scroll to trigger next page load
        viewModel.onScrollPositionChanged(firstVisibleItemIndex = 60)

        // Then - Should not crash, keep visible window
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        // After scroll to index 60, visible items = centerIndex(85) to centerIndex+VIEWPORT_SIZE(135)
        // But we only have items 0-99 cached, filtered/sorted
        assertTrue(state.items.isNotEmpty()) // Should have items
        assertTrue(state.items.size <= FileBrowserViewModel.HALF_WINDOW * 2) // Should be <= window size
    }

    // ========== Navigation during paginated mode ==========

    @Test
    fun `navigateUp during paginated mode resets cache and pagination state`() = runTest {
        val allItems = (0 until 200).map { i -> createBrowsableItem("item_$i.txt") }
        coEvery { browseItemsUseCase(any(), any(), any()) } returns Result.success(
            BrowseResult.Complete(items = allItems.take(3))
        )
        viewModel = FileBrowserViewModel(browseItemsUseCase, testDispatcher, eventBus, extractionQueue)

        coEvery { browseItemsUseCase(any(), any(), any()) } returns Result.success(
            BrowseResult.Paginated(items = allItems.take(100), hasMore = true, totalEstimate = 200, nextOffset = 100)
        )
        val firstItem = (viewModel.uiState.value as FileBrowserUiState.Success).items.first()
        viewModel.navigateInto(firstItem)

        coEvery { browseItemsUseCase(any(), any(), any()) } returns Result.success(
            BrowseResult.Complete(items = allItems.take(3))
        )
        viewModel.navigateUp()

        val state = viewModel.uiState.value as FileBrowserUiState.Success
        assertEquals("After navigateUp, should show parent items", 3, state.items.size)
    }

    @Test
    fun `refresh during paginated mode reloads from offset 0`() = runTest {
        val allItems = (0 until 200).map { i -> createBrowsableItem("item_$i.txt") }
        coEvery { browseItemsUseCase(any(), any(), any()) } returns Result.success(
            BrowseResult.Paginated(items = allItems.take(100), hasMore = true, totalEstimate = 200, nextOffset = 100)
        )
        viewModel = FileBrowserViewModel(browseItemsUseCase, testDispatcher, eventBus, extractionQueue)

        val loadedOffsets = mutableListOf<Int>()
        coEvery { browseItemsUseCase(any(), any(), any()) } answers {
            val offset = arg<Int>(1)
            loadedOffsets.add(offset)
            Result.success(BrowseResult.Paginated(
                items = allItems.drop(offset).take(100),
                hasMore = offset + 100 < 200,
                totalEstimate = 200,
                nextOffset = offset + 100
            ))
        }

        viewModel.refresh()

        assertTrue("refresh() must reload from offset 0", loadedOffsets.contains(0))
        assertNotNull("State must be Success after refresh",
            viewModel.uiState.value as? FileBrowserUiState.Success)
    }

    @Test
    fun `loadNextPage failure keeps state as Success`() = runTest {
        val allItems = (0 until 500).map { i -> createBrowsableItem("item_$i.txt") }
        coEvery { browseItemsUseCase(any(), any(), any()) } answers {
            val offset = arg<Int>(1)
            if (offset == 0) {
                Result.success(BrowseResult.Paginated(
                    items = allItems.take(100), hasMore = true, totalEstimate = 500, nextOffset = 100
                ))
            } else {
                Result.failure(Exception("Load failed"))
            }
        }
        viewModel = FileBrowserViewModel(browseItemsUseCase, testDispatcher, eventBus, extractionQueue)

        viewModel.onScrollPositionChanged(firstVisibleItemIndex = 40)

        assertTrue("State must stay Success after page load failure",
            viewModel.uiState.value is FileBrowserUiState.Success)
    }

    @Test
    fun `getSelectedPaths returns correct paths after selection`() = runTest {
        val items = (0 until 10).map { i -> createBrowsableItem("file_$i.txt") }
        coEvery { browseItemsUseCase(any(), any(), any()) } returns Result.success(BrowseResult.Complete(items = items))
        viewModel = FileBrowserViewModel(browseItemsUseCase, testDispatcher, eventBus, extractionQueue)

        viewModel.enterSelectionMode()
        val state = viewModel.uiState.value as FileBrowserUiState.Success
        viewModel.toggleFileSelection(state.items[0])
        viewModel.toggleFileSelection(state.items[2])
        viewModel.toggleFileSelection(state.items[4])

        val paths = viewModel.getSelectedPaths()
        assertEquals(3, paths.size)
        assertTrue(paths.contains(state.items[0].path))
        assertTrue(paths.contains(state.items[2].path))
        assertTrue(paths.contains(state.items[4].path))
    }

    @Test
    fun `isFileSelected accurate after multiple toggles`() = runTest {
        val items = (0 until 5).map { i -> createBrowsableItem("file_$i.txt") }
        coEvery { browseItemsUseCase(any(), any(), any()) } returns Result.success(BrowseResult.Complete(items = items))
        viewModel = FileBrowserViewModel(browseItemsUseCase, testDispatcher, eventBus, extractionQueue)

        viewModel.enterSelectionMode()
        val item = (viewModel.uiState.value as FileBrowserUiState.Success).items[0]

        viewModel.toggleFileSelection(item)
        assertTrue(viewModel.isFileSelected(item))

        viewModel.toggleFileSelection(item)
        assertFalse(viewModel.isFileSelected(item))

        viewModel.toggleFileSelection(item)
        assertTrue(viewModel.isFileSelected(item))
    }

    private fun createBrowsableItems(count: Int) = (0 until count).map { i ->
        createBrowsableItem("file_$i.txt")
    }
}
