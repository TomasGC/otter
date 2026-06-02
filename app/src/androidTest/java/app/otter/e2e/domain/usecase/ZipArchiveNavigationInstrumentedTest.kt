package app.otter.domain.usecase.zip

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otter.domain.model.BrowsableItem
import app.otter.domain.usecase.helpers.ArchiveNavigationTestHelper
import app.otter.domain.usecase.helpers.BaseArchiveNavigationTest
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Navigation tests for ZIP archives (browsing into folders and back).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ZipArchiveNavigationInstrumentedTest : BaseArchiveNavigationTest() {

    private companion object {
        private const val TEST_ARCHIVE = ArchiveNavigationTestHelper.TEST_ARCHIVE_ZIP
    }

    @Test
    fun browseRootFolder_returnsExpectedStructure() = runBlocking {
        // Arrange
        val archivePath = ArchiveNavigationTestHelper.getArchivePath(TEST_ARCHIVE)

        // Act
        val result = browseArchive(archivePath, path = null)

        // Assert
        ArchiveNavigationTestHelper.assertRootHasFoldersAndFiles(result)
    }

    @Test
    fun descendIntoFolder_returnsNestedFiles() = runBlocking {
        // Arrange
        val archivePath = ArchiveNavigationTestHelper.getArchivePath(TEST_ARCHIVE)

        // Act - Browse root and get first folder
        val rootResult = browseArchive(archivePath, path = null)
        val folders = rootResult.filterIsInstance<BrowsableItem.ArchiveDirectory>()
        assertTrue("Root should have at least one folder", folders.isNotEmpty())

        val folderResult = browseArchive(archivePath, path = folders.first().path)

        // Assert
        val filesInFolder = folderResult.filterIsInstance<BrowsableItem.ArchiveFileEntry>()
        assertTrue("Folder should contain files", filesInFolder.isNotEmpty())
    }

    @Test
    fun descendAndAscend_maintainsCorrectPath() = runBlocking {
        // Arrange
        val archivePath = ArchiveNavigationTestHelper.getArchivePath(TEST_ARCHIVE)

        // Act - Browse root, navigate into folder, navigate back
        val rootResult = browseArchive(archivePath, path = null)
        val rootItemCount = rootResult.size

        val folders = rootResult.filterIsInstance<BrowsableItem.ArchiveDirectory>()
        browseArchive(archivePath, path = folders.first().path)

        val backToRootResult = browseArchive(archivePath, path = null)

        // Assert
        assertEquals("Root item count should match after returning", rootItemCount, backToRootResult.size)
    }
}
