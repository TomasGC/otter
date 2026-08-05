package app.otter.ui.viewmodel

import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.BrowseResult
import app.otter.domain.model.ResourcePath
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FileBrowserViewModelStartPathTest : BaseFileBrowserViewModelTest() {

    @Before
    fun setupStartPath() {
        coEvery { browseItemsUseCase.invoke(any(), any(), any()) } returns
            Result.success(BrowseResult.Complete(emptyList()))
    }

    private fun createViewModel(startPath: ResourcePath) = FileBrowserViewModel(
        browseItemsUseCase, getFolderCountsUseCase, testDispatcher, eventBus, extractionQueue, startPath = startPath
    )

    // ========== startPath routing ==========

    @Test
    fun `startPath FileSystem is passed to browseItemsUseCase on init`() = runTest {
        val path = ResourcePath.FileSystem("/storage/emulated/0/Downloads")
        createViewModel(path)
        coVerify { browseItemsUseCase.invoke(path, 0, any()) }
    }

    @Test
    fun `startPath ArchiveEntry root is passed to browseItemsUseCase on init`() = runTest {
        val path = ResourcePath.ArchiveEntry("/sdcard/test.zip", "")
        createViewModel(path)
        coVerify { browseItemsUseCase.invoke(path, 0, any()) }
    }

    @Test
    fun `startPath ArchiveEntry with non-empty entryPath is passed to browseItemsUseCase`() = runTest {
        val path = ResourcePath.ArchiveEntry("/sdcard/test.zip", "subfolder/")
        createViewModel(path)
        coVerify { browseItemsUseCase.invoke(path, 0, any()) }
    }

    @Test
    fun `two different startPaths produce two different browseItemsUseCase calls`() = runTest {
        val path1 = ResourcePath.FileSystem("/storage/emulated/0/Documents")
        val path2 = ResourcePath.FileSystem("/storage/emulated/0/Downloads")
        createViewModel(path1)
        createViewModel(path2)
        coVerify { browseItemsUseCase.invoke(path1, 0, any()) }
        coVerify { browseItemsUseCase.invoke(path2, 0, any()) }
    }

    // ========== initial state ==========

    @Test
    fun `startPath produces Success state immediately with UnconfinedTestDispatcher`() = runTest {
        val vm = createViewModel(ResourcePath.FileSystem("/storage/emulated/0/Downloads"))
        assertTrue("Must be Success after init", vm.uiState.value is FileBrowserUiState.Success)
    }

    @Test
    fun `startPath items from use case appear in initial Success state`() = runTest {
        val path = ResourcePath.FileSystem("/storage/emulated/0/Downloads")
        val items = listOf(
            BrowsableItem.FileSystemFile(
                path = ResourcePath.FileSystem("/storage/emulated/0/Downloads/file.txt"),
                name = "file.txt",
                sizeBytes = 1024L,
                lastModified = 0L,
                mimeType = "text/plain"
            )
        )
        coEvery { browseItemsUseCase.invoke(path, 0, any()) } returns
            Result.success(BrowseResult.Complete(items))
        val vm = createViewModel(path)
        val state = vm.uiState.value as FileBrowserUiState.Success
        assertEquals(1, state.items.size)
        assertEquals("file.txt", state.items[0].name)
    }

    @Test
    fun `startPath error from use case produces Error state`() = runTest {
        val path = ResourcePath.FileSystem("/storage/emulated/0/Downloads")
        coEvery { browseItemsUseCase.invoke(path, 0, any()) } returns
            Result.failure(RuntimeException("browse failed"))
        val vm = createViewModel(path)
        assertTrue("use case failure must show Error state", vm.uiState.value is FileBrowserUiState.Error)
    }

    // ========== navigation stack ==========

    @Test
    fun `startPath canNavigateUp is false at initial position`() = runTest {
        val vm = createViewModel(ResourcePath.FileSystem("/storage/emulated/0/Downloads"))
        val state = vm.uiState.value as FileBrowserUiState.Success
        assertFalse("canNavigateUp must be false at startPath root", state.canNavigateUp)
    }

    @Test
    fun `navigateUp at startPath root is no-op and does not call browseItemsUseCase again`() = runTest {
        val path = ResourcePath.FileSystem("/storage/emulated/0/Downloads")
        val vm = createViewModel(path)
        vm.navigateUp()
        val state = vm.uiState.value as FileBrowserUiState.Success
        assertFalse("Still cannot navigate up after no-op navigateUp", state.canNavigateUp)
        coVerify(exactly = 1) { browseItemsUseCase.invoke(path, 0, any()) }
    }

    @Test
    fun `multiple navigateUp calls at root are all no-ops`() = runTest {
        val path = ResourcePath.FileSystem("/storage/emulated/0/Downloads")
        val vm = createViewModel(path)
        repeat(5) { vm.navigateUp() }
        val state = vm.uiState.value as FileBrowserUiState.Success
        assertFalse("Still at root after 5 navigateUp no-ops", state.canNavigateUp)
        coVerify(exactly = 1) { browseItemsUseCase.invoke(path, 0, any()) }
    }

    @Test
    fun `navigateInto subdir enables canNavigateUp`() = runTest {
        val startPath = ResourcePath.FileSystem("/storage/emulated/0/Downloads")
        val subdirPath = ResourcePath.FileSystem("/storage/emulated/0/Downloads/sub")
        val subdir = BrowsableItem.FileSystemDirectory(
            path = subdirPath, name = "sub", sizeBytes = 0L, lastModified = 0L
        )
        coEvery { browseItemsUseCase.invoke(startPath, 0, any()) } returns
            Result.success(BrowseResult.Complete(listOf(subdir)))
        coEvery { browseItemsUseCase.invoke(subdirPath, 0, any()) } returns
            Result.success(BrowseResult.Complete(emptyList()))
        val vm = createViewModel(startPath)
        vm.navigateInto(subdir)
        val state = vm.uiState.value as FileBrowserUiState.Success
        assertTrue("canNavigateUp must be true after navigateInto", state.canNavigateUp)
    }

    @Test
    fun `navigateInto then navigateUp returns to startPath`() = runTest {
        val startPath = ResourcePath.FileSystem("/storage/emulated/0/Downloads")
        val subdirPath = ResourcePath.FileSystem("/storage/emulated/0/Downloads/sub")
        val subdir = BrowsableItem.FileSystemDirectory(
            path = subdirPath, name = "sub", sizeBytes = 0L, lastModified = 0L
        )
        coEvery { browseItemsUseCase.invoke(startPath, 0, any()) } returns
            Result.success(BrowseResult.Complete(listOf(subdir)))
        coEvery { browseItemsUseCase.invoke(subdirPath, 0, any()) } returns
            Result.success(BrowseResult.Complete(emptyList()))
        val vm = createViewModel(startPath)
        vm.navigateInto(subdir)
        vm.navigateUp()
        val state = vm.uiState.value as FileBrowserUiState.Success
        assertFalse("Back at startPath root: canNavigateUp must be false", state.canNavigateUp)
        coVerify(exactly = 2) { browseItemsUseCase.invoke(startPath, 0, any()) }
    }

    @Test
    fun `deep navigation then full navigateUp returns to startPath`() = runTest {
        val root = ResourcePath.FileSystem("/a")
        val level2 = ResourcePath.FileSystem("/a/b")
        val level3 = ResourcePath.FileSystem("/a/b/c")
        val dir2 = BrowsableItem.FileSystemDirectory(path = level2, name = "b", sizeBytes = 0L, lastModified = 0L)
        val dir3 = BrowsableItem.FileSystemDirectory(path = level3, name = "c", sizeBytes = 0L, lastModified = 0L)
        coEvery { browseItemsUseCase.invoke(root, 0, any()) } returns
            Result.success(BrowseResult.Complete(listOf(dir2)))
        coEvery { browseItemsUseCase.invoke(level2, 0, any()) } returns
            Result.success(BrowseResult.Complete(listOf(dir3)))
        coEvery { browseItemsUseCase.invoke(level3, 0, any()) } returns
            Result.success(BrowseResult.Complete(emptyList()))
        val vm = createViewModel(root)
        vm.navigateInto(dir2)
        vm.navigateInto(dir3)
        assertTrue("canNavigateUp at depth 3", (vm.uiState.value as FileBrowserUiState.Success).canNavigateUp)
        vm.navigateUp()
        vm.navigateUp()
        assertFalse("Back at startPath after 2 navigateUp", (vm.uiState.value as FileBrowserUiState.Success).canNavigateUp)
    }

    // ========== ArchiveEntry startPath navigation ==========

    @Test
    fun `startPath ArchiveEntry canNavigateUp is false at archive root`() = runTest {
        val rootEntry = ResourcePath.ArchiveEntry("/sdcard/test.zip", "")
        val vm = createViewModel(rootEntry)
        assertFalse("canNavigateUp must be false at archive root startPath",
            (vm.uiState.value as FileBrowserUiState.Success).canNavigateUp)
    }

    @Test
    fun `startPath ArchiveEntry navigateInto subdir enables canNavigateUp`() = runTest {
        val archivePath = "/sdcard/test.zip"
        val rootEntry = ResourcePath.ArchiveEntry(archivePath, "")
        val subdirEntry = ResourcePath.ArchiveEntry(archivePath, "subdir/")
        val subdir = BrowsableItem.ArchiveDirectory(
            path = subdirEntry, name = "subdir", sizeBytes = 0L, lastModified = 0L,
            archivePath = subdirEntry
        )
        coEvery { browseItemsUseCase.invoke(rootEntry, 0, any()) } returns
            Result.success(BrowseResult.Complete(listOf(subdir)))
        coEvery { browseItemsUseCase.invoke(subdirEntry, 0, any()) } returns
            Result.success(BrowseResult.Complete(emptyList()))
        val vm = createViewModel(rootEntry)
        vm.navigateInto(subdir)
        assertTrue("canNavigateUp after entering archive subdir",
            (vm.uiState.value as FileBrowserUiState.Success).canNavigateUp)
    }

    @Test
    fun `startPath ArchiveEntry navigateInto then navigateUp returns to archive root`() = runTest {
        val archivePath = "/sdcard/test.zip"
        val rootEntry = ResourcePath.ArchiveEntry(archivePath, "")
        val subdirEntry = ResourcePath.ArchiveEntry(archivePath, "subdir/")
        val subdir = BrowsableItem.ArchiveDirectory(
            path = subdirEntry, name = "subdir", sizeBytes = 0L, lastModified = 0L,
            archivePath = subdirEntry
        )
        coEvery { browseItemsUseCase.invoke(rootEntry, 0, any()) } returns
            Result.success(BrowseResult.Complete(listOf(subdir)))
        coEvery { browseItemsUseCase.invoke(subdirEntry, 0, any()) } returns
            Result.success(BrowseResult.Complete(emptyList()))
        val vm = createViewModel(rootEntry)
        vm.navigateInto(subdir)
        vm.navigateUp()
        assertFalse("Back at archive root: canNavigateUp must be false",
            (vm.uiState.value as FileBrowserUiState.Success).canNavigateUp)
        coVerify(exactly = 2) { browseItemsUseCase.invoke(rootEntry, 0, any()) }
    }

    @Test
    fun `navigateToPath resets stack so canNavigateUp is false`() = runTest {
        val startPath = ResourcePath.FileSystem("/storage/emulated/0/Downloads")
        val newPath = ResourcePath.FileSystem("/storage/emulated/0/Documents")
        coEvery { browseItemsUseCase.invoke(newPath, 0, any()) } returns
            Result.success(BrowseResult.Complete(emptyList()))
        val vm = createViewModel(startPath)
        vm.navigateToPath(newPath)
        val state = vm.uiState.value as FileBrowserUiState.Success
        assertFalse("navigateToPath resets stack: canNavigateUp must be false", state.canNavigateUp)
        coVerify { browseItemsUseCase.invoke(newPath, 0, any()) }
    }
}
