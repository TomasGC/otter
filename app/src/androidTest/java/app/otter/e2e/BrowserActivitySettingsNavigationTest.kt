package app.otter.e2e

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otter.BrowserActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for the system back button on the Settings screen. Previously
 * (fixed by BackHandler in BrowserActivity), pressing back while Settings was showing
 * closed the whole app instead of returning to the file browser — caught only by
 * manual verification, with no automated guard. This locks it in.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BrowserActivitySettingsNavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<BrowserActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun systemBackOnSettings_returnsToFileBrowserInsteadOfClosingApp() {
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Back").assertExists()

        Espresso.pressBack()
        composeTestRule.waitForIdle()

        // Back in the file browser (its top bar's settings icon is showing again),
        // not on Settings, and the Activity is still alive (not finished).
        composeTestRule.onNodeWithContentDescription("Settings").assertExists()
        assertEquals(
            androidx.lifecycle.Lifecycle.State.RESUMED,
            composeTestRule.activity.lifecycle.currentState
        )
    }

    @Test
    fun backArrowOnSettings_alsoReturnsToFileBrowser() {
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Settings").assertExists()
    }
}
