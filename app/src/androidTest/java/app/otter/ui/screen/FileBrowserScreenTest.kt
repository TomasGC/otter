package app.otter.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.BrowseResult
import app.otter.domain.model.ResourcePath
import app.otter.domain.usecase.BrowseItemsUseCase
import app.otter.service.ExtractionEventBus
import app.otter.service.ExtractionQueue
import app.otter.ui.theme.OtterTheme
import app.otter.ui.viewmodel.FileBrowserViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class FileBrowserScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: FileBrowserViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        val mockUseCase = mockk<BrowseItemsUseCase>()
        coEvery { mockUseCase.invoke(any(), any(), any()) } returns Result.success(
            BrowseResult.Complete(
                listOf(
                    BrowsableItem.ArchiveFile(
                        path = ResourcePath.ArchiveEntry("/test/test.zip", ""),
                        name = "test.zip",
                        sizeBytes = 1024L,
                        lastModified = 0L,
                        archivePath = ResourcePath.ArchiveEntry("/test/test.zip", ""),
                        mimeType = "application/zip"
                    ),
                    BrowsableItem.FileSystemDirectory(
                        path = ResourcePath.FileSystem("/test/folder1"),
                        name = "folder1",
                        sizeBytes = 0L,
                        lastModified = 0L
                    ),
                    BrowsableItem.FileSystemFile(
                        path = ResourcePath.FileSystem("/test/document.txt"),
                        name = "document.txt",
                        sizeBytes = 512L,
                        lastModified = 0L,
                        mimeType = "text/plain"
                    )
                )
            )
        )

        viewModel = FileBrowserViewModel(
            browseItemsUseCase = mockUseCase,
            ioDispatcher = testDispatcher,
            eventBus = ExtractionEventBus(),
            extractionQueue = ExtractionQueue()
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialScreen_displaysFileList() {
        composeTestRule.setContent {
            OtterTheme {
                FileBrowserScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("test.zip").assertIsDisplayed()
        composeTestRule.onNodeWithText("folder1").assertIsDisplayed()
        composeTestRule.onNodeWithText("document.txt").assertIsDisplayed()
    }

    @Test
    fun clickOnArchive_showsConfirmationDialog() {
        composeTestRule.setContent {
            OtterTheme {
                FileBrowserScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("test.zip").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Extract archive?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Extract").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun clickOnDirectory_navigatesIntoDirectory() {
        composeTestRule.setContent {
            OtterTheme {
                FileBrowserScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("folder1").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Extract archive?").assertIsNotDisplayed()
    }

    @Test
    fun longPressOnFile_entersSelectionMode() {
        composeTestRule.setContent {
            OtterTheme {
                FileBrowserScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("test.zip").performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("1 selected").assertIsDisplayed()
        composeTestRule.onNodeWithText("Select All").assertIsDisplayed()
    }

    @Test
    fun inSelectionMode_clickSelectAll_selectsAllArchives() {
        composeTestRule.setContent {
            OtterTheme {
                FileBrowserScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("test.zip").performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Select All").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("1 selected").assertIsDisplayed()
    }

    @Test
    fun inSelectionMode_clickClose_exitsSelectionMode() {
        composeTestRule.setContent {
            OtterTheme {
                FileBrowserScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("test.zip").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1 selected").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Exit selection mode").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("1 selected").assertIsNotDisplayed()
        composeTestRule.onNodeWithText("Select All").assertIsNotDisplayed()
    }

    @Test
    fun confirmDialog_clickExtract_startsExtraction() {
        composeTestRule.setContent {
            OtterTheme {
                FileBrowserScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("test.zip").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Extract archive?").assertIsDisplayed()

        composeTestRule.onNodeWithText("Extract").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Extract archive?").assertIsNotDisplayed()
    }

    @Test
    fun confirmDialog_clickCancel_dismissesDialog() {
        composeTestRule.setContent {
            OtterTheme {
                FileBrowserScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("test.zip").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Extract archive?").assertIsDisplayed()

        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Extract archive?").assertIsNotDisplayed()
        composeTestRule.onNodeWithText("test.zip").assertIsDisplayed()
    }
}
