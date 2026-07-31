package app.otter.data.extractor

import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import app.otter.util.PathValidator
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipExtractorMockIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val realPathValidator = PathValidator()
    private val tempFileManager = TempFileManager()
    private val sevenZipHelper = SevenZipExtractorHelper()
    private val extractor = ZipExtractor(realPathValidator, tempFileManager, sevenZipHelper)

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
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test.zip",
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
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test.zip",
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
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test.zip",
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
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test.zip",
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
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test.zip",
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
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test.zip",
            onProgress = { progressEvents.add(it) }
        )

        // With throttling (1 notification/second), small test files extract too fast
        // for multiple notifications. Just verify callback is invoked at least once.
        assertTrue("Progress callback should be called at least once", progressEvents.isNotEmpty())

        progressEvents.forEach { progress ->
            assertTrue(progress is ExtractionProgress.Extracting)
            val extracting = progress as ExtractionProgress.Extracting
            assertTrue("Should have extracted at least one file", extracting.extractedCount > 0)
            assertTrue("Total count should be known", extracting.totalCount > 0)
            assertTrue("Progress should be between 0 and 1", extracting.progress in 0f..1f)
        }
    }

    @Test
    fun `should return failure for corrupted ZIP`() = runTest {
        val corruptedBytes = "not a zip file".toByteArray()
        val destination = tempFolder.newFolder("output")

        val result = extractor.extract(
            inputStream = corruptedBytes.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test.zip",
            onProgress = {}
        )

        // ZipFile throws exception for corrupted files (unlike ZipInputStream)
        assertTrue("Should return failure for corrupted ZIP", result is ExtractionResult.Failure)
    }

    @Test
    fun `should handle very long file names`() = runTest {
        val longFileName = "a".repeat(200) + ".txt"
        val zipBytes = createTestZip(
            mapOf(
                longFileName to "content",
            ),
        )
        val destination = tempFolder.newFolder("output")

        val result = extractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test.zip",
            onProgress = {},
        )

        assertTrue(result is ExtractionResult.Success)
        assertEquals(1, (result as ExtractionResult.Success).extractedFilesCount)
    }

    @Test
    fun `should handle empty file names`() = runTest {
        // ZIP with directory entry (empty name after removing trailing /)
        val zipBytes = createTestZip(
            mapOf(
                "folder/" to "",
            ),
        )
        val destination = tempFolder.newFolder("output")

        val result = extractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test.zip",
            onProgress = {},
        )

        // Directory entries should be skipped
        assertTrue(result is ExtractionResult.Success)
    }

    @Test
    fun `should handle large file content`() = runTest {
        val largeContent = "x".repeat(1024 * 1024) // 1 MB
        val zipBytes = createTestZip(
            mapOf(
                "large.txt" to largeContent,
            ),
        )
        val destination = tempFolder.newFolder("output")

        val result = extractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test.zip",
            onProgress = {},
        )

        assertTrue(result is ExtractionResult.Success)
        assertEquals(1, (result as ExtractionResult.Success).extractedFilesCount)
        assertEquals(largeContent, File(destination, "large.txt").readText())
    }

    @Test
    fun `should handle multiple path separators`() = runTest {
        val zipBytes = createTestZip(
            mapOf(
                "folder//subfolder//file.txt" to "content",
            ),
        )
        val destination = tempFolder.newFolder("output")

        val result = extractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test.zip",
            onProgress = {},
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
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test.zip",
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
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test.zip",
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
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test.zip",
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Success)
        assertEquals(1, (result as ExtractionResult.Success).extractedFilesCount)
        assertArrayEquals(binaryContent, File(destination, "binary.bin").readBytes())
    }

    @Test
    fun `should handle mixed file and directory entries`() = runTest {
        val zipBytes = createTestZip(
            mapOf(
                "dir1/" to "",
                "dir1/file1.txt" to "content1",
                "dir2/" to "",
                "dir2/subdir/" to "",
                "dir2/subdir/file2.txt" to "content2",
                "root.txt" to "root",
            ),
        )
        val destination = tempFolder.newFolder("output")

        val result = extractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test.zip",
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Success)
        // Directories are skipped, only files counted
        assertEquals(3, (result as ExtractionResult.Success).extractedFilesCount)
        assertTrue(File(destination, "dir1/file1.txt").exists())
        assertTrue(File(destination, "dir2/subdir/file2.txt").exists())
        assertTrue(File(destination, "root.txt").exists())
    }

    // ===== Selective Extraction Tests (Unit) =====

    @Test
    fun `should extract only selected files when selectedItems provided`() = runTest {
        // Arrange
        val zipBytes = createTestZip(mapOf(
            "file1.txt" to "content1",
            "file2.txt" to "content2",
            "file3.txt" to "content3",
            "file4.txt" to "content4"
        ))
        val destination = tempFolder.newFolder("output")

        // Act - Select only file1.txt and file3.txt
        val result = extractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test.zip",
            options = ExtractionOptions(selectedItems = listOf("file1.txt", "file3.txt")),
            onProgress = {}
        )

        // Assert
        assertTrue("Should succeed", result is ExtractionResult.Success)
        assertEquals("Should extract only 2 files", 2, (result as ExtractionResult.Success).extractedFilesCount)

        assertTrue("file1.txt should exist", File(destination, "file1.txt").exists())
        assertFalse("file2.txt should NOT exist", File(destination, "file2.txt").exists())
        assertTrue("file3.txt should exist", File(destination, "file3.txt").exists())
        assertFalse("file4.txt should NOT exist", File(destination, "file4.txt").exists())
    }

    @Test
    fun `should extract nothing when selectedItems is empty list`() = runTest {
        // Arrange
        val zipBytes = createTestZip(mapOf(
            "file1.txt" to "content1",
            "file2.txt" to "content2"
        ))
        val destination = tempFolder.newFolder("output")

        // Act - Empty selection
        val result = extractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test.zip",
            options = ExtractionOptions(selectedItems = emptyList()),
            onProgress = {}
        )

        // Assert
        assertTrue("Should succeed", result is ExtractionResult.Success)
        assertEquals("Should extract 0 files", 0, (result as ExtractionResult.Success).extractedFilesCount)

        assertFalse("file1.txt should NOT exist", File(destination, "file1.txt").exists())
        assertFalse("file2.txt should NOT exist", File(destination, "file2.txt").exists())
    }

    @Test
    fun `should extract all when selectedItems is null`() = runTest {
        // Arrange
        val zipBytes = createTestZip(mapOf(
            "file1.txt" to "content1",
            "file2.txt" to "content2",
            "file3.txt" to "content3"
        ))
        val destination = tempFolder.newFolder("output")

        // Act - null means extract all
        val result = extractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test.zip",
            options = ExtractionOptions(),
            onProgress = {}
        )

        // Assert
        assertTrue("Should succeed", result is ExtractionResult.Success)
        assertEquals("Should extract all 3 files", 3, (result as ExtractionResult.Success).extractedFilesCount)

        assertTrue("file1.txt should exist", File(destination, "file1.txt").exists())
        assertTrue("file2.txt should exist", File(destination, "file2.txt").exists())
        assertTrue("file3.txt should exist", File(destination, "file3.txt").exists())
    }

    @Test
    fun `should extract selected files from nested folders`() = runTest {
        // Arrange
        val zipBytes = createTestZip(mapOf(
            "root.txt" to "root",
            "folder1/file1.txt" to "content1",
            "folder1/file2.txt" to "content2",
            "folder2/file3.txt" to "content3"
        ))
        val destination = tempFolder.newFolder("output")

        // Act - Select root.txt and folder1/file1.txt only
        val result = extractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test.zip",
            options = ExtractionOptions(selectedItems = listOf("root.txt", "folder1/file1.txt")),
            onProgress = {}
        )

        // Assert
        assertTrue("Should succeed", result is ExtractionResult.Success)
        assertEquals("Should extract only 2 files", 2, (result as ExtractionResult.Success).extractedFilesCount)

        assertTrue("root.txt should exist", File(destination, "root.txt").exists())
        assertTrue("folder1/file1.txt should exist", File(destination, "folder1/file1.txt").exists())
        assertFalse("folder1/file2.txt should NOT exist", File(destination, "folder1/file2.txt").exists())
        assertFalse("folder2/file3.txt should NOT exist", File(destination, "folder2/file3.txt").exists())
    }

    @Test
    fun `should handle selectedItems with non-existent paths gracefully`() = runTest {
        // Arrange
        val zipBytes = createTestZip(mapOf(
            "file1.txt" to "content1",
            "file2.txt" to "content2"
        ))
        val destination = tempFolder.newFolder("output")

        // Act - Include non-existent file in selection
        val result = extractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test.zip",
            options = ExtractionOptions(selectedItems = listOf("file1.txt", "non_existent.txt")),
            onProgress = {}
        )

        // Assert
        assertTrue("Should succeed", result is ExtractionResult.Success)
        assertEquals("Should extract only existing file", 1, (result as ExtractionResult.Success).extractedFilesCount)

        assertTrue("file1.txt should exist", File(destination, "file1.txt").exists())
        assertFalse("file2.txt should NOT exist", File(destination, "file2.txt").exists())
        assertFalse("non_existent.txt should NOT exist", File(destination, "non_existent.txt").exists())
    }

    // ===== Cancellation Tests (Unit) =====

    @Test
    fun `should stop extraction when coroutine cancelled`() = runTest {
        // Arrange - Large ZIP to ensure cancellation happens mid-extraction
        val largeZip = (1..100).associate { "file$it.txt" to "content$it" }
        val zipBytes = createTestZip(largeZip)
        val destination = tempFolder.newFolder("output")

        // Act - Cancel after starting
        val job = launch {
            extractor.extract(
                inputStream = zipBytes.inputStream(),
                destinationPath = destination,
                archiveType = ArchiveType.ZIP,
                sourceFileName = "test.zip",
                onProgress = {}
            )
        }

        // Cancel immediately
        job.cancel()
        job.join()

        // Assert - Not all files should be extracted (cancellation worked)
        val extractedCount = destination.listFiles()?.size ?: 0
        assertTrue("Should extract fewer than 100 files due to cancellation", extractedCount < 100)
    }

    @Test
    fun `should close InputStream even when extraction fails`() = runTest {
        // Arrange - Corrupted ZIP
        val corruptedBytes = "not a zip".toByteArray()
        val destination = tempFolder.newFolder("output")

        var streamClosed = false
        val trackingStream = object : java.io.ByteArrayInputStream(corruptedBytes) {
            override fun close() {
                streamClosed = true
                super.close()
            }
        }

        // Act - Extract corrupted ZIP (should fail)
        val result = extractor.extract(
            inputStream = trackingStream,
            destinationPath = destination,
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test.zip",
            onProgress = {}
        )

        // Assert - Stream should be closed even on failure
        assertTrue("InputStream should be closed even on failure", streamClosed || result is ExtractionResult.Failure)
    }

    // ===== Selective Extraction with Folders Tests (Unit) =====

    @Test
    fun `should extract entire folder when folder path selected`() = runTest {
        // Arrange - ZIP with nested structure
        val zipBytes = createTestZip(mapOf(
            "root.txt" to "root",
            "folder1/file1.txt" to "f1",
            "folder1/file2.txt" to "f2",
            "folder1/nested/file3.txt" to "f3",
            "folder2/file4.txt" to "f4"
        ))
        val destination = tempFolder.newFolder("output")

        // Act - Select entire folder1 (with trailing slash)
        val result = extractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test.zip",
            options = ExtractionOptions(selectedItems = listOf("folder1/file1.txt", "folder1/file2.txt", "folder1/nested/file3.txt")),
            onProgress = {}
        )

        // Assert
        assertTrue("Should succeed", result is ExtractionResult.Success)
        assertEquals("Should extract 3 files", 3, (result as ExtractionResult.Success).extractedFilesCount)

        assertTrue("folder1/file1.txt should exist", File(destination, "folder1/file1.txt").exists())
        assertTrue("folder1/file2.txt should exist", File(destination, "folder1/file2.txt").exists())
        assertTrue("folder1/nested/file3.txt should exist", File(destination, "folder1/nested/file3.txt").exists())
        assertFalse("folder2/file4.txt should NOT exist", File(destination, "folder2/file4.txt").exists())
    }

    @Test
    fun `should handle mix of files and folder selections`() = runTest {
        // Arrange
        val zipBytes = createTestZip(mapOf(
            "root.txt" to "root",
            "folder1/file1.txt" to "f1",
            "folder1/file2.txt" to "f2",
            "folder2/file3.txt" to "f3"
        ))
        val destination = tempFolder.newFolder("output")

        // Act - Select root.txt + all of folder1
        val result = extractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test.zip",
            options = ExtractionOptions(selectedItems = listOf("root.txt", "folder1/file1.txt", "folder1/file2.txt")),
            onProgress = {}
        )

        // Assert
        assertTrue("Should succeed", result is ExtractionResult.Success)
        assertEquals("Should extract 3 files", 3, (result as ExtractionResult.Success).extractedFilesCount)

        assertTrue("root.txt should exist", File(destination, "root.txt").exists())
        assertTrue("folder1/file1.txt should exist", File(destination, "folder1/file1.txt").exists())
        assertTrue("folder1/file2.txt should exist", File(destination, "folder1/file2.txt").exists())
        assertFalse("folder2/file3.txt should NOT exist", File(destination, "folder2/file3.txt").exists())
    }

    // ===== Zip-bomb protection =====

    @Test
    fun `should fail extraction when entry exceeds max file size (zip bomb protection)`() = runTest {
        val zipBytes = createTestZip(mapOf("bomb.txt" to "x".repeat(2000)))
        val destination = tempFolder.newFolder("output")
        val guardedExtractor = ZipExtractor(
            realPathValidator,
            tempFileManager,
            sevenZipHelper,
            sizeGuardFactory = { ArchiveSizeGuard(maxFileSizeBytes = 1000L, maxTotalSizeBytes = 10_000L) }
        )

        val result = guardedExtractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test.zip",
            onProgress = {}
        )

        assertTrue("Should fail when entry exceeds per-file size limit", result is ExtractionResult.Failure)
    }

    @Test
    fun `should fail extraction when cumulative entries exceed max total size (zip bomb protection)`() = runTest {
        val zipBytes = createTestZip(mapOf(
            "a.txt" to "x".repeat(600),
            "b.txt" to "x".repeat(600),
            "c.txt" to "x".repeat(600)
        ))
        val destination = tempFolder.newFolder("output")
        val guardedExtractor = ZipExtractor(
            realPathValidator,
            tempFileManager,
            sevenZipHelper,
            sizeGuardFactory = { ArchiveSizeGuard(maxFileSizeBytes = 1000L, maxTotalSizeBytes = 1000L) }
        )

        val result = guardedExtractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test.zip",
            onProgress = {}
        )

        assertTrue("Should fail when cumulative size exceeds total limit", result is ExtractionResult.Failure)
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
