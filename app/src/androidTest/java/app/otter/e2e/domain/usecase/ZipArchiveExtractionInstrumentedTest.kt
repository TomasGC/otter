package app.otter.domain.usecase.zip

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otter.domain.model.BrowsableItem
import app.otter.domain.usecase.helpers.ArchiveExtractionTestHelper
import app.otter.domain.usecase.helpers.ArchiveNavigationTestHelper
import app.otter.domain.usecase.helpers.ArchiveSelectionTestHelper
import app.otter.domain.usecase.helpers.BaseArchiveExtractionTest
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Extraction tests for ZIP archives (without browsing, direct extraction).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ZipArchiveExtractionInstrumentedTest : BaseArchiveExtractionTest() {

    private companion object {
        private const val TEST_ARCHIVE = ArchiveNavigationTestHelper.TEST_ARCHIVE_ZIP
    }

    @Test
    fun extractWithoutBrowsing_extractsAllFilesAndFolders() = runBlocking {
        // Arrange
        val archivePath = ArchiveNavigationTestHelper.getArchivePath(TEST_ARCHIVE)

        // Act - Extract all (empty selection = extract everything)
        val extractedDir = extractArchive(archivePath, selectedItems = emptyList())

        // Assert
        ArchiveExtractionTestHelper.assertTotalExtractedFilesAndFolders(extractedDir)
    }

    @Test
    fun selectAll_extractsAllFilesAndFolders() = runBlocking {
        // Arrange
        val archivePath = ArchiveNavigationTestHelper.getArchivePath(TEST_ARCHIVE)

        // Browse and select all items
        val allItems = browseArchive(archivePath)

        // Act - Extract with select all
        val extractedDir = extractArchive(archivePath, selectedItems = allItems)

        // Assert
        ArchiveExtractionTestHelper.assertTotalExtractedFilesAndFolders(extractedDir)
    }

    @Test
    fun selectFewRootFiles_extractsOnlySelectedFiles() = runBlocking {
        // Arrange
        val archivePath = ArchiveNavigationTestHelper.getArchivePath(TEST_ARCHIVE)

        val allItems = browseArchive(archivePath)
        val rootFiles = allItems.filterIsInstance<BrowsableItem.ArchiveFileEntry>()
            .take(10) // Select only 10 root files

        // Act - Extract selected files
        val extractedDir = extractArchive(archivePath, selectedItems = rootFiles)

        // Assert - Verify count AND names
        val expectedFileNames = ArchiveSelectionTestHelper.getExpectedFileNames(rootFiles)
        val expectedFolderNames = emptySet<String>()

        ArchiveExtractionTestHelper.assertExtractedFilesMatch(extractedDir, expectedFileNames, expectedFolderNames)
    }

    @Test
    fun selectRootFilesAndOneFolder_extractsFilesAndFolderContents() = runBlocking {
        // Arrange
        val archivePath = ArchiveNavigationTestHelper.getArchivePath(TEST_ARCHIVE)

        val allItems = browseArchive(archivePath)
        val rootFiles = allItems.filterIsInstance<BrowsableItem.ArchiveFileEntry>().take(10)
        // folder_300 has exactly 300 files — all fit in one browse call (limit=2000)
        val firstFolder = allItems.filterIsInstance<BrowsableItem.ArchiveDirectory>()
            .first { it.name == "folder_300" }

        // Get all files inside the selected folder
        val folderItems = browseArchive(archivePath, firstFolder.path)
        val filesInFolder = folderItems.filterIsInstance<BrowsableItem.ArchiveFileEntry>()

        val selectedItems = rootFiles + listOf(firstFolder)

        // Act - Extract
        val extractedDir = extractArchive(archivePath, selectedItems = selectedItems)

        // Assert - Verify count AND names
        val expectedFileNames = ArchiveSelectionTestHelper.getExpectedFileNames(rootFiles + filesInFolder)
        val expectedFolderNames = ArchiveSelectionTestHelper.getExpectedFolderNames(listOf(firstFolder))

        ArchiveExtractionTestHelper.assertExtractedFilesMatch(extractedDir, expectedFileNames, expectedFolderNames)
    }

    @Test
    fun selectFilesFromMultipleFolders_extractsFoldersAndSelectedFiles() = runBlocking {
        // Arrange
        val archivePath = ArchiveNavigationTestHelper.getArchivePath(TEST_ARCHIVE)

        val rootItems = browseArchive(archivePath)
        val folders = rootItems.filterIsInstance<BrowsableItem.ArchiveDirectory>().take(3)

        // Browse into each folder and select a few files
        val selectedFiles = mutableListOf<BrowsableItem>()
        folders.forEach { folder ->
            val folderItems = browseArchive(archivePath, folder.path)
            val filesInFolder = folderItems.filterIsInstance<BrowsableItem.ArchiveFileEntry>().take(5)
            selectedFiles.addAll(filesInFolder)
        }

        // Act - Extract selected files from 3 folders
        val extractedDir = extractArchive(archivePath, selectedItems = selectedFiles)

        // Assert - Verify count AND names
        val expectedFileNames = ArchiveSelectionTestHelper.getExpectedFileNames(selectedFiles)
        val expectedFolderNames = ArchiveSelectionTestHelper.getExpectedFolderNames(folders)

        ArchiveExtractionTestHelper.assertExtractedFilesMatch(extractedDir, expectedFileNames, expectedFolderNames)
    }
}
