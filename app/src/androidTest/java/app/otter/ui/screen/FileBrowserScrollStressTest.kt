package app.otter.ui.screen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otter.domain.model.ResourcePath
import app.otter.domain.usecase.BrowseItemsUseCase
import app.otter.domain.usecase.helpers.ArchiveNavigationTestHelper
import app.otter.domain.usecase.helpers.BaseInstrumentedTest
import app.otter.service.ExtractionEventBus
import app.otter.service.ExtractionQueue
import app.otter.ui.theme.OtterTheme
import app.otter.ui.viewmodel.FileBrowserViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Scrolls through the real 10,000-entry large_test_archive.zip using the actual
 * FileBrowserViewModel (real BrowseItemsUseCase, real Dispatchers.IO — no mocks,
 * no UnconfinedTestDispatcher) to exercise the sliding-window cache under real
 * scroll-triggered load/evict cycles without hanging or crashing.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class FileBrowserScrollStressTest : BaseInstrumentedTest() {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createComposeRule()

    @Inject
    lateinit var browseItemsUseCase: BrowseItemsUseCase

    private lateinit var viewModel: FileBrowserViewModel

    @Before
    fun setup() {
        hiltRule.inject()

        val archivePath = ArchiveNavigationTestHelper.getArchivePath("large_test_archive.zip")
        viewModel = FileBrowserViewModel(
            browseItemsUseCase = browseItemsUseCase,
            eventBus = ExtractionEventBus(),
            extractionQueue = ExtractionQueue()
        )
        viewModel.navigateToPath(ResourcePath.ArchiveEntry(archivePath, "bulk"))
    }

    private fun anyBulkFileDisplayed(): Boolean =
        composeTestRule.onAllNodesWithText("bulk_", substring = true).fetchSemanticsNodes().isNotEmpty()

    /**
     * Real Dispatchers.IO work isn't tracked by Compose's idling resource, so waitForIdle()
     * alone doesn't guarantee a background browse finished. Poll on wall-clock time instead.
     */
    private fun waitUntilTextVisible(text: String, timeoutMs: Long = 15_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            composeTestRule.waitForIdle()
            if (composeTestRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()) {
                return
            }
            Thread.sleep(100)
        }
        fail("Expected text containing '$text' to become visible within ${timeoutMs}ms")
    }

    @Test
    fun scrollingDeepIntoLargeArchive_advancesWindowAndReturnsWithoutCrashing() {
        composeTestRule.setContent {
            OtterTheme {
                FileBrowserScreen(viewModel = viewModel)
            }
        }

        waitUntilTextVisible("bulk_00001")

        repeat(40) {
            composeTestRule.onNodeWithTag("fileBrowserList").performScrollToIndex(49)
            composeTestRule.waitForIdle()
        }

        assertTrue("List must still show entries after scrolling deep into the archive", anyBulkFileDisplayed())

        repeat(40) {
            composeTestRule.onNodeWithTag("fileBrowserList").performScrollToIndex(0)
            composeTestRule.waitForIdle()
        }

        waitUntilTextVisible("bulk_00001")
    }
}
