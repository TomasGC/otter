package app.otter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for ResourcePath sealed class hierarchy.
 *
 * Verifies type-safe path representation for file system and archive entry paths.
 */
class ResourcePathTest {

    @Test
    fun `FileSystem creates path correctly`() {
        // Given
        val path = "/storage/emulated/0/Download"

        // When
        val result = ResourcePath.FileSystem(path)

        // Then
        assertEquals(path, result.path)
        assertTrue(result is ResourcePath.FileSystem)
    }

    @Test
    fun `ArchiveEntry creates archive root correctly`() {
        // Given
        val archivePath = "/storage/emulated/0/archive.zip"

        // When
        val result = ResourcePath.ArchiveEntry(archivePath)

        // Then
        assertEquals(archivePath, result.archivePath)
        assertEquals("", result.entryPath)
        assertTrue(result is ResourcePath.ArchiveEntry)
    }

    @Test
    fun `ArchiveEntry creates subfolder correctly`() {
        // Given
        val archivePath = "/storage/emulated/0/archive.zip"
        val entryPath = "folder/subfolder/"

        // When
        val result = ResourcePath.ArchiveEntry(archivePath, entryPath)

        // Then
        assertEquals(archivePath, result.archivePath)
        assertEquals(entryPath, result.entryPath)
        assertTrue(result is ResourcePath.ArchiveEntry)
    }
}
