package app.otter.ui.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otter.PermissionsHelper
import app.otter.domain.model.ArchiveType
import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.ResourcePath
import app.otter.domain.usecase.BrowseItemsUseCase
import app.otter.domain.usecase.helpers.ArchiveExtractionTestHelper
import app.otter.domain.usecase.helpers.ArchiveNavigationTestHelper
import app.otter.domain.usecase.helpers.ArchiveSelectionTestHelper
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Selection-driven extraction test: select-all then deselect 2 items from a ZIP archive,
 * extract the remainder, and verify that exactly the non-deselected files are present.
 *
 * RAR selective extraction is intentionally omitted from this class.
 * [app.otter.data.extractor.RarExtractorInstrumentedTest.selectiveExtract_subsetOfFiles]
 * already covers the identical scenario (probe a full extract to discover entry names,
 * then selectively extract the first two files, then assert the extracted count).
 * Adding a duplicate test here would provide no additional coverage.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SelectionAndExtractionInstrumentedTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var browseItemsUseCase: BrowseItemsUseCase

    @Before
    fun setup() {
        hiltRule.inject()
        PermissionsHelper.grantStoragePermissions()
    }

    @Test
    fun selectAll_deselect2_extractSelected_onlyNonDeselectedFilesExtracted() = runBlocking {
        val archivePath = ArchiveNavigationTestHelper.getArchivePath(
            ArchiveNavigationTestHelper.TEST_ARCHIVE_ZIP
        )

        // Browse root of archive to discover file entries
        val rootItems = browseItemsUseCase(
            path = ResourcePath.ArchiveEntry(archivePath = archivePath, entryPath = ""),
            offset = 0,
            limit = 500
        ).getOrThrow().items

        val fileItems = rootItems.filterIsInstance<BrowsableItem.ArchiveFileEntry>()
        assertTrue("Archive must have at least 3 files for this test", fileItems.size >= 3)

        // Simulate select-all then deselect the first 2
        val deselected = fileItems.take(2)
        val toExtract = fileItems.drop(2)

        assertTrue("Must have items to extract after deselection", toExtract.isNotEmpty())

        // Extract only the non-deselected items
        val outputDir = ArchiveExtractionTestHelper.createOutputDir()
        val extractedDir = ArchiveExtractionTestHelper.extractArchive(
            archivePath = archivePath,
            archiveType = ArchiveType.ZIP,
            outputDir = outputDir,
            selectedItems = toExtract
        )

        try {
            // Verify exact file count matches the non-deselected set
            val extractedCount = ArchiveExtractionTestHelper.countFilesRecursively(extractedDir)
            assertEquals(
                "Should extract exactly ${toExtract.size} files (select-all minus 2 deselected)",
                toExtract.size,
                extractedCount
            )

            // Verify none of the deselected file names appear in the output
            val extractedNames = ArchiveExtractionTestHelper.getFileNamesRecursively(extractedDir)
            val deselectedPaths = ArchiveSelectionTestHelper.getExpectedFileNames(deselected)

            deselectedPaths.forEach { deselectedPath ->
                val deselectedFileName = deselectedPath.substringAfterLast("/")
                assertFalse(
                    "Deselected file '$deselectedFileName' must NOT be in extracted output",
                    extractedNames.any { it.endsWith(deselectedFileName) }
                )
            }
        } finally {
            extractedDir.deleteRecursively()
        }
    }
}
