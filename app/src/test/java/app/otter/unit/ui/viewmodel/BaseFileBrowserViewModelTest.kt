package app.otter.ui.viewmodel

import android.net.Uri
import app.otter.data.util.ResourcePathConverter
import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.BrowseResult
import app.otter.domain.model.ResourcePath
import app.otter.domain.usecase.BrowseItemsUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before

/**
 * Base class for FileBrowserViewModel tests.
 * Provides common setup, teardown, and helper methods.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class BaseFileBrowserViewModelTest {

    protected lateinit var browseItemsUseCase: BrowseItemsUseCase
    protected lateinit var eventBus: app.otter.service.ExtractionEventBus
    protected lateinit var extractionQueue: app.otter.service.ExtractionQueue
    protected lateinit var viewModel: FileBrowserViewModel
    protected val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockkObject(ResourcePathConverter)
        every { ResourcePathConverter.toUri(any()) } returns mockk<Uri>(relaxed = true)

        browseItemsUseCase = mockk()
        eventBus = app.otter.service.ExtractionEventBus()
        extractionQueue = app.otter.service.ExtractionQueue()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
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

    protected fun createBrowsableItem(
        name: String,
        isDirectory: Boolean = false,
        isArchive: Boolean = false
    ): BrowsableItem {
        return when {
            isArchive -> BrowsableItem.ArchiveFile(
                path = ResourcePath.ArchiveEntry(archivePath = "file:///$name", entryPath = ""),
                name = name,
                sizeBytes = 1024L,
                lastModified = System.currentTimeMillis(),
                archivePath = ResourcePath.ArchiveEntry(archivePath = "file:///$name", entryPath = ""),
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
}
