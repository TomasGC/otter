package app.otter.ui.viewmodel

import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.BrowseResult
import app.otter.domain.model.FileCategory
import app.otter.domain.model.FileCategoryFilterState
import app.otter.domain.model.ResourcePath
import app.otter.domain.model.UserSettings
import app.otter.domain.repository.SettingsRepository
import app.otter.domain.usecase.BrowsingUseCases
import app.otter.service.ExtractionCoordinator
import io.mockk.coEvery
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FileBrowserViewModelCategoryFilterTest : BaseFileBrowserViewModelTest() {

    private class FakeSettingsRepository(initial: UserSettings = UserSettings()) : SettingsRepository {
        val state = MutableStateFlow(initial)
        override val settings = state
        override suspend fun setCacheWindowSize(size: Int) {
            state.value = state.value.copy(cacheWindowSize = size)
        }
        override suspend fun setFileCategoryFilter(category: FileCategory, filterState: FileCategoryFilterState?) {
            val updated = state.value.fileCategoryFilters.toMutableMap()
            if (filterState == null) updated.remove(category) else updated[category] = filterState
            state.value = state.value.copy(fileCategoryFilters = updated)
        }
    }

    private fun mixedItems(): List<BrowsableItem> = listOf(
        createBrowsableItem("folder1", isDirectory = true),
        createBrowsableItem("archive.zip", isArchive = true),
        createBrowsableItem("file.txt", isDirectory = false), // mimeType text/plain -> DOCUMENT
    )

    private fun createViewModel(settingsRepository: SettingsRepository): FileBrowserViewModel {
        coEvery { browseItemsUseCase.invoke(any(), any(), any()) } returns
            Result.success(BrowseResult.Complete(mixedItems()))
        return FileBrowserViewModel(
            BrowsingUseCases(browseItemsUseCase, getFolderCountsUseCase),
            testDispatcher,
            ExtractionCoordinator(eventBus, extractionQueue),
            settingsRepository = settingsRepository,
            startPath = ResourcePath.FileSystem("/storage/emulated/0")
        )
    }

    @Test
    fun `no filters shows all items including directories`() = runTest {
        val vm = createViewModel(FakeSettingsRepository())
        val state = vm.uiState.value as FileBrowserUiState.Success
        assertEquals(3, state.items.size)
    }

    @Test
    fun `include-only filter shows matching category plus all directories`() = runTest {
        val vm = createViewModel(FakeSettingsRepository())
        vm.applyCategoryFilterOverride(mapOf(FileCategory.ARCHIVE to FileCategoryFilterState.INCLUDE))
        val state = vm.uiState.value as FileBrowserUiState.Success
        // Directory always shown + the one archive file. Text file (DOCUMENT) excluded.
        assertEquals(2, state.items.size)
        assertTrue(state.items.any { it is BrowsableItem.FileSystemDirectory })
        assertTrue(state.items.any { it is BrowsableItem.ArchiveFile })
    }

    @Test
    fun `exclude-only filter hides matching category, shows everything else`() = runTest {
        val vm = createViewModel(FakeSettingsRepository())
        vm.applyCategoryFilterOverride(mapOf(FileCategory.ARCHIVE to FileCategoryFilterState.EXCLUDE))
        val state = vm.uiState.value as FileBrowserUiState.Success
        assertEquals(2, state.items.size)
        assertTrue(state.items.none { it is BrowsableItem.ArchiveFile })
    }

    @Test
    fun `mixed include and exclude - include wins`() = runTest {
        val vm = createViewModel(FakeSettingsRepository())
        vm.applyCategoryFilterOverride(
            mapOf(
                FileCategory.ARCHIVE to FileCategoryFilterState.INCLUDE,
                FileCategory.DOCUMENT to FileCategoryFilterState.EXCLUDE,
            )
        )
        val state = vm.uiState.value as FileBrowserUiState.Success
        // Only ARCHIVE (included) + directory shown; DOCUMENT exclude is a no-op since
        // it was already outside the include whitelist.
        assertEquals(2, state.items.size)
        assertTrue(state.items.any { it is BrowsableItem.ArchiveFile })
    }

    @Test
    fun `clearing override with empty map removes all filtering`() = runTest {
        val vm = createViewModel(FakeSettingsRepository())
        vm.applyCategoryFilterOverride(mapOf(FileCategory.ARCHIVE to FileCategoryFilterState.INCLUDE))
        vm.applyCategoryFilterOverride(emptyMap())
        val state = vm.uiState.value as FileBrowserUiState.Success
        assertEquals(3, state.items.size)
    }

    @Test
    fun `changing the persisted default replaces an active session override`() = runTest {
        val settingsRepository = FakeSettingsRepository()
        val vm = createViewModel(settingsRepository)

        // Override to ARCHIVE-only for this session.
        vm.applyCategoryFilterOverride(mapOf(FileCategory.ARCHIVE to FileCategoryFilterState.INCLUDE))
        var state = vm.uiState.value as FileBrowserUiState.Success
        assertEquals(2, state.items.size)
        assertTrue(state.items.any { it is BrowsableItem.ArchiveFile })

        // Default changes live in the background (e.g. user edited Settings screen)
        // AFTER the session override was set — the new default must win.
        settingsRepository.setFileCategoryFilter(FileCategory.DOCUMENT, FileCategoryFilterState.INCLUDE)

        state = vm.uiState.value as FileBrowserUiState.Success
        // No longer ARCHIVE-only — the new default (DOCUMENT-only) replaced the session override.
        assertEquals(2, state.items.size)
        assertTrue(state.items.none { it is BrowsableItem.ArchiveFile })
        assertTrue(state.items.any { it.name == "file.txt" })
    }

    @Test
    fun `default change after session override keeps live-tracking further default changes`() = runTest {
        val settingsRepository = FakeSettingsRepository()
        val vm = createViewModel(settingsRepository)

        vm.applyCategoryFilterOverride(mapOf(FileCategory.ARCHIVE to FileCategoryFilterState.INCLUDE))
        // First default change clears the override and starts live-tracking again.
        settingsRepository.setFileCategoryFilter(FileCategory.DOCUMENT, FileCategoryFilterState.INCLUDE)
        // A second default change must also be reflected — tracking isn't a one-shot unfreeze.
        settingsRepository.setFileCategoryFilter(FileCategory.DOCUMENT, null)
        settingsRepository.setFileCategoryFilter(FileCategory.ARCHIVE, FileCategoryFilterState.EXCLUDE)

        val state = vm.uiState.value as FileBrowserUiState.Success
        assertEquals(2, state.items.size)
        assertTrue(state.items.none { it is BrowsableItem.ArchiveFile })
    }

    @Test
    fun `default filter tracks settings live while no override is active`() = runTest {
        val settingsRepository = FakeSettingsRepository()
        val vm = createViewModel(settingsRepository)

        settingsRepository.setFileCategoryFilter(FileCategory.ARCHIVE, FileCategoryFilterState.INCLUDE)

        val state = vm.uiState.value as FileBrowserUiState.Success
        assertEquals(2, state.items.size)
        assertTrue(state.items.any { it is BrowsableItem.ArchiveFile })
    }

    @Test
    fun `multiple categories set to INCLUDE combine as a whitelist`() = runTest {
        val vm = createViewModel(FakeSettingsRepository())
        vm.applyCategoryFilterOverride(
            mapOf(
                FileCategory.ARCHIVE to FileCategoryFilterState.INCLUDE,
                FileCategory.DOCUMENT to FileCategoryFilterState.INCLUDE,
            )
        )
        val state = vm.uiState.value as FileBrowserUiState.Success
        // Directory (always shown) + archive.zip (ARCHIVE) + file.txt (DOCUMENT).
        assertEquals(3, state.items.size)
    }

    @Test
    fun `multiple categories set to EXCLUDE combine to hide all of them`() = runTest {
        val vm = createViewModel(FakeSettingsRepository())
        vm.applyCategoryFilterOverride(
            mapOf(
                FileCategory.ARCHIVE to FileCategoryFilterState.EXCLUDE,
                FileCategory.DOCUMENT to FileCategoryFilterState.EXCLUDE,
            )
        )
        val state = vm.uiState.value as FileBrowserUiState.Success
        // Only the directory remains — both ARCHIVE and DOCUMENT are excluded.
        assertEquals(1, state.items.size)
        assertTrue(state.items.all { it is BrowsableItem.FileSystemDirectory })
    }

    @Test
    fun `clearing override via empty map still gets replaced by a later default change`() = runTest {
        // Product decision: a persisted default change always wins over an active session
        // override — "Clear" is not exempt, it's still a session override under the hood
        // (an explicit empty map, not null) and gets replaced the same as any other override
        // the moment the default itself changes.
        val settingsRepository = FakeSettingsRepository()
        val vm = createViewModel(settingsRepository)

        vm.applyCategoryFilterOverride(mapOf(FileCategory.ARCHIVE to FileCategoryFilterState.INCLUDE))
        vm.applyCategoryFilterOverride(emptyMap())
        settingsRepository.setFileCategoryFilter(FileCategory.DOCUMENT, FileCategoryFilterState.INCLUDE)

        val state = vm.uiState.value as FileBrowserUiState.Success
        // No longer unfiltered — the new DOCUMENT-only default replaced the cleared override.
        assertEquals(2, state.items.size)
        assertTrue(state.items.none { it is BrowsableItem.ArchiveFile })
    }

    @Test
    fun `category filter applies correctly in paginated mode and stays correct after live default change`() = runTest {
        // Filtering in paginated mode goes through emitVisibleItems() rather than
        // applyFilterAndSort()'s Complete-result branch — the default-overrides-session
        // behavior must hold on that path too, not just for small non-paginated directories.
        val settingsRepository = FakeSettingsRepository()
        coEvery { browseItemsUseCase.invoke(any(), any(), any()) } returns Result.success(
            BrowseResult.Paginated(items = mixedItems(), hasMore = false, totalEstimate = 3, nextOffset = 3)
        )
        val vm = FileBrowserViewModel(
            BrowsingUseCases(browseItemsUseCase, getFolderCountsUseCase),
            testDispatcher,
            ExtractionCoordinator(eventBus, extractionQueue),
            settingsRepository = settingsRepository,
            startPath = ResourcePath.FileSystem("/storage/emulated/0")
        )

        vm.applyCategoryFilterOverride(mapOf(FileCategory.ARCHIVE to FileCategoryFilterState.INCLUDE))
        var state = vm.uiState.value as FileBrowserUiState.Success
        assertEquals(2, state.items.size)
        assertTrue(state.items.any { it is BrowsableItem.ArchiveFile })

        settingsRepository.setFileCategoryFilter(FileCategory.DOCUMENT, FileCategoryFilterState.INCLUDE)

        state = vm.uiState.value as FileBrowserUiState.Success
        assertEquals(2, state.items.size)
        assertTrue(state.items.none { it is BrowsableItem.ArchiveFile })
    }
}
