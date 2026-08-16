package app.otter.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.BrowseResult
import app.otter.domain.model.ResourcePath
import app.otter.domain.usecase.BrowseItemsUseCase
import app.otter.domain.usecase.BrowsingUseCases
import app.otter.domain.usecase.GetFolderCountsUseCase
import app.otter.service.ExtractionCoordinator
import app.otter.service.ExtractionEventBus
import app.otter.service.ExtractionQueue
import app.otter.ui.theme.OtterTheme
import app.otter.ui.viewmodel.FileBrowserViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose instrumented tests for FileBrowserScreen UI state branches.
 *
 * Covers Error, Empty, and Extracting states of FileBrowserScreen.
 *
 * CRITICAL: Never call Dispatchers.setMain() here. Compose tests rely on
 * AndroidUiDispatcher.Main's frame clock for waitForIdle(). Only inject
 * testDispatcher as ioDispatcher into the ViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class FileBrowserScreenUiStateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val mockFolderCountsUseCase = mockk<GetFolderCountsUseCase>(relaxed = true)

    // ========== Test 1: ErrorView rendered on browse failure ==========

    @Test
    fun errorState_browseFailure_rendersErrorMessage() {
        val errorMessage = "Cannot read archive: file not found"
        val mockUseCase = mockk<BrowseItemsUseCase>()
        coEvery { mockUseCase.invoke(any(), any(), any()) } returns Result.failure(
            Exception(errorMessage)
        )

        val viewModel = FileBrowserViewModel(
            browsingUseCases = BrowsingUseCases(mockUseCase, mockFolderCountsUseCase),
            ioDispatcher = testDispatcher,
            extraction = ExtractionCoordinator(ExtractionEventBus(), ExtractionQueue())
        )

        composeTestRule.setContent {
            OtterTheme {
                FileBrowserScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Error").assertIsDisplayed()
        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
    }

    // ========== Test 2: EmptyView on empty directory ==========

    @Test
    fun successState_emptyItemList_rendersEmptyState() {
        val mockUseCase = mockk<BrowseItemsUseCase>()
        coEvery { mockUseCase.invoke(any(), any(), any()) } returns Result.success(
            BrowseResult.Complete(emptyList())
        )

        val viewModel = FileBrowserViewModel(
            browsingUseCases = BrowsingUseCases(mockUseCase, mockFolderCountsUseCase),
            ioDispatcher = testDispatcher,
            extraction = ExtractionCoordinator(ExtractionEventBus(), ExtractionQueue())
        )

        composeTestRule.setContent {
            OtterTheme {
                FileBrowserScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("No files to display").assertIsDisplayed()
    }

    // ========== Test 3: Extract-all FAB tap transitions to Extracting state ==========

    @Test
    fun extractAllFab_tapInArchive_transitionsToExtractingState() {
        val archiveEntry = ResourcePath.ArchiveEntry("/test/test.zip", "")
        val mockUseCase = mockk<BrowseItemsUseCase>()
        coEvery { mockUseCase.invoke(any(), any(), any()) } returns Result.success(
            BrowseResult.Complete(
                listOf(
                    // ArchiveFileEntry triggers isInArchive = true → shows "Extract all visible files" FAB
                    BrowsableItem.ArchiveFileEntry(
                        path = ResourcePath.ArchiveEntry("/test/test.zip", "file.txt"),
                        name = "file.txt",
                        sizeBytes = 512L,
                        lastModified = 0L,
                        archivePath = ResourcePath.ArchiveEntry("/test/test.zip", "file.txt"),
                        mimeType = "text/plain"
                    ),
                    // ArchiveFile is picked up by filterIsInstance<ArchiveFile>() in onExtractAllVisible
                    BrowsableItem.ArchiveFile(
                        path = archiveEntry,
                        name = "test.zip",
                        sizeBytes = 1024L,
                        lastModified = 0L,
                        archivePath = archiveEntry,
                        mimeType = "application/zip"
                    )
                )
            )
        )

        // Prevent processNext from starting ExtractionService during the test
        val extractionQueue = spyk(ExtractionQueue())
        every { extractionQueue.processNext(any()) } returns false

        val viewModel = FileBrowserViewModel(
            browsingUseCases = BrowsingUseCases(mockUseCase, mockFolderCountsUseCase),
            ioDispatcher = testDispatcher,
            extraction = ExtractionCoordinator(ExtractionEventBus(), extractionQueue)
        )

        composeTestRule.setContent {
            OtterTheme {
                FileBrowserScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Extract all visible files").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Extract all visible files").performClick()
        composeTestRule.waitForIdle()

        // State transitions to Extracting("1 files") → ExtractionScreen shows "Extracting 1 files"
        composeTestRule.onNodeWithText("Extracting 1 files").assertIsDisplayed()
    }

    // ========== Test 4: Loading state renders without crashing ==========

    @Test
    fun loadingState_rendersFallbackTitleWithoutCrashing() {
        val mockUseCase = mockk<BrowseItemsUseCase>()
        val neverCompletes = CompletableDeferred<Result<BrowseResult>>()
        coEvery { mockUseCase.invoke(any(), any(), any()) } coAnswers { neverCompletes.await() }

        val viewModel = FileBrowserViewModel(
            browsingUseCases = BrowsingUseCases(mockUseCase, mockFolderCountsUseCase),
            ioDispatcher = testDispatcher,
            extraction = ExtractionCoordinator(ExtractionEventBus(), ExtractionQueue())
        )

        composeTestRule.setContent {
            OtterTheme {
                FileBrowserScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // Loading is neither Success, Error nor Extracting -> top bar falls back to "File Browser"
        composeTestRule.onNodeWithText("File Browser").assertIsDisplayed()
    }

    // ========== Test 5: Success state with mixed item types renders every item ==========

    @Test
    fun successState_mixedItemTypes_rendersAllItemNames() {
        val mockUseCase = mockk<BrowseItemsUseCase>()
        coEvery { mockUseCase.invoke(any(), any(), any()) } returns Result.success(
            BrowseResult.Complete(
                listOf(
                    BrowsableItem.FileSystemDirectory(
                        path = ResourcePath.FileSystem("/sdcard/Documents"),
                        name = "Documents",
                        sizeBytes = 0L,
                        lastModified = 0L
                    ),
                    BrowsableItem.FileSystemFile(
                        path = ResourcePath.FileSystem("/sdcard/notes.txt"),
                        name = "notes.txt",
                        sizeBytes = 128L,
                        lastModified = 0L,
                        mimeType = "text/plain"
                    ),
                    BrowsableItem.ArchiveFile(
                        path = ResourcePath.ArchiveEntry("/sdcard/test.zip", ""),
                        name = "test.zip",
                        sizeBytes = 2048L,
                        lastModified = 0L,
                        archivePath = ResourcePath.ArchiveEntry("/sdcard/test.zip", ""),
                        mimeType = "application/zip"
                    )
                )
            )
        )

        val viewModel = FileBrowserViewModel(
            browsingUseCases = BrowsingUseCases(mockUseCase, mockFolderCountsUseCase),
            ioDispatcher = testDispatcher,
            extraction = ExtractionCoordinator(ExtractionEventBus(), ExtractionQueue())
        )

        composeTestRule.setContent {
            OtterTheme {
                FileBrowserScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Documents").assertIsDisplayed()
        composeTestRule.onNodeWithText("notes.txt").assertIsDisplayed()
        composeTestRule.onNodeWithText("test.zip").assertIsDisplayed()
    }

    // ========== Test 6: breadcrumb / navigate-up depth state ==========

    @Test
    fun successState_atRoot_navigateUpIsDisabled() {
        val mockUseCase = mockk<BrowseItemsUseCase>()
        coEvery { mockUseCase.invoke(any(), any(), any()) } returns Result.success(
            BrowseResult.Complete(emptyList())
        )

        val viewModel = FileBrowserViewModel(
            browsingUseCases = BrowsingUseCases(mockUseCase, mockFolderCountsUseCase),
            ioDispatcher = testDispatcher,
            extraction = ExtractionCoordinator(ExtractionEventBus(), ExtractionQueue())
        )

        composeTestRule.setContent {
            OtterTheme {
                FileBrowserScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // BrowseResult carries no canNavigateUp signal on its own; the ViewModel derives it
        // from the navigation stack, which is empty right after the initial browse.
        composeTestRule.onNodeWithContentDescription("Navigate up").assertIsNotEnabled()
    }

    @Test
    fun successState_afterNavigatingInto_navigateUpIsEnabled() {
        val archiveEntry = ResourcePath.ArchiveEntry("/test/test.zip", "")
        val mockUseCase = mockk<BrowseItemsUseCase>()
        coEvery { mockUseCase.invoke(any(), any(), any()) } returns Result.success(
            BrowseResult.Complete(
                listOf(
                    BrowsableItem.ArchiveFile(
                        path = archiveEntry,
                        name = "test.zip",
                        sizeBytes = 1024L,
                        lastModified = 0L,
                        archivePath = archiveEntry,
                        mimeType = "application/zip"
                    )
                )
            )
        )

        val viewModel = FileBrowserViewModel(
            browsingUseCases = BrowsingUseCases(mockUseCase, mockFolderCountsUseCase),
            ioDispatcher = testDispatcher,
            extraction = ExtractionCoordinator(ExtractionEventBus(), ExtractionQueue())
        )

        composeTestRule.setContent {
            OtterTheme {
                FileBrowserScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // Navigate into the archive file shown at root -> navigation stack now has one entry.
        composeTestRule.onNodeWithText("test.zip").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Navigate up").assertIsEnabled()
    }
}
