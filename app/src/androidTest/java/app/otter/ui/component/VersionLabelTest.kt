package app.otter.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otter.BuildConfig
import app.otter.HiltTestActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class VersionLabelTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun versionLabel_displaysCorrectVersion() {
        // Given
        val expectedVersion = "v${BuildConfig.VERSION_NAME}"

        // When
        composeTestRule.setContent {
            VersionLabel()
        }

        // Then - Version text is displayed
        composeTestRule
            .onNodeWithText(expectedVersion)
            .assertIsDisplayed()
    }

    @Test
    fun versionLabel_hasVersionPrefix() {
        // Given
        composeTestRule.setContent {
            VersionLabel()
        }

        // Then - Version starts with "v"
        composeTestRule.waitForIdle()

        // Verify "v" prefix exists by checking the full version string
        val expectedVersion = "v${BuildConfig.VERSION_NAME}"
        composeTestRule
            .onNodeWithText(expectedVersion, substring = false)
            .assertIsDisplayed()
    }

    @Test
    fun versionLabel_displaysNonEmptyVersion() {
        // When
        composeTestRule.setContent {
            VersionLabel()
        }

        // Then - Version is not empty
        composeTestRule.waitForIdle()
        assert(BuildConfig.VERSION_NAME.isNotEmpty()) {
            "VERSION_NAME should not be empty"
        }

        composeTestRule
            .onNodeWithText("v${BuildConfig.VERSION_NAME}")
            .assertIsDisplayed()
    }
}
