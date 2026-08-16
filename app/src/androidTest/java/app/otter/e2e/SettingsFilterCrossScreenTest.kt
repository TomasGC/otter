package app.otter.e2e

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.BrowseResult
import app.otter.domain.model.FileCategory
import app.otter.domain.model.FileCategoryFilterState
import app.otter.domain.model.ResourcePath
import app.otter.domain.model.UserSettings
import app.otter.domain.repository.SettingsRepository
import app.otter.domain.usecase.BrowseItemsUseCase
import app.otter.domain.usecase.BrowsingUseCases
import app.otter.domain.usecase.GetFolderCountsUseCase
import app.otter.service.ExtractionCoordinator
import app.otter.service.ExtractionEventBus
import app.otter.service.ExtractionQueue
import app.otter.ui.screen.FileBrowserScreen
import app.otter.ui.screen.SettingsScreen
import app.otter.ui.theme.OtterTheme
import app.otter.ui.viewmodel.FileBrowserViewModel
import app.otter.ui.viewmodel.SettingsViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the "persisted default always replaces an active session override" behavior
 * through the actual two-screen structure BrowserActivity wires in production (mutually
 * exclusive FileBrowserScreen/SettingsScreen sharing one SettingsRepository), rather than
 * only at the ViewModel unit-test level.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SettingsFilterCrossScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

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

    @Test
    fun changingDefaultInSettings_replacesActiveSessionOverrideOnReturnToBrowser() {
        val repository = FakeSettingsRepository()
        val mockUseCase = mockk<BrowseItemsUseCase>()
        coEvery { mockUseCase.invoke(any(), any(), any()) } returns Result.success(
            BrowseResult.Complete(
                listOf(
                    BrowsableItem.ArchiveFile(
                        path = ResourcePath.ArchiveEntry("/test/archive.zip", ""),
                        name = "archive.zip",
                        sizeBytes = 1024L,
                        lastModified = 0L,
                        archivePath = ResourcePath.ArchiveEntry("/test/archive.zip", ""),
                        mimeType = "application/zip"
                    ),
                    BrowsableItem.FileSystemFile(
                        path = ResourcePath.FileSystem("/test/document.txt"),
                        name = "document.txt",
                        sizeBytes = 256L,
                        lastModified = 0L,
                        mimeType = "text/plain"
                    )
                )
            )
        )
        val browserViewModel = FileBrowserViewModel(
            BrowsingUseCases(mockUseCase, mockk<GetFolderCountsUseCase>(relaxed = true)),
            UnconfinedTestDispatcher(),
            ExtractionCoordinator(ExtractionEventBus(), ExtractionQueue()),
            settingsRepository = repository,
            startPath = ResourcePath.FileSystem("/storage/emulated/0")
        )
        val settingsViewModel = SettingsViewModel(repository)

        composeTestRule.setContent {
            OtterTheme {
                var showSettings by remember { mutableStateOf(false) }
                if (showSettings) {
                    SettingsScreen(onNavigateBack = { showSettings = false }, viewModel = settingsViewModel)
                } else {
                    FileBrowserScreen(onOpenSettings = { showSettings = true }, viewModel = browserViewModel)
                }
            }
        }
        composeTestRule.waitForIdle()

        // Set a session override on the browser: ARCHIVE-only via the filter popup.
        composeTestRule.onNodeWithContentDescription("File type filter").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("ARCHIVE").performClick()
        composeTestRule.waitForIdle()
        Espresso.pressBack()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("archive.zip").assertIsDisplayed()
        composeTestRule.onNodeWithText("document.txt").assertDoesNotExist()

        // Go to Settings, change the persisted default to DOCUMENT-only.
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("DOCUMENT").performClick()
        composeTestRule.waitForIdle()

        // Back to the browser: the new default must have replaced the session override.
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("document.txt").assertIsDisplayed()
        composeTestRule.onNodeWithText("archive.zip").assertDoesNotExist()
    }
}
