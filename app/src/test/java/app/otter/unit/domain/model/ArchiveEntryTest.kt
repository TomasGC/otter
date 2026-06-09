package app.otter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ArchiveEntryTest {

    @Test
    fun `ArchiveEntry should create valid instance`() {
        val entry = ArchiveEntry(
            path = "valid/path.txt",
            isDirectory = false,
            sizeBytes = 100,
            compressedSize = 50,
            lastModified = System.currentTimeMillis()
        )

        assertEquals("valid/path.txt", entry.path)
        assertEquals(false, entry.isDirectory)
        assertEquals(100, entry.sizeBytes)
        assertEquals(50, entry.compressedSize)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ArchiveEntry should reject blank path`() {
        ArchiveEntry(
            path = "",
            isDirectory = false,
            sizeBytes = 100,
            compressedSize = 50,
            lastModified = System.currentTimeMillis()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ArchiveEntry should reject path traversal`() {
        ArchiveEntry(
            path = "../../../etc/passwd",
            isDirectory = false,
            sizeBytes = 100,
            compressedSize = 50,
            lastModified = System.currentTimeMillis()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ArchiveEntry should reject negative size`() {
        ArchiveEntry(
            path = "valid/path.txt",
            isDirectory = false,
            sizeBytes = -1,
            compressedSize = 50,
            lastModified = System.currentTimeMillis()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ArchiveEntry should reject negative compressed size`() {
        ArchiveEntry(
            path = "valid/path.txt",
            isDirectory = false,
            sizeBytes = 100,
            compressedSize = -1,
            lastModified = System.currentTimeMillis()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ArchiveEntry should reject negative last modified`() {
        ArchiveEntry(
            path = "valid/path.txt",
            isDirectory = false,
            sizeBytes = 100,
            compressedSize = 50,
            lastModified = -1
        )
    }
}
