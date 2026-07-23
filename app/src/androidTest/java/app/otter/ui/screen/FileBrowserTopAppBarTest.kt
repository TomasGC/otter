package app.otter.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.ResourcePath
import app.otter.ui.theme.OtterTheme
import app.otter.ui.viewmodel.FileBrowserUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Isolated coverage for FileBrowserTopAppBar's state-derived rendering, independent of the
 * full FileBrowserScreen + ViewModel flow already exercised by FileBrowserScreenUiStateTest.
 */
@RunWith(AndroidJUnit4::class)
class FileBrowserTopAppBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val archiveEntry = ResourcePath.ArchiveEntry("/test/test.zip", "")

    private fun setBar(
        uiState: FileBrowserUiState,
        onExtractAllVisible: () -> Unit = {},
        onNavigateUp: () -> Unit = {},
        onExitSelectionMode: () -> Unit = {},
        onSelectAll: () -> Unit = {},
        onExtractSelected: () -> Unit = {},
        onToggleFilter: () -> Unit = {},
        onCycleSortOrder: () -> Unit = {},
        onRefresh: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            OtterTheme {
                FileBrowserTopAppBar(
                    uiState = uiState,
                    onExtractAllVisible = onExtractAllVisible,
                    onNavigateUp = onNavigateUp,
                    onExitSelectionMode = onExitSelectionMode,
                    onSelectAll = onSelectAll,
                    onExtractSelected = onExtractSelected,
                    onToggleFilter = onToggleFilter,
                    onCycleSortOrder = onCycleSortOrder,
                    onRefresh = onRefresh
                )
            }
        }
    }

    private fun successState(
        items: List<BrowsableItem> = emptyList(),
        canNavigateUp: Boolean = false,
        isSelectionMode: Boolean = false,
        selectedCount: Int = 0,
    ) = FileBrowserUiState.Success(
        items = items,
        currentPath = "/sdcard/Documents",
        canNavigateUp = canNavigateUp,
        isSelectionMode = isSelectionMode,
        selectedCount = selectedCount
    )

    @Test
    fun normalMode_showsCurrentPathAsTitle() {
        setBar(successState())

        composeTestRule.onNodeWithText("/sdcard/Documents").assertIsDisplayed()
    }

    @Test
    fun selectionMode_showsSelectedCountAsTitle() {
        setBar(successState(isSelectionMode = true, selectedCount = 3))

        composeTestRule.onNodeWithText("3 selected").assertIsDisplayed()
    }

    @Test
    fun selectionMode_showsExitIconInsteadOfNavigateUp() {
        setBar(successState(isSelectionMode = true))

        composeTestRule.onNodeWithContentDescription("Exit selection mode").assertIsDisplayed()
    }

    @Test
    fun normalMode_showsNavigateUpInsteadOfExitIcon() {
        setBar(successState(isSelectionMode = false))

        composeTestRule.onNodeWithContentDescription("Navigate up").assertIsDisplayed()
    }

    @Test
    fun selectionMode_showsSelectAllButton() {
        setBar(successState(isSelectionMode = true))

        composeTestRule.onNodeWithText("Select All").assertIsDisplayed()
    }

    @Test
    fun selectionMode_extractSelectedDisabledWhenNothingSelected() {
        setBar(successState(isSelectionMode = true, selectedCount = 0))

        composeTestRule.onNodeWithContentDescription("Extract selected").assertIsNotEnabled()
    }

    @Test
    fun selectionMode_extractSelectedEnabledWhenItemsSelected() {
        setBar(successState(isSelectionMode = true, selectedCount = 2))

        composeTestRule.onNodeWithContentDescription("Extract selected").assertIsEnabled()
    }

    @Test
    fun normalMode_extractAllVisibleHiddenWhenNotBrowsingArchive() {
        setBar(successState(items = emptyList()))

        composeTestRule.onNodeWithContentDescription("Extract all visible files").assertDoesNotExist()
    }

    @Test
    fun normalMode_extractAllVisibleShownWhenBrowsingArchiveContents() {
        setBar(
            successState(
                items = listOf(
                    BrowsableItem.ArchiveFileEntry(
                        path = archiveEntry,
                        name = "file.txt",
                        sizeBytes = 10L,
                        lastModified = 0L,
                        archivePath = archiveEntry,
                        mimeType = "text/plain"
                    )
                )
            )
        )

        composeTestRule.onNodeWithContentDescription("Extract all visible files").assertIsDisplayed()
    }

    @Test
    fun navigateUp_disabledAtRoot() {
        setBar(successState(canNavigateUp = false))

        composeTestRule.onNodeWithContentDescription("Navigate up").assertIsNotEnabled()
    }

    @Test
    fun navigateUp_enabledWhenNotAtRoot() {
        setBar(successState(canNavigateUp = true))

        composeTestRule.onNodeWithContentDescription("Navigate up").assertIsEnabled()
    }
}
