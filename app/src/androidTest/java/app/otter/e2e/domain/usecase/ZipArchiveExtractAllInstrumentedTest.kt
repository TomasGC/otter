package app.otter.domain.usecase.zip

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otter.domain.usecase.helpers.ArchiveExtractionTestHelper
import app.otter.domain.usecase.helpers.ArchiveNavigationTestHelper
import app.otter.domain.usecase.helpers.BaseArchiveExtractionTest
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Extract All button tests for ZIP archives (after browsing, press extract button).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ZipArchiveExtractAllInstrumentedTest : BaseArchiveExtractionTest() {

    private companion object {
        private const val TEST_ARCHIVE = ArchiveNavigationTestHelper.TEST_ARCHIVE_ZIP
    }

    @Test
    fun afterBrowsing_pressExtractButton_extractsAllFilesAndFolders() = runBlocking {
        // Arrange
        val archivePath = ArchiveNavigationTestHelper.getArchivePath(TEST_ARCHIVE)

        // Browse archive first
        browseArchive(archivePath)

        // Act - Press Extract button
        val extractedDir = extractArchive(archivePath, selectedItems = emptyList())

        // Assert
        ArchiveExtractionTestHelper.assertTotalExtractedFilesAndFolders(extractedDir)
    }
}
