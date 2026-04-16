package app.otter.data.extractor

import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipExtractorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val extractor = ZipExtractor()

    @Test
    fun `should support ZIP type`() {
        assertTrue(extractor.supports(ArchiveType.ZIP))
    }

    @Test
    fun `should extract real ZIP file from test resources`() = runTest {
        val testZip = javaClass.classLoader.getResourceAsStream("archives/test.zip")
            ?: throw IllegalStateException("test.zip not found in test resources")
        val destination = tempFolder.newFolder("output")

        val result = extractor.extract(
            inputStream = testZip,
            destinationPath = destination,
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Success)
        val extractedCount = (result as ExtractionResult.Success).extractedFilesCount
        assertTrue("Expected at least 1 file, got $extractedCount", extractedCount >= 1)

        // List all extracted files for debugging
        val extractedFiles = destination.walk().filter { it.isFile }.toList()
        assertTrue("No files extracted", extractedFiles.isNotEmpty())

        // Verify the content of the first file
        val firstFile = extractedFiles.first()
        val content = firstFile.readText().trim()
        assertTrue("Expected 'ZIP', got '$content'", content.contains("ZIP"))
    }

    @Test
    fun `should block path traversal attack`() = runTest {
        val zipBytes = createTestZip(mapOf(
            "../../../etc/passwd" to "hacked"
        ))
        val destination = tempFolder.newFolder("output")

        val result = extractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Failure)
        assertTrue((result as ExtractionResult.Failure).errorMessage.contains("failed"))
    }

    @Test
    fun `should extract ZIP with subdirectories`() = runTest {
        val zipBytes = createTestZip(mapOf(
            "folder1/file1.txt" to "content1",
            "folder1/file2.txt" to "content2",
            "folder2/subfolder/file3.txt" to "content3"
        ))
        val destination = tempFolder.newFolder("output")

        val result = extractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Success)
        assertEquals(3, (result as ExtractionResult.Success).extractedFilesCount)
        assertTrue(File(destination, "folder1/file1.txt").exists())
        assertTrue(File(destination, "folder2/subfolder/file3.txt").exists())
        assertEquals("content3", File(destination, "folder2/subfolder/file3.txt").readText())
    }

    @Test
    fun `should return success for empty ZIP`() = runTest {
        val zipBytes = createTestZip(emptyMap())
        val destination = tempFolder.newFolder("output")

        val result = extractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Success)
        assertEquals(0, (result as ExtractionResult.Success).extractedFilesCount)
    }

    @Test
    fun `should handle files with special characters in names`() = runTest {
        val zipBytes = createTestZip(mapOf(
            "file with spaces.txt" to "content1",
            "file-with-dashes.txt" to "content2",
            "file_with_underscores.txt" to "content3"
        ))
        val destination = tempFolder.newFolder("output")

        val result = extractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Success)
        assertEquals(3, (result as ExtractionResult.Success).extractedFilesCount)
        assertTrue(File(destination, "file with spaces.txt").exists())
        assertTrue(File(destination, "file-with-dashes.txt").exists())
    }

    @Test
    fun `should call onProgress during extraction`() = runTest {
        val zipBytes = createTestZip(mapOf(
            "file1.txt" to "content1",
            "file2.txt" to "content2",
            "file3.txt" to "content3"
        ))
        val destination = tempFolder.newFolder("output")
        val progressEvents = mutableListOf<ExtractionProgress>()

        extractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            onProgress = { progressEvents.add(it) }
        )

        // With throttling (1 notification/second), small test files extract too fast
        // for multiple notifications. Just verify callback is invoked at least once.
        assertTrue("Progress callback should be called at least once", progressEvents.isNotEmpty())

        progressEvents.forEach { progress ->
            assertTrue(progress is ExtractionProgress.Extracting)
            val extracting = progress as ExtractionProgress.Extracting
            assertTrue("Should have extracted at least one file", extracting.extractedCount > 0)
            assertEquals(0, extracting.totalCount) // Indeterminate progress (unknown total)
            assertEquals(0f, extracting.progress) // Indeterminate progress
        }
    }

    @Test
    fun `should return success with zero files for corrupted ZIP`() = runTest {
        val corruptedBytes = "not a zip file".toByteArray()
        val destination = tempFolder.newFolder("output")

        val result = extractor.extract(
            inputStream = corruptedBytes.inputStream(),
            destinationPath = destination,
            onProgress = {}
        )

        // ZipInputStream doesn't throw exception for corrupted files,
        // it just returns null for nextEntry(), resulting in 0 extracted files
        assertTrue(result is ExtractionResult.Success)
        assertEquals(0, (result as ExtractionResult.Success).extractedFilesCount)
    }

    @Test
    fun `should handle very long file names`() = runTest {
        val longFileName = "a".repeat(200) + ".txt"
        val zipBytes = createTestZip(mapOf(
            longFileName to "content"
        ))
        val destination = tempFolder.newFolder("output")

        val result = extractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Success)
        assertEquals(1, (result as ExtractionResult.Success).extractedFilesCount)
    }

    @Test
    fun `should handle empty file names`() = runTest {
        // ZIP with directory entry (empty name after removing trailing /)
        val zipBytes = createTestZip(mapOf(
            "folder/" to ""
        ))
        val destination = tempFolder.newFolder("output")

        val result = extractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            onProgress = {}
        )

        // Directory entries should be skipped
        assertTrue(result is ExtractionResult.Success)
    }

    @Test
    fun `should handle large file content`() = runTest {
        val largeContent = "x".repeat(1024 * 1024) // 1 MB
        val zipBytes = createTestZip(mapOf(
            "large.txt" to largeContent
        ))
        val destination = tempFolder.newFolder("output")

        val result = extractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Success)
        assertEquals(1, (result as ExtractionResult.Success).extractedFilesCount)
        assertEquals(largeContent, File(destination, "large.txt").readText())
    }

    @Test
    fun `should handle multiple path separators`() = runTest {
        val zipBytes = createTestZip(mapOf(
            "folder//subfolder//file.txt" to "content"
        ))
        val destination = tempFolder.newFolder("output")

        val result = extractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Success)
        assertTrue(File(destination, "folder/subfolder/file.txt").exists())
    }

    @Test
    fun `should handle Windows-style paths`() = runTest {
        val zipBytes = createTestZip(mapOf(
            "folder\\subfolder\\file.txt" to "content"
        ))
        val destination = tempFolder.newFolder("output")

        val result = extractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Success)
    }

    @Test
    fun `should handle Unicode filenames`() = runTest {
        val zipBytes = createTestZip(mapOf(
            "文件.txt" to "Chinese",
            "файл.txt" to "Russian",
            "αρχείο.txt" to "Greek"
        ))
        val destination = tempFolder.newFolder("output")

        val result = extractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Success)
        assertEquals(3, (result as ExtractionResult.Success).extractedFilesCount)
    }

    @Test
    fun `should handle binary file content`() = runTest {
        val binaryContent = ByteArray(256) { it.toByte() }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("binary.bin"))
            zip.write(binaryContent)
            zip.closeEntry()
        }

        val destination = tempFolder.newFolder("output")

        val result = extractor.extract(
            inputStream = output.toByteArray().inputStream(),
            destinationPath = destination,
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Success)
        assertEquals(1, (result as ExtractionResult.Success).extractedFilesCount)
        assertArrayEquals(binaryContent, File(destination, "binary.bin").readBytes())
    }

    @Test
    fun `should handle mixed file and directory entries`() = runTest {
        val zipBytes = createTestZip(mapOf(
            "dir1/" to "",
            "dir1/file1.txt" to "content1",
            "dir2/" to "",
            "dir2/subdir/" to "",
            "dir2/subdir/file2.txt" to "content2",
            "root.txt" to "root"
        ))
        val destination = tempFolder.newFolder("output")

        val result = extractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Success)
        // Directories are skipped, only files counted
        assertEquals(3, (result as ExtractionResult.Success).extractedFilesCount)
        assertTrue(File(destination, "dir1/file1.txt").exists())
        assertTrue(File(destination, "dir2/subdir/file2.txt").exists())
        assertTrue(File(destination, "root.txt").exists())
    }

    private fun createTestZip(files: Map<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
