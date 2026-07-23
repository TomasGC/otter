package app.otter.data.inspector

import app.otter.domain.inspector.ArchiveEntry
import app.otter.domain.inspector.ArchiveType
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.zip.GeneralPurposeBit
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.attribute.FileTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipInspectorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `should lazily stream ZIP entries`() {
        val zipFile = createTestZip(mapOf(
            "file1.txt" to "content1",
            "file2.txt" to "content2",
            "file3.txt" to "content3"
        ))

        val inspector = ZipInspector(zipFile)
        val entries = mutableListOf<ArchiveEntry>()

        inspector.entries().forEach { entry ->
            entries.add(entry)
        }

        assertEquals(3, entries.size)
        assertEquals("file1.txt", entries[0].path)
        assertEquals("file2.txt", entries[1].path)
        assertEquals("file3.txt", entries[2].path)

        inspector.close()
    }

    @Test
    fun `should provide entry path and size`() {
        val zipFile = createTestZip(mapOf(
            "test.txt" to "12345678"
        ))

        val inspector = ZipInspector(zipFile)
        val entry = inspector.entries().first()

        assertEquals("test.txt", entry.path)
        assertEquals(8L, entry.sizeBytes)

        inspector.close()
    }

    @Test
    fun `should provide lastModified with default time if not explicitly set`() {
        val zipFile = createTestZipWithTime("test.txt", "content", lastModifiedTime = null)

        val inspector = ZipInspector(zipFile)
        val entry = inspector.entries().first()

        // When time not explicitly set, ZIP uses current time (not 0)
        assertTrue(entry.lastModified > 0L)

        inspector.close()
    }

    @Test
    fun `should provide non-zero lastModified when set`() {
        val timestamp = 1704067200000L // 2024-01-01 00:00:00 UTC
        val zipFile = createTestZipWithTime("test.txt", "content", lastModifiedTime = timestamp)

        val inspector = ZipInspector(zipFile)
        val entry = inspector.entries().first()

        assertTrue(entry.lastModified > 0L)

        inspector.close()
    }

    @Test
    fun `should not buffer entries in memory`() {
        // Create large ZIP with 1000 entries (would OOM if buffered)
        val files = (1..1000).associate { "file$it.txt" to "content$it" }
        val zipFile = createTestZip(files)

        val inspector = ZipInspector(zipFile)

        // Take only first 10 entries - if buffering, would load all 1000
        val count = inspector.entries().take(10).count()

        assertEquals(10, count)

        inspector.close()
    }

    @Test
    fun `countEntries should return total file count in O(1)`() = runTest {
        val zipFile = createTestZip(mapOf(
            "file1.txt" to "content1",
            "file2.txt" to "content2",
            "file3.txt" to "content3",
            "dir/" to "",
            "dir/file4.txt" to "content4"
        ))

        val inspector = ZipInspector(zipFile)

        val start = System.nanoTime()
        val count = inspector.countEntries()
        val elapsed = System.nanoTime() - start

        assertEquals(5, count)
        assertTrue("countEntries() should be O(1), took ${elapsed}ns", elapsed < 1_000_000_000) // 1s

        inspector.close()
    }

    @Test
    fun `countEntries should handle empty ZIP`() = runTest {
        val zipFile = createTestZip(emptyMap())

        val inspector = ZipInspector(zipFile)

        assertEquals(0, inspector.countEntries())

        inspector.close()
    }

    @Test
    fun `close should be idempotent`() {
        val zipFile = createTestZip(mapOf("test.txt" to "content"))

        val inspector = ZipInspector(zipFile)
        inspector.close()
        inspector.close() // Second close should not throw

        // No assertion needed - test passes if no exception thrown
    }

    @Test
    fun `entries should throw IllegalStateException after close`() {
        val zipFile = createTestZip(mapOf("test.txt" to "content"))

        val inspector = ZipInspector(zipFile)
        inspector.close()

        val exception = assertThrows(IllegalStateException::class.java) {
            inspector.entries().forEach { }
        }

        assertTrue(exception.message?.contains("closed") == true)
    }

    @Test
    fun `countEntries should throw IllegalStateException after close`() {
        val zipFile = createTestZip(mapOf("test.txt" to "content"))

        val inspector = ZipInspector(zipFile)
        inspector.close()

        val exception = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                inspector.countEntries()
            }
        }

        assertTrue(exception.message?.contains("closed") == true)
    }

    @Test
    fun `entries returns new sequence on each call`() {
        val zipFile = createTestZip(mapOf(
            "file1.txt" to "content1",
            "file2.txt" to "content2"
        ))

        val inspector = ZipInspector(zipFile)

        // First call to entries()
        val firstPass = inspector.entries().toList()
        assertEquals(2, firstPass.size)

        // Second call to entries() returns a new sequence
        val secondPass = inspector.entries().toList()
        assertEquals(2, secondPass.size)

        // Third call still works (each call creates new stream)
        val thirdPass = inspector.entries().toList()
        assertEquals(2, thirdPass.size)

        inspector.close()
    }

    @Test
    fun `should handle ZIP with directory entries`() {
        val zipFile = createTestZip(mapOf(
            "dir/" to "",
            "dir/file.txt" to "content"
        ))

        val inspector = ZipInspector(zipFile)
        val entries = inspector.entries().toList()

        assertEquals(2, entries.size)
        assertEquals("dir/", entries[0].path)
        assertTrue(entries[0].isDirectory)
        assertEquals("dir/file.txt", entries[1].path)
        assertFalse(entries[1].isDirectory)

        inspector.close()
    }

    @Test
    fun `should handle ZIP with nested directories`() {
        val zipFile = createTestZip(mapOf(
            "a/b/c/file.txt" to "content"
        ))

        val inspector = ZipInspector(zipFile)
        val entries = inspector.entries().toList()

        assertEquals(1, entries.size)
        assertEquals("a/b/c/file.txt", entries[0].path)

        inspector.close()
    }

    @Test
    fun `should handle ZIP with unicode filenames`() {
        val zipFile = createTestZip(mapOf(
            "文件.txt" to "Chinese",
            "файл.txt" to "Russian"
        ))

        val inspector = ZipInspector(zipFile)
        val entries = inspector.entries().toList()

        assertEquals(2, entries.size)
        assertTrue(entries.any { it.path == "文件.txt" })
        assertTrue(entries.any { it.path == "файл.txt" })

        inspector.close()
    }

    @Test
    fun `should handle zero-byte files`() {
        val zipFile = createTestZip(mapOf(
            "empty.txt" to ""
        ))

        val inspector = ZipInspector(zipFile)
        val entry = inspector.entries().first()

        assertEquals("empty.txt", entry.path)
        assertEquals(0L, entry.sizeBytes)

        inspector.close()
    }

    @Test
    fun `should handle large file sizes`() {
        val largeContent = "x".repeat(10 * 1024 * 1024) // 10 MB
        val zipFile = createTestZip(mapOf(
            "large.txt" to largeContent
        ))

        val inspector = ZipInspector(zipFile)
        val entry = inspector.entries().first()

        assertEquals("large.txt", entry.path)
        assertEquals(10L * 1024 * 1024, entry.sizeBytes)

        inspector.close()
    }

    @Test
    fun `should handle corrupted ZIP gracefully`() {
        val corruptedFile = tempFolder.newFile("corrupted.zip")
        corruptedFile.writeBytes("not a zip file".toByteArray())

        // Constructor succeeds (file exists)
        val inspector = ZipInspector(corruptedFile)

        // IOException should be thrown when trying to access ZIP contents
        // Note: ZipFile throws on instantiation, ZipInputStream throws on iteration
        try {
            inspector.entries().forEach { }
            // If no exception, verify countEntries() throws
            try {
                runBlocking { inspector.countEntries() }
                throw AssertionError("Expected IOException for corrupted ZIP")
            } catch (e: java.io.IOException) {
                // Expected
            }
        } catch (e: java.io.IOException) {
            // Expected - corrupted file detected
        } finally {
            inspector.close()
        }
    }

    @Test
    fun `getArchiveType should return ZIP`() {
        val zipFile = createTestZip(mapOf("test.txt" to "content"))

        val inspector = ZipInspector(zipFile)

        assertEquals(ArchiveType.ZIP, inspector.getArchiveType())

        inspector.close()
    }

    @Test
    fun `isEncrypted should return false for unencrypted ZIP`() {
        val zipFile = createTestZip(mapOf("test.txt" to "content"))

        val inspector = ZipInspector(zipFile)

        assertFalse(inspector.isEncrypted())

        inspector.close()
    }

    @Test
    fun `isEncrypted returns true for encrypted ZIP`() {
        val zipFile = createEncryptedZip()

        val inspector = ZipInspector(zipFile)

        assertTrue(inspector.isEncrypted())

        inspector.close()
    }

    @Test
    fun `isEncrypted should throw IllegalStateException after close`() {
        val zipFile = createTestZip(mapOf("test.txt" to "content"))

        val inspector = ZipInspector(zipFile)
        inspector.close()

        val exception = assertThrows(IllegalStateException::class.java) {
            inspector.isEncrypted()
        }

        assertTrue(exception.message?.contains("closed") == true)
    }

    @Test
    fun `getArchiveType should not throw after close`() {
        val zipFile = createTestZip(mapOf("test.txt" to "content"))

        val inspector = ZipInspector(zipFile)
        inspector.close()

        // getArchiveType() is stateless and should work even after close
        assertEquals(ArchiveType.ZIP, inspector.getArchiveType())
    }

    // ========== RED Phase: Path Normalization Tests ==========

    @Test
    fun `should normalize path with leading dot`() {
        val zipFile = createTestZip(mapOf(
            "./file.txt" to "content with leading dot"
        ))

        val inspector = ZipInspector(zipFile)
        val entry = inspector.entries().first()

        // Leading './' should be normalized to 'file.txt'
        assertEquals("file.txt", entry.path)

        inspector.close()
    }

    @Test
    fun `should normalize path with leading slash`() {
        val zipFile = createTestZip(mapOf(
            "/file.txt" to "content with leading slash"
        ))

        val inspector = ZipInspector(zipFile)
        val entry = inspector.entries().first()

        // Leading '/' should be normalized to 'file.txt'
        assertEquals("file.txt", entry.path)

        inspector.close()
    }

    @Test
    fun `should normalize directory path with trailing slash`() {
        val zipFile = createTestZip(mapOf(
            "folder/" to ""
        ))

        val inspector = ZipInspector(zipFile)
        val entry = inspector.entries().first()

        // Trailing '/' should be preserved for directories
        assertEquals("folder/", entry.path)
        assertTrue(entry.isDirectory)

        inspector.close()
    }

    @Test
    fun `should handle path with multiple leading slashes`() {
        val zipFile = createTestZip(mapOf(
            "///file.txt" to "content with multiple slashes"
        ))

        val inspector = ZipInspector(zipFile)
        val entry = inspector.entries().first()

        // Multiple leading '/' should be normalized to 'file.txt'
        assertEquals("file.txt", entry.path)

        inspector.close()
    }

    @Test
    fun `should handle Unicode characters in path`() {
        val zipFile = createTestZip(mapOf(
            "folder/文件-📁.txt" to "Unicode content",
            "папка/файл-🎉.txt" to "Cyrillic content",
            "dossier/fichier-été.txt" to "French content"
        ))

        val inspector = ZipInspector(zipFile)
        val entries = inspector.entries().toList()

        assertEquals(3, entries.size)

        // Verify all Unicode paths are preserved correctly
        assertTrue(entries.any { it.path == "folder/文件-📁.txt" })
        assertTrue(entries.any { it.path == "папка/файл-🎉.txt" })
        assertTrue(entries.any { it.path == "dossier/fichier-été.txt" })

        inspector.close()
    }

    @Test
    fun `should normalize path with dot-slash in middle`() {
        val zipFile = createTestZip(mapOf(
            "folder/./file.txt" to "content with dot in middle"
        ))

        val inspector = ZipInspector(zipFile)
        val entry = inspector.entries().first()

        // Path with '/./' should be normalized to 'folder/file.txt'
        assertEquals("folder/file.txt", entry.path)

        inspector.close()
    }

    private fun createTestZip(files: Map<String, String>): File {
        val zipFile = tempFolder.newFile("test.zip")
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return zipFile
    }

    private fun createTestZipWithTime(
        name: String,
        content: String,
        lastModifiedTime: Long?
    ): File {
        val zipFile = tempFolder.newFile("test.zip")
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            val entry = ZipEntry(name)
            // Only set time if not null (can't set to null explicitly)
            if (lastModifiedTime != null) {
                entry.lastModifiedTime = FileTime.fromMillis(lastModifiedTime)
            }
            // If null, time will be set to current time by default
            zip.putNextEntry(entry)
            zip.write(content.toByteArray())
            zip.closeEntry()
        }
        return zipFile
    }

    private fun createEncryptedZip(): File {
        val file = tempFolder.newFile("encrypted.zip")
        // Create a regular ZIP using standard Java API
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("secret.txt"))
            zip.write("content".toByteArray())
            zip.closeEntry()
        }

        // Manually set the encryption flag (bit 0 of general purpose bit flag) in the ZIP file
        val fileBytes = file.readBytes().toMutableList()

        // Find and modify local file header (starts with 0x50, 0x4b, 0x03, 0x04)
        for (i in 0 until fileBytes.size - 3) {
            if (fileBytes[i] == 0x50.toByte() &&
                fileBytes[i + 1] == 0x4b.toByte() &&
                fileBytes[i + 2] == 0x03.toByte() &&
                fileBytes[i + 3] == 0x04.toByte()) {
                // Found local file header, set encryption flag at offset i+6 (bit 0)
                val flagIndex = i + 6
                if (flagIndex < fileBytes.size - 1) {
                    fileBytes[flagIndex] = (fileBytes[flagIndex].toInt() or 0x01).toByte()
                }
                break
            }
        }

        // Find and modify central directory header (starts with 0x50, 0x4b, 0x01, 0x02)
        for (i in 0 until fileBytes.size - 3) {
            if (fileBytes[i] == 0x50.toByte() &&
                fileBytes[i + 1] == 0x4b.toByte() &&
                fileBytes[i + 2] == 0x01.toByte() &&
                fileBytes[i + 3] == 0x02.toByte()) {
                // Found central directory header, set encryption flag at offset i+8 (bit 0)
                // Central directory has offset +2 compared to local file header
                val flagIndex = i + 8
                if (flagIndex < fileBytes.size - 1) {
                    fileBytes[flagIndex] = (fileBytes[flagIndex].toInt() or 0x01).toByte()
                }
                break
            }
        }

        file.writeBytes(fileBytes.toByteArray())
        return file
    }
}
