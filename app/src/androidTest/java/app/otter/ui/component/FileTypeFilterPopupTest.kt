package app.otter.ui.component

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
import app.otter.domain.model.FileCategory
import app.otter.domain.model.FileCategoryFilterState
import app.otter.ui.theme.OtterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FileTypeFilterPopupTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setPopup(
        currentFilters: Map<FileCategory, FileCategoryFilterState> = emptyMap(),
        defaultFilters: Map<FileCategory, FileCategoryFilterState> = emptyMap(),
        onDismiss: (Map<FileCategory, FileCategoryFilterState>) -> Unit,
    ) {
        composeTestRule.setContent {
            OtterTheme {
                var expanded by remember { mutableStateOf(true) }
                FileTypeFilterPopup(
                    expanded = expanded,
                    currentFilters = currentFilters,
                    defaultFilters = defaultFilters,
                    onDismiss = { staged ->
                        expanded = false
                        onDismiss(staged)
                    }
                )
            }
        }
    }

    @Test
    fun opensSeededFromCurrentFilters_showsAllRowsAndButtons() {
        setPopup(currentFilters = mapOf(FileCategory.ARCHIVE to FileCategoryFilterState.INCLUDE), onDismiss = {})

        composeTestRule.onNodeWithText("Archive").assertIsDisplayed()
        composeTestRule.onNodeWithText("Image").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reset").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clear").assertIsDisplayed()
    }

    @Test
    fun tappingCategory_doesNotCommitBeforeDismiss() {
        var committed: Map<FileCategory, FileCategoryFilterState>? = null
        setPopup(onDismiss = { committed = it })

        composeTestRule.onNodeWithContentDescription("IMAGE").performClick()
        composeTestRule.waitForIdle()

        assertNull(committed)
    }

    @Test
    fun dismissAfterTappingCategory_commitsStagedInclude() {
        var committed: Map<FileCategory, FileCategoryFilterState>? = null
        setPopup(onDismiss = { committed = it })

        composeTestRule.onNodeWithContentDescription("IMAGE").performClick()
        composeTestRule.waitForIdle()
        Espresso.pressBack()
        composeTestRule.waitForIdle()

        assertEquals(mapOf(FileCategory.IMAGE to FileCategoryFilterState.INCLUDE), committed)
    }

    @Test
    fun dismissAfterTappingCategoryTwice_commitsStagedExclude() {
        var committed: Map<FileCategory, FileCategoryFilterState>? = null
        setPopup(onDismiss = { committed = it })

        composeTestRule.onNodeWithContentDescription("IMAGE").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("IMAGE").performClick()
        composeTestRule.waitForIdle()
        Espresso.pressBack()
        composeTestRule.waitForIdle()

        assertEquals(mapOf(FileCategory.IMAGE to FileCategoryFilterState.EXCLUDE), committed)
    }

    @Test
    fun resetButton_restagesToDefaultFilters() {
        var committed: Map<FileCategory, FileCategoryFilterState>? = null
        setPopup(
            currentFilters = mapOf(FileCategory.ARCHIVE to FileCategoryFilterState.INCLUDE),
            defaultFilters = mapOf(FileCategory.VIDEO to FileCategoryFilterState.EXCLUDE),
            onDismiss = { committed = it }
        )

        composeTestRule.onNodeWithText("Reset").performClick()
        composeTestRule.waitForIdle()
        Espresso.pressBack()
        composeTestRule.waitForIdle()

        assertEquals(mapOf(FileCategory.VIDEO to FileCategoryFilterState.EXCLUDE), committed)
    }

    @Test
    fun clearButton_restagesToEmptyMap() {
        var committed: Map<FileCategory, FileCategoryFilterState>? = null
        setPopup(
            currentFilters = mapOf(FileCategory.ARCHIVE to FileCategoryFilterState.INCLUDE),
            onDismiss = { committed = it }
        )

        composeTestRule.onNodeWithText("Clear").performClick()
        composeTestRule.waitForIdle()
        Espresso.pressBack()
        composeTestRule.waitForIdle()

        assertEquals(emptyMap<FileCategory, FileCategoryFilterState>(), committed)
    }

    @Test
    fun dismissWithoutAnyChange_commitsOriginalCurrentFilters() {
        var committed: Map<FileCategory, FileCategoryFilterState>? = null
        setPopup(
            currentFilters = mapOf(FileCategory.DOCUMENT to FileCategoryFilterState.EXCLUDE),
            onDismiss = { committed = it }
        )

        Espresso.pressBack()
        composeTestRule.waitForIdle()

        assertEquals(mapOf(FileCategory.DOCUMENT to FileCategoryFilterState.EXCLUDE), committed)
    }

    @Test
    fun resetThenTappingCategory_combinesBeforeDismiss() {
        var committed: Map<FileCategory, FileCategoryFilterState>? = null
        setPopup(
            currentFilters = mapOf(FileCategory.ARCHIVE to FileCategoryFilterState.INCLUDE),
            defaultFilters = mapOf(FileCategory.VIDEO to FileCategoryFilterState.EXCLUDE),
            onDismiss = { committed = it }
        )

        composeTestRule.onNodeWithText("Reset").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("IMAGE").performClick()
        composeTestRule.waitForIdle()
        Espresso.pressBack()
        composeTestRule.waitForIdle()

        assertEquals(
            mapOf(
                FileCategory.VIDEO to FileCategoryFilterState.EXCLUDE,
                FileCategory.IMAGE to FileCategoryFilterState.INCLUDE,
            ),
            committed
        )
    }

    @Test
    fun reopeningAfterCommit_reseedsFromUpdatedCurrentFilters() {
        var currentFilters by mutableStateOf(mapOf(FileCategory.ARCHIVE to FileCategoryFilterState.INCLUDE))
        var expanded by mutableStateOf(true)
        var committed: Map<FileCategory, FileCategoryFilterState>? = null

        composeTestRule.setContent {
            OtterTheme {
                FileTypeFilterPopup(
                    expanded = expanded,
                    currentFilters = currentFilters,
                    defaultFilters = emptyMap(),
                    onDismiss = { staged ->
                        expanded = false
                        committed = staged
                        currentFilters = staged
                    }
                )
            }
        }

        // First cycle: dismiss without changes — commits the original ARCHIVE=INCLUDE.
        Espresso.pressBack()
        composeTestRule.waitForIdle()
        assertEquals(mapOf(FileCategory.ARCHIVE to FileCategoryFilterState.INCLUDE), committed)

        // Reopen — must re-seed staged state from the NEW currentFilters, not the original.
        expanded = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("VIDEO").performClick()
        composeTestRule.waitForIdle()
        Espresso.pressBack()
        composeTestRule.waitForIdle()

        assertEquals(
            mapOf(
                FileCategory.ARCHIVE to FileCategoryFilterState.INCLUDE,
                FileCategory.VIDEO to FileCategoryFilterState.INCLUDE,
            ),
            committed
        )
    }
}
