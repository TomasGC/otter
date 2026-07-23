package app.otter.ui.component

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.ResourcePath
import app.otter.ui.theme.OtterTheme
import app.otter.util.FileFormatters
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowsableItemRowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val file = BrowsableItem.FileSystemFile(
        path = ResourcePath.FileSystem("/sdcard/notes.txt"),
        name = "notes.txt",
        sizeBytes = 512L,
        lastModified = 1_700_000_000_000L,
        mimeType = "text/plain"
    )

    private val directory = BrowsableItem.FileSystemDirectory(
        path = ResourcePath.FileSystem("/sdcard/Documents"),
        name = "Documents",
        sizeBytes = 0L,
        lastModified = 1_700_000_000_000L
    )

    private fun setRow(
        item: BrowsableItem,
        isSelectionMode: Boolean = false,
        isSelected: Boolean = false,
        onClick: () -> Unit = {},
        onLongClick: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            OtterTheme {
                BrowsableItemRow(
                    item = item,
                    isSelectionMode = isSelectionMode,
                    isSelected = isSelected,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
            }
        }
    }

    @Test
    fun displaysItemName() {
        setRow(file)

        composeTestRule.onNodeWithText("notes.txt").assertIsDisplayed()
    }

    @Test
    fun displaysFormattedSizeForFilesOnly() {
        setRow(file)

        composeTestRule.onNodeWithText(FileFormatters.formatFileSize(file.sizeBytes)).assertIsDisplayed()
    }

    @Test
    fun doesNotDisplaySizeForDirectories() {
        setRow(directory)

        composeTestRule.onNodeWithText(FileFormatters.formatFileSize(directory.sizeBytes)).assertDoesNotExist()
    }

    @Test
    fun displaysFormattedDateForBothFilesAndDirectories() {
        setRow(directory)

        composeTestRule.onNodeWithText(FileFormatters.formatDate(directory.lastModified)).assertIsDisplayed()
    }

    @Test
    fun checkboxNotShownOutsideSelectionMode() {
        setRow(file, isSelectionMode = false)

        composeTestRule.onAllNodes(isToggleable()).assertCountEquals(0)
    }

    @Test
    fun checkboxReflectsSelectedState() {
        setRow(file, isSelectionMode = true, isSelected = true)

        composeTestRule.onNode(isToggleable()).assertIsOn()
    }

    @Test
    fun checkboxReflectsUnselectedState() {
        setRow(file, isSelectionMode = true, isSelected = false)

        composeTestRule.onNode(isToggleable()).assertIsOff()
    }

    @Test
    fun clickingCheckboxInvokesOnClick() {
        var clicked = false
        setRow(file, isSelectionMode = true, isSelected = false, onClick = { clicked = true })

        composeTestRule.onNode(isToggleable()).performClick()

        assertTrue("Checkbox toggle must invoke onClick", clicked)
    }

    @Test
    fun clickingRowInvokesOnClick() {
        var clicked = false
        setRow(file, onClick = { clicked = true })

        composeTestRule.onNodeWithText("notes.txt").performClick()

        assertTrue("Row click must invoke onClick", clicked)
    }

    @Test
    fun longClickingRowInvokesOnLongClick() {
        var longClicked = false
        setRow(file, onLongClick = { longClicked = true })

        composeTestRule.onNodeWithText("notes.txt").performTouchInput { longClick() }

        assertTrue("Row long-click must invoke onLongClick", longClicked)
    }
}
