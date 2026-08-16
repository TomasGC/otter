package app.otter.ui.viewmodel

import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.BrowseResult
import app.otter.domain.model.FolderCounts
import app.otter.domain.model.ResourcePath
import app.otter.domain.usecase.BrowsingUseCases
import app.otter.service.ExtractionCoordinator
import io.mockk.coEvery
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FileBrowserViewModelFolderCountsTest : BaseFileBrowserViewModelTest() {

    private val startPath = ResourcePath.FileSystem("/storage/emulated/0")

    private fun createViewModel() {
        viewModel = FileBrowserViewModel(
            BrowsingUseCases(browseItemsUseCase, getFolderCountsUseCase),
            testDispatcher,
            ExtractionCoordinator(eventBus, extractionQueue),
            startPath = startPath
        )
    }

    @Test
    fun `folderCounts accumulates pair emitted by use case`() = runTest {
        val dir = createBrowsableItem("Documents", isDirectory = true) as BrowsableItem.FileSystemDirectory
        val dirPath = (dir.path as ResourcePath.FileSystem).path
        val counts = FolderCounts(3, 7)
        mockBrowseResult(listOf(dir))
        every { getFolderCountsUseCase(listOf(dirPath)) } returns flowOf(dirPath to counts)

        createViewModel()

        assertEquals(mapOf(dirPath to counts), viewModel.folderCounts.value)
    }

    @Test
    fun `folderCounts accumulates multiple pairs from multiple directories`() = runTest {
        val dir1 = createBrowsableItem("Downloads", isDirectory = true) as BrowsableItem.FileSystemDirectory
        val dir2 = createBrowsableItem("Pictures", isDirectory = true) as BrowsableItem.FileSystemDirectory
        val path1 = (dir1.path as ResourcePath.FileSystem).path
        val path2 = (dir2.path as ResourcePath.FileSystem).path
        val counts1 = FolderCounts(1, 2)
        val counts2 = FolderCounts(3, 4)
        mockBrowseResult(listOf(dir1, dir2))
        every { getFolderCountsUseCase(listOf(path1, path2)) } returns flowOf(path1 to counts1, path2 to counts2)

        createViewModel()

        assertEquals(mapOf(path1 to counts1, path2 to counts2), viewModel.folderCounts.value)
    }

    @Test
    fun `re-browse resets folderCounts before loading new counts`() = runTest {
        val dir = createBrowsableItem("Documents", isDirectory = true) as BrowsableItem.FileSystemDirectory
        val dirPath = (dir.path as ResourcePath.FileSystem).path
        val counts = FolderCounts(2, 5)
        mockBrowseResult(listOf(dir))
        every { getFolderCountsUseCase(listOf(dirPath)) } returns flowOf(dirPath to counts)

        createViewModel()

        assertEquals(mapOf(dirPath to counts), viewModel.folderCounts.value)

        val fileItem = BrowsableItem.FileSystemFile(
            path = ResourcePath.FileSystem("$dirPath/notes.txt"),
            name = "notes.txt", sizeBytes = 100L, lastModified = 0L, mimeType = "text/plain"
        )
        coEvery { browseItemsUseCase.invoke(ResourcePath.FileSystem(dirPath), 0, 100) } returns
            Result.success(BrowseResult.Complete(listOf(fileItem)))

        viewModel.navigateInto(dir)

        assertEquals(emptyMap<String, FolderCounts>(), viewModel.folderCounts.value)
    }

    @Test
    fun `only FileSystemDirectory paths passed to getFolderCountsUseCase`() = runTest {
        val dir = createBrowsableItem("Documents", isDirectory = true) as BrowsableItem.FileSystemDirectory
        val dirPath = (dir.path as ResourcePath.FileSystem).path
        val file = createBrowsableItem("notes.txt")
        val archive = createBrowsableItem("data.zip", isArchive = true)
        mockBrowseResult(listOf(dir, file, archive))

        createViewModel()

        verify(exactly = 1) { getFolderCountsUseCase(listOf(dirPath)) }
    }

    @Test
    fun `getFolderCountsUseCase not invoked when browse result has no directories`() = runTest {
        mockBrowseResult(listOf(createBrowsableItem("notes.txt")))

        createViewModel()

        assertEquals(emptyMap<String, FolderCounts>(), viewModel.folderCounts.value)
        verify(exactly = 0) { getFolderCountsUseCase(any()) }
    }

    @Test
    fun `folderCounts stays empty when initial browse result is Paginated`() = runTest {
        coEvery { browseItemsUseCase.invoke(any(), any(), any()) } returns Result.success(
            BrowseResult.Paginated(
                items = (0 until 10).map { i ->
                    BrowsableItem.ArchiveFileEntry(
                        path = ResourcePath.ArchiveEntry("file:///archive.zip", "file_$i.txt"),
                        name = "file_$i.txt", sizeBytes = 0L, lastModified = 0L,
                        archivePath = ResourcePath.ArchiveEntry("file:///archive.zip", "file_$i.txt"),
                        mimeType = "text/plain"
                    )
                },
                hasMore = true,
                totalEstimate = 1000,
                nextOffset = 10
            )
        )

        createViewModel()

        assertEquals(emptyMap<String, FolderCounts>(), viewModel.folderCounts.value)
        verify(exactly = 0) { getFolderCountsUseCase(any()) }
    }
}
