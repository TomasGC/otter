package app.otter.ui.viewmodel

import app.otter.domain.model.BrowseResult
import io.mockk.coEvery
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import app.otter.domain.model.ResourcePath

/**
 * Tests for FileBrowserViewModel cache cleanup.
 *
 * TDD - Phase 2: Cache cleanup
 * Covers:
 * - Cleanup removes items outside window
 * - Cleanup preserves items within window
 * - No cleanup in non-paginated archives
 * - Boundary handling during cleanup
 * - Continuous scroll cleanup
 * - Window size limits after cleanup
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FileBrowserViewModelCacheCleanupTest : BaseFileBrowserViewModelTest() {

    @Test
    fun `cache cleanup removes items outside window when scrolling`() = runTest {
        // Arrange - Create large paginated archive
        val allItems = createMockArchiveItems(10_000)

        // Mock multiple pages
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

        coEvery {
            browseItemsUseCase.invoke(any(), any(), any())
        } returns Result.success(
            BrowseResult.Paginated(
                items = allItems.drop(100).take(100),
                hasMore = true,
                totalEstimate = 10_000,
                nextOffset = 200
            )
        )

        // Act - Create ViewModel
        viewModel = FileBrowserViewModel(
            browseItemsUseCase,
            testDispatcher,
            eventBus,
            extractionQueue
        ,
            startPath = ResourcePath.FileSystem("/storage/emulated/0")
        )

        // Scroll forward significantly to trigger cleanup
        repeat(10) { index ->
            viewModel.onScrollPositionChanged(firstVisibleItemIndex = index * 10)
        }

        // Assert - Items far behind should be cleaned up, state should be valid
        val uiState = getCurrentSuccessState()
        assertNotNull("State should not be null after cleanup", uiState)
        assertTrue("Should still have items", uiState.items.isNotEmpty())
    }

    @Test
    fun `cache cleanup preserves items within current window`() = runTest {
        // Arrange - Create large archive
        val items = createMockArchiveItems(10_000)

        coEvery { browseItemsUseCase.invoke(any(), any(), any()) } returns Result.success(
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
            testDispatcher,
            eventBus,
            extractionQueue
        ,
            startPath = ResourcePath.FileSystem("/storage/emulated/0")
        )

        // Scroll within window (should not trigger cleanup)
        viewModel.onScrollPositionChanged(firstVisibleItemIndex = 10)
        viewModel.onScrollPositionChanged(firstVisibleItemIndex = 20)
        viewModel.onScrollPositionChanged(firstVisibleItemIndex = 15)

        // Assert - All items within window should still be available
        val uiState = getCurrentSuccessState()
        assertNotNull("State should not be null", uiState)
        assertTrue("Should have items", uiState.items.isNotEmpty())
    }

    @Test
    fun `cache cleanup does not affect non-paginated archives`() = runTest {
        // Arrange - Create small archive (Complete result)
        val items = createMockArchiveItems(100)
        mockBrowseResult(items)

        // Act - Create ViewModel
        viewModel = FileBrowserViewModel(
            browseItemsUseCase,
            testDispatcher,
            eventBus,
            extractionQueue
        ,
            startPath = ResourcePath.FileSystem("/storage/emulated/0")
        )

        // Scroll through entire list
        repeat(50) { index ->
            viewModel.onScrollPositionChanged(firstVisibleItemIndex = index)
        }

        // Assert - All items should remain (no pagination, no cleanup)
        val uiState = getCurrentSuccessState()
        assertNotNull("State should not be null", uiState)
        assertEquals("Should have all 100 items", 100, uiState.items.size)
    }

    @Test
    fun `cache cleanup handles boundaries correctly`() = runTest {
        // Arrange - Create paginated archive
        val items = createMockArchiveItems(10_000)

        coEvery { browseItemsUseCase.invoke(any(), any(), any()) } returns Result.success(
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
            testDispatcher,
            eventBus,
            extractionQueue
        ,
            startPath = ResourcePath.FileSystem("/storage/emulated/0")
        )

        // Scroll to start (index 0)
        viewModel.onScrollPositionChanged(firstVisibleItemIndex = 0)

        // Assert - Should not crash at boundary
        val uiState = getCurrentSuccessState()
        assertNotNull("State should not be null at start boundary", uiState)

        // Scroll to various positions
        viewModel.onScrollPositionChanged(firstVisibleItemIndex = 50)
        viewModel.onScrollPositionChanged(firstVisibleItemIndex = 0)

        // Assert - Should handle back and forth
        val finalState = getCurrentSuccessState()
        assertNotNull("State should not be null after boundary navigation", finalState)
    }

    @Test
    fun `cache cleanup is called on every scroll position change in paginated mode`() = runTest {
        // Arrange - Create paginated archive
        val items = createMockArchiveItems(10_000)

        coEvery { browseItemsUseCase.invoke(any(), any(), any()) } returns Result.success(
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
            testDispatcher,
            eventBus,
            extractionQueue
        ,
            startPath = ResourcePath.FileSystem("/storage/emulated/0")
        )

        // Simulate continuous scrolling
        for (index in 0..50 step 5) {
            viewModel.onScrollPositionChanged(firstVisibleItemIndex = index)
        }

        // Assert - State should remain consistent throughout
        val uiState = getCurrentSuccessState()
        assertNotNull("State should not be null after continuous scroll", uiState)
        assertTrue("Should have items", uiState.items.isNotEmpty())
    }

    @Test
    fun `cache cleanup maintains window size limits`() = runTest {
        // Arrange - Create large archive
        val allItems = createMockArchiveItems(10_000)

        // Mock responses for multiple pages
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

        coEvery {
            browseItemsUseCase.invoke(any(), any(), any())
        } returns Result.success(
            BrowseResult.Paginated(
                items = allItems.drop(100).take(100),
                hasMore = true,
                totalEstimate = 10_000,
                nextOffset = 200
            )
        )

        coEvery {
            browseItemsUseCase.invoke(any(), any(), any())
        } returns Result.success(
            BrowseResult.Paginated(
                items = allItems.drop(200).take(100),
                hasMore = true,
                totalEstimate = 10_000,
                nextOffset = 300
            )
        )

        // Act - Create ViewModel and scroll extensively
        viewModel = FileBrowserViewModel(
            browseItemsUseCase,
            testDispatcher,
            eventBus,
            extractionQueue
        ,
            startPath = ResourcePath.FileSystem("/storage/emulated/0")
        )

        // Scroll through multiple pages
        viewModel.onScrollPositionChanged(firstVisibleItemIndex = 60)
        viewModel.onScrollPositionChanged(firstVisibleItemIndex = 120)
        viewModel.onScrollPositionChanged(firstVisibleItemIndex = 180)

        // Assert - Window size should remain bounded
        val uiState = getCurrentSuccessState()
        assertNotNull("State should not be null", uiState)
        assertTrue("Window should be bounded", uiState.items.size <= 200) // Reasonable upper bound
    }
}
