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
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Base class for FileBrowserViewModel tests.
 * Provides common setup, teardown, and helper methods.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
abstract class BaseFileBrowserViewModelTest {

    protected lateinit var browseItemsUseCase: BrowseItemsUseCase
    protected lateinit var eventBus: app.otter.service.ExtractionEventBus
    protected lateinit var extractionQueue: app.otter.service.ExtractionQueue
    protected lateinit var viewModel: FileBrowserViewModel
    protected val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        browseItemsUseCase = mockk()
        eventBus = app.otter.service.ExtractionEventBus()
        extractionQueue = app.otter.service.ExtractionQueue()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========== Helper Methods ==========

    protected fun createMockArchiveItems(count: Int): List<BrowsableItem> {
        return (0 until count).map { index ->
            BrowsableItem.ArchiveFileEntry(
                path = ResourcePath.ArchiveEntry(
                    archivePath = "/storage/emulated/0/test.zip",
                    entryPath = "file_$index.txt"
                ),
                name = "file_$index.txt",
                sizeBytes = 1024L,
                lastModified = System.currentTimeMillis(),
                archivePath = ResourcePath.ArchiveEntry(
                    archivePath = "/storage/emulated/0/test.zip",
                    entryPath = "file_$index.txt"
                ),
                mimeType = "text/plain"
            )
        }
    }

    protected fun mockBrowseResult(items: List<BrowsableItem>) {
        coEvery { browseItemsUseCase.invoke(any(), any(), any()) } returns Result.success(
            BrowseResult.Complete(items = items)
        )
    }

    protected fun getCurrentSuccessState(): FileBrowserUiState.Success {
        val state = viewModel.uiState.value
        assertTrue("State should be Success", state is FileBrowserUiState.Success)
        return state as FileBrowserUiState.Success
    }
}
