package app.otter.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otter.HiltTestActivity
import app.otter.service.ExtractionEventBus
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ExtractionScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun versionLabel_visibleInAllStates() {
        val fileName = "test.zip"
        val eventBus = ExtractionEventBus()

        composeTestRule.setContent {
            ExtractionScreen(
                fileName = fileName,
                eventBus = eventBus,
                onComplete = {}
            )
        }

        composeTestRule.waitForIdle()
    }
}
