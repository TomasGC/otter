package app.otter.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otter.HiltTestActivity
import app.otter.R
import app.otter.service.ExtractionEventBus
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * State-machine coverage for ExtractionScreen: Starting (no progress yet) -> Extracting
 * (progress + recent files) -> Complete (checkmark, file count, Close button).
 */
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class ExtractionScreenIntegrationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    @Inject
    lateinit var eventBus: ExtractionEventBus

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun startingState_showsAnimatedStartingIndicator_beforeAnyProgress() {
        composeTestRule.setContent {
            ExtractionScreen(fileName = "test.zip", eventBus = eventBus, onComplete = {})
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNode(hasText("Starting", substring = true)).assertIsDisplayed()
    }

    @Test
    fun extractingState_showsTitleProgressLineRecentFilesAndButtons() = runTest {
        val fileName = "test.zip"

        composeTestRule.setContent {
            ExtractionScreen(fileName = fileName, eventBus = eventBus, onComplete = {})
        }
        composeTestRule.waitForIdle() // let LaunchedEffect collectors start before emitting

        eventBus.emitProgress(
            fileName = fileName,
            currentFile = "images/photo.jpg",
            extractedCount = 45,
            totalCount = 100,
            progress = 0.45f,
            recentFiles = listOf("a.txt", "b.txt", "images/photo.jpg")
        )
        composeTestRule.waitForIdle()

        val title = composeTestRule.activity.getString(R.string.extraction_title, fileName)
        composeTestRule.onNodeWithText(title).assertIsDisplayed()

        composeTestRule.onNode(hasText("100 files", substring = true)).assertIsDisplayed()

        // Last recent file gets the "in progress" arrow, earlier ones the checkmark.
        composeTestRule.onNodeWithText("→ images/photo.jpg").assertIsDisplayed()
        composeTestRule.onNodeWithText("✓ a.txt").assertIsDisplayed()
        composeTestRule.onNodeWithText("✓ b.txt").assertIsDisplayed()

        val stopLabel = composeTestRule.activity.getString(R.string.extraction_button_stop)
        composeTestRule.onNodeWithText(stopLabel).assertIsDisplayed()
        val backgroundLabel = composeTestRule.activity.getString(R.string.extraction_button_background)
        composeTestRule.onNodeWithText(backgroundLabel).assertIsDisplayed()
    }

    @Test
    fun completeState_showsCheckmarkFileCountAndCloseButton_afterEmitComplete() = runTest {
        val fileName = "test.zip"
        var completedCalled = false

        composeTestRule.setContent {
            ExtractionScreen(fileName = fileName, eventBus = eventBus, onComplete = { completedCalled = true })
        }
        composeTestRule.waitForIdle()

        eventBus.emitProgress(
            fileName = fileName,
            currentFile = "file1.txt",
            extractedCount = 3,
            totalCount = 3,
            progress = 1.0f,
            recentFiles = listOf("file1.txt")
        )
        eventBus.emitComplete()
        composeTestRule.waitForIdle()

        val completeTitle = composeTestRule.activity.getString(R.string.extraction_complete_title)
        composeTestRule.onNodeWithText(completeTitle).assertIsDisplayed()

        val filesCount = composeTestRule.activity.getString(R.string.extraction_files_count, 3)
        composeTestRule.onNodeWithText(filesCount).assertIsDisplayed()

        val closeLabel = composeTestRule.activity.getString(R.string.extraction_button_close)
        composeTestRule.onNodeWithText(closeLabel).assertIsDisplayed().performClick()

        assertTrue("Close button must invoke onComplete", completedCalled)
    }
}
