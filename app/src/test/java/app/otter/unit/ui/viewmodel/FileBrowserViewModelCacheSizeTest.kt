package app.otter.ui.viewmodel

import app.otter.domain.model.BrowseResult
import app.otter.domain.model.ResourcePath
import app.otter.domain.model.UserSettings
import app.otter.domain.repository.SettingsRepository
import app.otter.domain.usecase.BrowsingUseCases
import app.otter.service.ExtractionCoordinator
import io.mockk.coEvery
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FileBrowserViewModelCacheSizeTest : BaseFileBrowserViewModelTest() {

    private class FakeSettingsRepository(initial: UserSettings) : SettingsRepository {
        val state = MutableStateFlow(initial)
        override val settings = state
        override suspend fun setCacheWindowSize(size: Int) {
            state.value = state.value.copy(cacheWindowSize = size)
        }
        override suspend fun setFileCategoryFilter(
            category: app.otter.domain.model.FileCategory,
            state2: app.otter.domain.model.FileCategoryFilterState?
        ) {
        }
    }

    @Test
    fun `cache window respects custom size from settings on scroll`() = runTest {
        val items = createMockArchiveItems(1000)
        coEvery { browseItemsUseCase.invoke(any(), any(), any()) } returns Result.success(
            BrowseResult.Paginated(items = items.take(100), hasMore = true, totalEstimate = 1000, nextOffset = 100)
        )
        val settingsRepository = FakeSettingsRepository(UserSettings(cacheWindowSize = 30))
        val vm = FileBrowserViewModel(
            BrowsingUseCases(browseItemsUseCase, getFolderCountsUseCase), testDispatcher, ExtractionCoordinator(eventBus, extractionQueue),
            settingsRepository = settingsRepository,
            startPath = ResourcePath.FileSystem("/storage/emulated/0")
        )

        vm.onScrollPositionChanged(firstVisibleItemIndex = 50)
        val state = vm.uiState.value as FileBrowserUiState.Success

        // Window is ±30 around center 50 (raw absolute index), so displayed items must not
        // exceed roughly 2*30 + one extra loaded page (100) worth of slack.
        assertTrue("Displayed window must respect the custom cacheWindowSize=30", state.items.size <= 30 * 2 + 100)
    }

    @Test
    fun `live settings update changes window size without recreating the ViewModel`() = runTest {
        val items = createMockArchiveItems(1000)
        coEvery { browseItemsUseCase.invoke(any(), any(), any()) } returns Result.success(
            BrowseResult.Paginated(items = items.take(100), hasMore = true, totalEstimate = 1000, nextOffset = 100)
        )
        val settingsRepository = FakeSettingsRepository(UserSettings(cacheWindowSize = 100))
        val vm = FileBrowserViewModel(
            BrowsingUseCases(browseItemsUseCase, getFolderCountsUseCase), testDispatcher, ExtractionCoordinator(eventBus, extractionQueue),
            settingsRepository = settingsRepository,
            startPath = ResourcePath.FileSystem("/storage/emulated/0")
        )

        settingsRepository.state.value = UserSettings(cacheWindowSize = 20)
        vm.onScrollPositionChanged(firstVisibleItemIndex = 50)
        val state = vm.uiState.value as FileBrowserUiState.Success

        assertTrue("Live update to cacheWindowSize=20 must shrink the cache window", state.items.size <= 20 * 2 + 100)
    }

    @Test
    fun `omitting settingsRepository falls back to default no-op repository with DEFAULT_HALF_WINDOW`() = runTest {
        // Hilt always injects a real SettingsRepository in production; this only verifies the
        // default parameter's fallback (NoOpSettingsRepository) behaves like an unconfigured
        // UserSettings() — DEFAULT_HALF_WINDOW=100 — without needing to pass a repository at all.
        val items = createMockArchiveItems(1000)
        coEvery { browseItemsUseCase.invoke(any(), any(), any()) } returns Result.success(
            BrowseResult.Paginated(items = items.take(100), hasMore = true, totalEstimate = 1000, nextOffset = 100)
        )
        val vm = FileBrowserViewModel(
            BrowsingUseCases(browseItemsUseCase, getFolderCountsUseCase), testDispatcher, ExtractionCoordinator(eventBus, extractionQueue),
            startPath = ResourcePath.FileSystem("/storage/emulated/0")
        )

        vm.onScrollPositionChanged(firstVisibleItemIndex = 50)
        val state = vm.uiState.value as FileBrowserUiState.Success

        assertTrue(
            "No settingsRepository passed must still respect DEFAULT_HALF_WINDOW=${FileBrowserViewModel.DEFAULT_HALF_WINDOW}",
            state.items.size <= FileBrowserViewModel.DEFAULT_HALF_WINDOW * 2 + 100
        )
    }
}
