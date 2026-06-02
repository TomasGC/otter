package app.otter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [BrowsableItem] sealed class and its polymorphic types.
 *
 * Validates:
 * - Correct canNavigateInto behavior for each type
 * - Proper property initialization
 * - Type-safe polymorphic dispatch
 */
class BrowsableItemTest {

    @Test
    fun `FileSystemDirectory is navigable`() {
        // Given
        val path = ResourcePath.FileSystem("/storage/emulated/0/Download")
        val item = BrowsableItem.FileSystemDirectory(
            path = path,
            name = "Download",
            sizeBytes = 0L,
            lastModified = 1234567890L
        )

        // Then
        assertTrue(item.canNavigateInto)
        assertEquals(path, item.path)
        assertEquals("Download", item.name)
    }

    @Test
    fun `FileSystemFile is not navigable`() {
        // Given
        val path = ResourcePath.FileSystem("/storage/emulated/0/Download/document.pdf")
        val item = BrowsableItem.FileSystemFile(
            path = path,
            name = "document.pdf",
            sizeBytes = 1024L,
            lastModified = 1234567890L,
            mimeType = "application/pdf"
        )

        // Then
        assertFalse(item.canNavigateInto)
        assertEquals(path, item.path)
        assertEquals("document.pdf", item.name)
        assertEquals("application/pdf", item.mimeType)
    }

    @Test
    fun `ArchiveFile is navigable into archive`() {
        // Given
        val archivePath = ResourcePath.ArchiveEntry(
            archivePath = "/storage/emulated/0/Download/archive.zip",
            entryPath = ""
        )
        val item = BrowsableItem.ArchiveFile(
            path = archivePath,
            name = "archive.zip",
            sizeBytes = 2048L,
            lastModified = 1234567890L,
            archivePath = archivePath,
            mimeType = "application/zip"
        )

        // Then
        assertTrue(item.canNavigateInto)
        assertEquals(archivePath, item.path)
        assertEquals("archive.zip", item.name)
        assertEquals("application/zip", item.mimeType)
    }

    @Test
    fun `ArchiveDirectory is navigable`() {
        // Given
        val archivePath = ResourcePath.ArchiveEntry(
            archivePath = "/storage/emulated/0/Download/archive.zip",
            entryPath = "folder/"
        )
        val item = BrowsableItem.ArchiveDirectory(
            path = archivePath,
            name = "folder",
            sizeBytes = 0L,
            lastModified = 1234567890L,
            archivePath = archivePath
        )

        // Then
        assertTrue(item.canNavigateInto)
        assertEquals(archivePath, item.path)
        assertEquals("folder", item.name)
        assertEquals("folder/", archivePath.entryPath)
    }
}
