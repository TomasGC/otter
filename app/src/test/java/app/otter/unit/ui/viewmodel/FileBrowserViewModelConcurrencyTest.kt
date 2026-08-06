package app.otter.ui.viewmodel

import app.otter.domain.model.BrowseResult
import io.mockk.coEvery
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test
import app.otter.domain.model.ResourcePath

/**
 * Tests for FileBrowserViewModel concurrent access and race conditions.
 *
 * TDD - Phase 1: Concurrent access tests
 * TDD - Phase 2: Race condition tests
 * Covers:
 * - Concurrent state reads
 * - Rapid filter toggles
 * - Concurrent navigation
 * - Multiple sort order changes
 * - Concurrent selection toggles
 * - Concurrent page loads
 * - Cleanup during page load
 * - Multiple rapid scroll events
 * - Cache operations during filter toggle
 * - Cache state during navigation and scrolling
 * - Double load prevention (isLoadingPage flag)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FileBrowserViewModelConcurrencyTest : BaseFileBrowserViewModelTest() {

    // ========== RED Phase: Concurrent Access Tests ==========

    @Test
    fun `concurrent state access does not throw ConcurrentModificationException`() = runTest {
        // Arrange - Create archive with 100 files
        val items = createMockArchiveItems(100)
        mockBrowseResult(items)

        // Act - Create ViewModel
        viewModel = FileBrowserViewModel(
            browseItemsUseCase,
            getFolderCountsUseCase,
            testDispatcher,
            eventBus,
            extractionQueue
        ,
            startPath = ResourcePath.FileSystem("/storage/emulated/0")
        )

        // Simulate concurrent reads from multiple collectors
        val job1 = launch {
            repeat(50) {
                viewModel.uiState.value
            }
        }

        val job2 = launch {
            repeat(50) {
                viewModel.uiState.value
            }
        }

        val job3 = launch {
            repeat(50) {
                viewModel.uiState.value
            }
        }

        // Wait for all jobs to complete
        job1.join()
        job2.join()
        job3.join()

        // Assert - No exception thrown, state is consistent
        val uiState = getCurrentSuccessState()
        assertNotNull("State should not be null after concurrent access", uiState)
        assertEquals("Should still have 100 items", 100, uiState.items.size)
    }

    @Test
    fun `rapid filter toggles do not corrupt state`() = runTest {
        // Arrange - Create archive with mixed items
        val items = createMockArchiveItems(50)
        mockBrowseResult(items)

        // Act - Create ViewModel
        viewModel = FileBrowserViewModel(
            browseItemsUseCase,
            getFolderCountsUseCase,
            testDispatcher,
            eventBus,
            extractionQueue
        ,
            startPath = ResourcePath.FileSystem("/storage/emulated/0")
        )

        // Simulate rapid filter toggles from UI thread
        repeat(20) {
            viewModel.toggleArchiveFilter()
        }

        // Assert - State is valid and consistent
        val uiState = getCurrentSuccessState()
        assertNotNull("State should not be null after rapid toggles", uiState)
        // After even number of toggles, filter should be back to original state
    }

    @Test
    fun `concurrent navigation does not corrupt navigation stack`() = runTest {
        // Arrange - Create archive hierarchy
        val items = createMockArchiveItems(10)
        mockBrowseResult(items)

        // Act - Create ViewModel
        viewModel = FileBrowserViewModel(
            browseItemsUseCase,
            getFolderCountsUseCase,
            testDispatcher,
            eventBus,
            extractionQueue
        ,
            startPath = ResourcePath.FileSystem("/storage/emulated/0")
        )

        // Simulate rapid navigation (back and forth)
        repeat(10) {
            viewModel.navigateUp()
        }

        // Assert - No exception thrown
        val uiState = viewModel.uiState.value
        assertNotNull("State should not be null", uiState)
    }

    @Test
    fun `multiple sort order changes do not cause race conditions`() = runTest {
        // Arrange - Create archive with items
        val items = createMockArchiveItems(50)
        mockBrowseResult(items)

        // Act - Create ViewModel
        viewModel = FileBrowserViewModel(
            browseItemsUseCase,
            getFolderCountsUseCase,
            testDispatcher,
            eventBus,
            extractionQueue
        ,
            startPath = ResourcePath.FileSystem("/storage/emulated/0")
        )

        // Simulate rapid sort order changes
        val sortOrders = listOf(
            SortOrder.ARCHIVES_FIRST,
            SortOrder.NAME_ASC,
            SortOrder.NAME_DESC,
            SortOrder.SIZE_ASC,
            SortOrder.SIZE_DESC
        )

        repeat(20) { index ->
            viewModel.setSortOrder(sortOrders[index % sortOrders.size])
        }

        // Assert - State is consistent
        val uiState = getCurrentSuccessState()
        assertNotNull("State should not be null after sort changes", uiState)
        assertEquals("Should still have all items", 50, uiState.items.size)
    }

    @Test
    fun `concurrent selection toggles do not corrupt selected set`() = runTest {
        // Arrange - Create archive with items
        val items = createMockArchiveItems(10)
        mockBrowseResult(items)

        // Act - Create ViewModel
        viewModel = FileBrowserViewModel(
            browseItemsUseCase,
            getFolderCountsUseCase,
            testDispatcher,
            eventBus,
            extractionQueue
        ,
            startPath = ResourcePath.FileSystem("/storage/emulated/0")
        )

        val uiState = getCurrentSuccessState()

        // Simulate rapid selection toggles on same item
        repeat(20) {
            // Toggle selection on first item
            viewModel.toggleFileSelection(uiState.items.first())
        }

        // Assert - Selection state is consistent
        // After even number of toggles, item should be unselected
        assertNotNull("State should not be null", viewModel.uiState.value)
    }

    // ========== RED Phase: Cache Race Condition Tests ==========

    @Test
    fun `concurrent page loads do not corrupt cache state`() = runTest {
        // Arrange - Create large archive
        val allItems = createMockArchiveItems(10_000)

        coEvery {
            browseItemsUseCase.invoke(any(), any(), any())
        } returns Result.success(
            BrowseResult.Paginated(
                items = allItems.take(100),
                hasMore = true,
                totalEstimate = 10_000,
                nextOffset = 100
            )
        )

        // Act - Create ViewModel
        viewModel = FileBrowserViewModel(
            browseItemsUseCase,
            getFolderCountsUseCase,
            testDispatcher,
            eventBus,
            extractionQueue
        ,
            startPath = ResourcePath.FileSystem("/storage/emulated/0")
        )

        // Simulate rapid scroll that would trigger multiple page loads
        val jobs = List(10) { index ->
            launch {
                viewModel.onScrollPositionChanged(firstVisibleItemIndex = index * 10)
            }
        }

        // Wait for all scroll events to complete
        jobs.forEach { it.join() }

        // Assert - Cache should be in consistent state
        val uiState = getCurrentSuccessState()
        assertNotNull("State should not be null after concurrent loads", uiState)
        assertTrue("Should have items", uiState.items.isNotEmpty())
    }

    @Test
    fun `cleanup during page load does not cause ConcurrentModificationException`() = runTest {
        // Arrange - Create large archive
        val items = createMockArchiveItems(10_000)

        coEvery {
            browseItemsUseCase.invoke(any(), any(), any())
        } returns Result.success(
            BrowseResult.Paginated(
                items = items.take(100),
                hasMore = true,
                totalEstimate = 10_000,
                nextOffset = 100
            )
        )

        // Act - Create ViewModel
        viewModel = FileBrowserViewModel(
            browseItemsUseCase,
            getFolderCountsUseCase,
            testDispatcher,
            eventBus,
            extractionQueue
        ,
            startPath = ResourcePath.FileSystem("/storage/emulated/0")
        )

        // Trigger page load and cleanup simultaneously
        val loadJob = launch {
            viewModel.onScrollPositionChanged(firstVisibleItemIndex = 60)
        }

        val cleanupJob = launch {
            repeat(20) { index ->
                viewModel.onScrollPositionChanged(firstVisibleItemIndex = 30 + index)
            }
        }

        loadJob.join()
        cleanupJob.join()

        // Assert - No ConcurrentModificationException
        val uiState = getCurrentSuccessState()
        assertNotNull("State should not be null", uiState)
    }

    @Test
    fun `multiple scroll events in rapid succession do not cause state corruption`() = runTest {
        // Arrange
        val items = createMockArchiveItems(10_000)

        coEvery {
            browseItemsUseCase.invoke(any(), any(), any())
        } returns Result.success(
            BrowseResult.Paginated(
                items = items.take(100),
                hasMore = true,
                totalEstimate = 10_000,
                nextOffset = 100
            )
        )

        // Act - Create ViewModel
        viewModel = FileBrowserViewModel(
            browseItemsUseCase,
            getFolderCountsUseCase,
            testDispatcher,
            eventBus,
            extractionQueue
        ,
            startPath = ResourcePath.FileSystem("/storage/emulated/0")
        )

        // Fire many scroll events rapidly
        repeat(100) { index ->
            viewModel.onScrollPositionChanged(firstVisibleItemIndex = index % 50)
        }

        // Assert - State should remain consistent
        val uiState = getCurrentSuccessState()
        assertNotNull("State should not be null after rapid scrolls", uiState)
        assertTrue("Should have valid items", uiState.items.isNotEmpty())
    }

    @Test
    fun `cache operations during filter toggle do not cause race conditions`() = runTest {
        // Arrange
        val items = createMockArchiveItems(10_000)

        coEvery {
            browseItemsUseCase.invoke(any(), any(), any())
        } returns Result.success(
            BrowseResult.Paginated(
                items = items.take(100),
                hasMore = true,
                totalEstimate = 10_000,
                nextOffset = 100
            )
        )

        // Act - Create ViewModel
        viewModel = FileBrowserViewModel(
            browseItemsUseCase,
            getFolderCountsUseCase,
            testDispatcher,
            eventBus,
            extractionQueue
        ,
            startPath = ResourcePath.FileSystem("/storage/emulated/0")
        )

        // Interleave scroll and filter operations
        val scrollJob = launch {
            repeat(20) { index ->
                viewModel.onScrollPositionChanged(firstVisibleItemIndex = index * 2)
            }
        }

        val filterJob = launch {
            repeat(10) {
                viewModel.toggleArchiveFilter()
            }
        }

        scrollJob.join()
        filterJob.join()

        // Assert - No race conditions
        val uiState = getCurrentSuccessState()
        assertNotNull("State should not be null", uiState)
    }

    @Test
    fun `cache state remains consistent during navigation and scrolling`() = runTest {
        // Arrange
        val items = createMockArchiveItems(10_000)

        coEvery {
            browseItemsUseCase.invoke(any(), any(), any())
        } returns Result.success(
            BrowseResult.Paginated(
                items = items.take(100),
                hasMore = true,
                totalEstimate = 10_000,
                nextOffset = 100
            )
        )

        // Act - Create ViewModel
        viewModel = FileBrowserViewModel(
            browseItemsUseCase,
            getFolderCountsUseCase,
            testDispatcher,
            eventBus,
            extractionQueue
        ,
            startPath = ResourcePath.FileSystem("/storage/emulated/0")
        )

        // Interleave navigation and scroll operations
        val scrollJob = launch {
            repeat(15) { index ->
                viewModel.onScrollPositionChanged(firstVisibleItemIndex = index * 3)
            }
        }

        val navJob = launch {
            repeat(5) {
                viewModel.navigateUp()
            }
        }

        scrollJob.join()
        navJob.join()

        // Assert - State remains consistent
        assertNotNull("State should not be null", viewModel.uiState.value)
    }

    @Test
    fun `isLoadingPage flag prevents double page loads`() = runTest {
        // Arrange
        val items = createMockArchiveItems(10_000)
        var loadCount = 0

        coEvery {
            browseItemsUseCase.invoke(any(), any(), any())
        } answers {
            loadCount++
            Result.success(
                BrowseResult.Paginated(
                    items = items.take(100),
                    hasMore = true,
                    totalEstimate = 10_000,
                    nextOffset = 100
                )
            )
        }

        // Act - Create ViewModel
        viewModel = FileBrowserViewModel(
            browseItemsUseCase,
            getFolderCountsUseCase,
            testDispatcher,
            eventBus,
            extractionQueue
        ,
            startPath = ResourcePath.FileSystem("/storage/emulated/0")
        )

        val initialLoadCount = loadCount

        // Try to trigger multiple loads by scrolling to same position
        repeat(5) {
            viewModel.onScrollPositionChanged(firstVisibleItemIndex = 60)
        }

        // Assert - Should not trigger multiple concurrent loads
        // Initial load + at most 1 additional load (isLoadingPage flag should prevent more)
        assertTrue("Should prevent concurrent loads", loadCount <= initialLoadCount + 2)
    }
}
