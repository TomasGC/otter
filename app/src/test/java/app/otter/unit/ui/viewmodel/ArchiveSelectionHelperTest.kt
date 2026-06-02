package app.otter.ui.viewmodel

import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.ResourcePath
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ArchiveSelectionHelper pure functions.
 *
 * Tests business logic independently without ViewModel or Android dependencies.
 */
class ArchiveSelectionHelperTest {

    // Test data - Using actual BrowsableItem types
    private val archivePath1 = ResourcePath.ArchiveEntry(
        archivePath = "/storage/emulated/0/Download/parent.zip",
        entryPath = "archive1.zip"
    )
    private val archive1 = BrowsableItem.ArchiveFile(
        path = archivePath1,
        name = "archive1.zip",
        sizeBytes = 1000L,
        lastModified = 0L,
        archivePath = archivePath1,
        mimeType = "application/zip"
    )

    private val archivePath2 = ResourcePath.ArchiveEntry(
        archivePath = "/storage/emulated/0/Download/parent.zip",
        entryPath = "nested/archive2.zip"
    )
    private val archive2 = BrowsableItem.ArchiveFileEntry(
        path = archivePath2,
        name = "archive2.zip",
        sizeBytes = 2000L,
        lastModified = 0L,
        archivePath = archivePath2,
        mimeType = "application/zip"
    )

    private val regularFile = BrowsableItem.FileSystemFile(
        path = ResourcePath.FileSystem("/path/file.txt"),
        name = "file.txt",
        sizeBytes = 500L,
        lastModified = 0L,
        mimeType = "text/plain"
    )

    private val folder = BrowsableItem.FileSystemDirectory(
        path = ResourcePath.FileSystem("/path/folder"),
        name = "folder",
        sizeBytes = 0L,
        lastModified = 0L
    )

    private val archiveDirectoryPath = ResourcePath.ArchiveEntry(
        archivePath = "/storage/emulated/0/Download/parent.zip",
        entryPath = "nested/folder"
    )
    private val archiveDirectory = BrowsableItem.ArchiveDirectory(
        path = archiveDirectoryPath,
        name = "folder",
        sizeBytes = 0L,
        lastModified = 0L,
        archivePath = archiveDirectoryPath
    )

    @Test
    fun `filterSelectableItems returns all items`() {
        // Arrange
        val items = listOf(archive1, archive2, archiveDirectory, regularFile, folder)

        // Act
        val result = ArchiveSelectionHelper.filterSelectableItems(items)

        // Assert
        assertEquals(5, result.size)
        assertTrue(result.contains(archive1))
        assertTrue(result.contains(archive2))
        assertTrue(result.contains(archiveDirectory))
        assertTrue(result.contains(regularFile))
        assertTrue(result.contains(folder))
    }

    @Test
    fun `filterArchives extracts ArchiveFile, ArchiveFileEntry, and ArchiveDirectory`() {
        // Arrange
        val items = listOf(archive1, archive2, archiveDirectory, regularFile, folder)

        // Act
        val result = ArchiveSelectionHelper.filterArchives(items)

        // Assert
        assertEquals(3, result.size)
        assertTrue(result.contains(archive1))
        assertTrue(result.contains(archive2))
        assertTrue(result.contains(archiveDirectory))
        assertFalse(result.contains(regularFile))
        assertFalse(result.contains(folder))
    }

    @Test
    fun `filterArchives returns empty list when no archives present`() {
        // Arrange
        val items = listOf(regularFile, folder)

        // Act
        val result = ArchiveSelectionHelper.filterArchives(items)

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun `filterArchives returns all items when all are archives`() {
        // Arrange
        val items = listOf(archive1, archive2)

        // Act
        val result = ArchiveSelectionHelper.filterArchives(items)

        // Assert
        assertEquals(2, result.size)
        assertEquals(items, result)
    }

    @Test
    fun `filterArchives with empty list returns empty`() {
        // Arrange
        val items = emptyList<BrowsableItem>()

        // Act
        val result = ArchiveSelectionHelper.filterArchives(items)

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun `addToSelection adds new items to selection`() {
        // Arrange
        val currentSelection = setOf(archive1.path)
        val newItems = listOf(archive2, regularFile)

        // Act
        val result = ArchiveSelectionHelper.addToSelection(newItems, currentSelection)

        // Assert
        assertEquals(3, result.size)
        assertTrue(result.contains(archive1.path))
        assertTrue(result.contains(archive2.path))
        assertTrue(result.contains(regularFile.path))
    }

    @Test
    fun `addToSelection is idempotent - no duplicates`() {
        // Arrange
        val currentSelection = setOf(archive1.path)
        val newItems = listOf(archive1, archive2) // archive1 already selected

        // Act
        val result = ArchiveSelectionHelper.addToSelection(newItems, currentSelection)

        // Assert
        assertEquals(2, result.size)
        assertTrue(result.contains(archive1.path))
        assertTrue(result.contains(archive2.path))
    }

    @Test
    fun `addToSelection with empty current selection`() {
        // Arrange
        val currentSelection = emptySet<ResourcePath>()
        val newItems = listOf(archive1, archive2)

        // Act
        val result = ArchiveSelectionHelper.addToSelection(newItems, currentSelection)

        // Assert
        assertEquals(2, result.size)
        assertTrue(result.contains(archive1.path))
        assertTrue(result.contains(archive2.path))
    }

    @Test
    fun `addToSelection preserves existing selection when adding empty list`() {
        // Arrange
        val currentSelection = setOf(archive1.path, archive2.path)
        val newItems = emptyList<BrowsableItem>()

        // Act
        val result = ArchiveSelectionHelper.addToSelection(newItems, currentSelection)

        // Assert
        assertEquals(2, result.size)
        assertEquals(currentSelection, result)
    }

    @Test
    fun `addToSelection with both empty returns empty`() {
        // Arrange
        val currentSelection = emptySet<ResourcePath>()
        val newItems = emptyList<BrowsableItem>()

        // Act
        val result = ArchiveSelectionHelper.addToSelection(newItems, currentSelection)

        // Assert
        assertTrue(result.isEmpty())
    }
}
