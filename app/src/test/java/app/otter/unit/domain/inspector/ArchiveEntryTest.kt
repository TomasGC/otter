package app.otter.domain.inspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ArchiveEntry] data class.
 *
 * Validates the structure and behavior of archive entry metadata.
 */
class ArchiveEntryTest {

    @Test
    fun `ArchiveEntry should store file metadata correctly`() {
        // Given
        val path = "documents/report.pdf"
        val sizeBytes = 1024L
        val compressedSize = 512L
        val lastModified = 1234567890000L
        val isDirectory = false

        // When
        val entry = ArchiveEntry(
            path = path,
            isDirectory = isDirectory,
            sizeBytes = sizeBytes,
            compressedSize = compressedSize,
            lastModified = lastModified
        )

        // Then
        assertEquals(path, entry.path)
        assertEquals(sizeBytes, entry.sizeBytes)
        assertEquals(compressedSize, entry.compressedSize)
        assertEquals(lastModified, entry.lastModified)
        assertFalse(entry.isDirectory)
    }

    @Test
    fun `ArchiveEntry should store directory metadata correctly`() {
        // Given
        val path = "documents/"
        val sizeBytes = 0L
        val compressedSize = 0L
        val lastModified = 1234567890000L
        val isDirectory = true

        // When
        val entry = ArchiveEntry(
            path = path,
            isDirectory = isDirectory,
            sizeBytes = sizeBytes,
            compressedSize = compressedSize,
            lastModified = lastModified
        )

        // Then
        assertEquals(path, entry.path)
        assertEquals(sizeBytes, entry.sizeBytes)
        assertEquals(compressedSize, entry.compressedSize)
        assertEquals(lastModified, entry.lastModified)
        assertTrue(entry.isDirectory)
    }
}
