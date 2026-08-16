package app.otter.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otter.domain.model.FileCategory
import app.otter.domain.model.FileCategoryFilterState
import app.otter.ui.theme.OtterTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FileCategoryFilterRowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun neutralState_displaysCapitalizedCategoryName() {
        composeTestRule.setContent {
            OtterTheme {
                FileCategoryFilterRow(category = FileCategory.IMAGE, state = null, onClick = {})
            }
        }

        composeTestRule.onNodeWithText("Image").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("IMAGE").assertIsDisplayed()
    }

    @Test
    fun includeState_rowStillDisplayed() {
        composeTestRule.setContent {
            OtterTheme {
                FileCategoryFilterRow(category = FileCategory.VIDEO, state = FileCategoryFilterState.INCLUDE, onClick = {})
            }
        }

        composeTestRule.onNodeWithContentDescription("VIDEO").assertIsDisplayed()
        composeTestRule.onNodeWithText("Video").assertIsDisplayed()
    }

    @Test
    fun excludeState_rowStillDisplayed() {
        composeTestRule.setContent {
            OtterTheme {
                FileCategoryFilterRow(category = FileCategory.AUDIO, state = FileCategoryFilterState.EXCLUDE, onClick = {})
            }
        }

        composeTestRule.onNodeWithContentDescription("AUDIO").assertIsDisplayed()
        composeTestRule.onNodeWithText("Audio").assertIsDisplayed()
    }

    @Test
    fun click_invokesOnClickCallback() {
        var clicked = false
        composeTestRule.setContent {
            OtterTheme {
                FileCategoryFilterRow(category = FileCategory.ARCHIVE, state = null, onClick = { clicked = true })
            }
        }

        composeTestRule.onNodeWithContentDescription("ARCHIVE").performClick()

        assertTrue(clicked)
    }

    @Test
    fun multiWordCategoryName_capitalizesOnlyFirstLetter() {
        composeTestRule.setContent {
            OtterTheme {
                FileCategoryFilterRow(category = FileCategory.SPREADSHEET, state = null, onClick = {})
            }
        }

        composeTestRule.onNodeWithText("Spreadsheet").assertIsDisplayed()
    }

    @Test
    fun otherCategory_displaysCorrectly() {
        composeTestRule.setContent {
            OtterTheme {
                FileCategoryFilterRow(category = FileCategory.OTHER, state = FileCategoryFilterState.INCLUDE, onClick = {})
            }
        }

        composeTestRule.onNodeWithText("Other").assertIsDisplayed()
    }
}
