package app.otter.ui.component

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otter.HiltTestActivity
import app.otter.service.ExtractionEventBus
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

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
    fun versionLabel_visibleDuringExtraction() = runTest {
        val fileName = "test.zip"

        composeTestRule.setContent {
            ExtractionScreen(
                fileName = fileName,
                eventBus = eventBus,
                onComplete = {}
            )
        }

        eventBus.emitProgress(
            fileName = fileName,
            currentFile = "file1.txt",
            extractedCount = 5,
            totalCount = 10,
            progress = 0.5f,
            recentFiles = listOf("file1.txt")
        )

        composeTestRule.waitForIdle()
    }
}
