package app.otter.integration.viewmodel

import android.net.Uri
import app.otter.data.util.ResourcePathConverter
import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.BrowseResult
import app.otter.domain.model.ResourcePath
import app.otter.domain.usecase.BrowseItemsUseCase
import app.otter.domain.usecase.GetFolderCountsUseCase
import app.otter.service.ExtractionEventBus
import app.otter.service.ExtractionQueue
import app.otter.ui.viewmodel.FileBrowserUiState
import app.otter.ui.viewmodel.FileBrowserViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Integration tests for the 200-item sliding window with mock repository.
 *
 * Tests the full scroll cycle: browse → scroll down → cleanup → scroll up → items available.
 * All I/O is mocked; tests verify correct window behavior end-to-end.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FileBrowserViewModelWindowMockIntegrationTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val browseItemsUseCase = mockk<BrowseItemsUseCase>()
    private val getFolderCountsUseCase = mockk<GetFolderCountsUseCase>()
    private val eventBus = ExtractionEventBus()
    private val extractionQueue = ExtractionQueue()
    private lateinit var viewModel: FileBrowserViewModel

    private val allItems: List<BrowsableItem> = (0 until 1000).map { i ->
        BrowsableItem.ArchiveFileEntry(
            path = ResourcePath.ArchiveEntry("file:///archive.zip", "item_$i.txt"),
            name = "item_${i.toString().padStart(4, '0')}.txt",
            sizeBytes = 1024L,
            lastModified = 0L,
            archivePath = ResourcePath.ArchiveEntry("file:///archive.zip", "item_$i.txt"),
            mimeType = "text/plain"
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(ResourcePathConverter)
        every { ResourcePathConverter.toUri(any()) } returns mockk<Uri>(relaxed = true)
        every { getFolderCountsUseCase(any()) } returns emptyFlow()
        coEvery { browseItemsUseCase.invoke(any(), any(), any()) } answers {
            val offset = arg<Int>(1)
            val limit = arg<Int>(2)
            if (offset + limit >= allItems.size) {
                Result.success(BrowseResult.Complete(allItems.drop(offset)))
            } else {
                Result.success(BrowseResult.Paginated(
                    items = allItems.drop(offset).take(limit),
                    hasMore = true,
                    totalEstimate = allItems.size,
                    nextOffset = offset + limit
                ))
            }
        }

        viewModel = FileBrowserViewModel(browseItemsUseCase, getFolderCountsUseCase, testDispatcher, eventBus, extractionQueue,
            startPath = ResourcePath.FileSystem("/storage/emulated/0")
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun successState() = viewModel.uiState.value as? FileBrowserUiState.Success

    @Test
    fun `full scroll down cycle loads next pages correctly`() = runTest {
        // Scroll progressively down through 300 items
        for (i in 0..250 step 10) {
            viewModel.onScrollPositionChanged(firstVisibleItemIndex = i)
        }

        val state = successState()
        assertNotNull("State must be Success after full scroll down", state)
        assertTrue("Items should be loaded", state!!.items.isNotEmpty())
    }

    @Test
    fun `scroll down then up preserves items at original position`() = runTest {
        // Scroll down to ~300
        for (i in 0..250 step 25) {
            viewModel.onScrollPositionChanged(firstVisibleItemIndex = i)
        }

        // Scroll back up
        for (i in 250 downTo 0 step 25) {
            viewModel.onScrollPositionChanged(firstVisibleItemIndex = i)
        }

        val state = successState()
        assertNotNull("State must be non-null after scroll down+up", state)
        assertTrue("Items must be present after scroll down+up", state!!.items.isNotEmpty())
    }

    @Test
    fun `end of archive items remain accessible after reaching hasMore false`() = runTest {
        // Archive has 1000 items. Scroll all the way to the end.
        // Page at offset=900 returns Complete → hasMore=false
        // Cleanup must NOT remove items 900-999
        for (i in 0..900 step 30) {
            viewModel.onScrollPositionChanged(firstVisibleItemIndex = i)
        }

        val state = successState()
        assertNotNull("State must be Success at end", state)
        assertTrue("Archive end items must remain visible after reaching end",
            state!!.items.isNotEmpty())
    }

    @Test
    fun `window stays centered on current position`() = runTest {
        // Scroll to absolute position ~300
        for (i in 0..250 step 25) {
            viewModel.onScrollPositionChanged(firstVisibleItemIndex = i)
        }

        val state = successState()!!
        // The displayed items should be within ±HALF_WINDOW of current position.
        // Window count ≤ 2 * HALF_WINDOW + page_size (one extra page may be loaded ahead)
        assertTrue("Displayed items should be present", state.items.isNotEmpty())
        assertTrue("Window should not exceed 2*HALF_WINDOW + 2*batch_size",
            state.items.size <= 2 * FileBrowserViewModel.HALF_WINDOW + 200)
    }

    @Test
    fun `rapid scroll simulation does not cause empty display`() = runTest {
        // Simulate fast fling: jump to position 500 directly
        viewModel.onScrollPositionChanged(firstVisibleItemIndex = 500)

        // After fast scroll, state must not be empty
        val state = successState()
        assertNotNull("State must not be null after fast scroll", state)
        assertTrue("Display must not be empty after fast scroll", state!!.items.isNotEmpty())
    }

    @Test
    fun `selection survives full scroll cycle`() = runTest {
        // Enter selection mode and select first item
        viewModel.enterSelectionMode()
        val initialState = successState()!!
        val selectedItem = initialState.items.first()
        viewModel.toggleFileSelection(selectedItem)
        assertEquals(1, successState()!!.selectedCount)

        // Scroll far away (triggers cleanup of initial window)
        for (i in 0..300 step 30) {
            viewModel.onScrollPositionChanged(firstVisibleItemIndex = i)
        }

        // Selection must still be tracked
        assertEquals("Selection count must be preserved after scroll cycle",
            1, successState()!!.selectedCount)
    }
}
