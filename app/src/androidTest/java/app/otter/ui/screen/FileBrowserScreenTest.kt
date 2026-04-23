package app.otter.ui.screen

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otter.domain.model.FileItem
import app.otter.domain.model.ResourcePath
import app.otter.domain.usecase.BrowseFilesUseCase
import app.otter.ui.theme.OtterTheme
import app.otter.ui.viewmodel.FileBrowserViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class FileBrowserScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createComposeRule()

    private lateinit var browseFilesUseCase: BrowseFilesUseCase
    private lateinit var viewModel: FileBrowserViewModel

    @Before
    fun setup() {
        hiltRule.inject()

        browseFilesUseCase = mockk()

        // Mock file list with archives and directories
        val mockFiles = listOf(
            FileItem(
                path = ResourcePath.from("file:///test.zip"),
                name = "test.zip",
                isDirectory = false,
                sizeBytes = 1024L,
                lastModified = System.currentTimeMillis(),
                mimeType = "application/zip"
            ),
            FileItem(
                path = ResourcePath.from("file:///folder1"),
                name = "folder1",
                isDirectory = true,
                sizeBytes = null,
                lastModified = System.currentTimeMillis(),
                mimeType = null
            ),
            FileItem(
                path = ResourcePath.from("file:///document.txt"),
                name = "document.txt",
                isDirectory = false,
                sizeBytes = 512L,
                lastModified = System.currentTimeMillis(),
                mimeType = "text/plain"
            )
        )

        coEvery { browseFilesUseCase(any()) } returns Result.success(mockFiles)
        viewModel = FileBrowserViewModel(
            browseFilesUseCase = browseFilesUseCase,
            eventBus = app.otter.service.ExtractionEventBus(),
            extractionQueue = app.otter.service.ExtractionQueue(),
        )
    }

    @Test
    fun initialScreen_displaysFileList() {
        // When
        composeTestRule.setContent {
            OtterTheme {
                FileBrowserScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // Then - Files are displayed
        composeTestRule.onNodeWithText("test.zip").assertIsDisplayed()
        composeTestRule.onNodeWithText("folder1").assertIsDisplayed()
        composeTestRule.onNodeWithText("document.txt").assertIsDisplayed()
    }

    @Test
    fun clickOnArchive_showsConfirmationDialog() {
        // Given
        composeTestRule.setContent {
            OtterTheme {
                FileBrowserScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // When - Click on archive
        composeTestRule.onNodeWithText("test.zip").performClick()
        composeTestRule.waitForIdle()

        // Then - Confirmation dialog appears
        composeTestRule.onNodeWithText("Extract archive?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Do you want to extract this archive?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Extract").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun clickOnDirectory_navigatesIntoDirectory() {
        // Given
        composeTestRule.setContent {
            OtterTheme {
                FileBrowserScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // Mock navigation into directory
        coEvery { browseFilesUseCase(any()) } returns Result.success(emptyList())

        // When - Click on directory
        composeTestRule.onNodeWithText("folder1").performClick()
        composeTestRule.waitForIdle()

        // Then - Should navigate (we'd need to verify ViewModel state)
        // For now, just verify no crash and no dialog shown
        composeTestRule.onNodeWithText("Extract archive?").assertDoesNotExist()
    }

    @Test
    fun longPressOnFile_entersSelectionMode() {
        // Given
        composeTestRule.setContent {
            OtterTheme {
                FileBrowserScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // When - Long press on a file
        composeTestRule.onNodeWithText("test.zip").performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        // Then - Selection mode UI appears
        composeTestRule.onNodeWithText("1 selected").assertIsDisplayed()
        composeTestRule.onNodeWithText("Select All").assertIsDisplayed()
    }

    @Test
    fun inSelectionMode_clickSelectAll_selectsAllArchives() {
        // Given - Enter selection mode
        composeTestRule.setContent {
            OtterTheme {
                FileBrowserScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("test.zip").performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        // When - Click "Select All"
        composeTestRule.onNodeWithText("Select All").performClick()
        composeTestRule.waitForIdle()

        // Then - All archives selected (only test.zip is an archive)
        composeTestRule.onNodeWithText("1 selected").assertIsDisplayed()
    }

    @Test
    fun inSelectionMode_clickClose_exitsSelectionMode() {
        // Given - Enter selection mode
        composeTestRule.setContent {
            OtterTheme {
                FileBrowserScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("test.zip").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1 selected").assertIsDisplayed()

        // When - Click close button (X icon in navigation)
        composeTestRule.onNodeWithContentDescription("Exit selection mode").performClick()
        composeTestRule.waitForIdle()

        // Then - Back to normal mode
        composeTestRule.onNodeWithText("1 selected").assertDoesNotExist()
        composeTestRule.onNodeWithText("Select All").assertDoesNotExist()
    }

    @Test
    fun confirmDialog_clickExtract_startsExtraction() {
        // Given - Dialog is shown
        composeTestRule.setContent {
            OtterTheme {
                FileBrowserScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("test.zip").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Extract archive?").assertIsDisplayed()

        // When - Click Extract
        composeTestRule.onNodeWithText("Extract").performClick()
        composeTestRule.waitForIdle()

        // Then - Extraction UI should appear (progress bar)
        // Note: This would need ExtractionService to be mocked or a fake implementation
        // For now, just verify dialog is dismissed
        composeTestRule.onNodeWithText("Extract archive?").assertDoesNotExist()
    }

    @Test
    fun confirmDialog_clickCancel_dismissesDialog() {
        // Given - Dialog is shown
        composeTestRule.setContent {
            OtterTheme {
                FileBrowserScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("test.zip").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Extract archive?").assertIsDisplayed()

        // When - Click Cancel
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()

        // Then - Dialog is dismissed
        composeTestRule.onNodeWithText("Extract archive?").assertDoesNotExist()
        composeTestRule.onNodeWithText("test.zip").assertIsDisplayed()
    }
}
