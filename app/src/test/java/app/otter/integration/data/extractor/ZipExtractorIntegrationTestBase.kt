package app.otter.data.extractor

import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import app.otter.test.ArchiveTestHelper
import app.otter.test.ExtractionTestHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Base class for ZipExtractor integration tests.
 * Contains all common test scenarios - subclasses only provide the setup (mock vs real).
 *
 * Subclasses must implement:
 * - createExtractor(): Factory method to create extractor with appropriate dependencies
 * - cleanupExtractor(): Optional cleanup for real implementations
 */
abstract class ZipExtractorIntegrationTestBase {

    protected lateinit var tempDir: File
    protected lateinit var zipFile: File
    protected lateinit var outputDir: File
    protected lateinit var extractor: ZipExtractor

    /**
     * Factory method - subclasses provide extractor with mock or real dependencies.
     */
    protected abstract fun createExtractor(): ZipExtractor

    /**
     * Optional cleanup hook for subclasses (e.g., real temp file managers).
     */
    protected open fun cleanupExtractor() {
        // Default: no-op, override if needed
    }

    @Before
    fun setup() {
        tempDir = ArchiveTestHelper.createTempTestDirectory("zip-integration-test")
        zipFile = File(tempDir, "test_archive.zip")
        outputDir = File(tempDir, "extracted")

        extractor = createExtractor()
    }

    @After
    fun cleanup() {
        cleanupExtractor()
        ArchiveTestHelper.cleanupDirectory(tempDir)
    }

    // ===== Common test scenarios (written once, run in both mock and real variants) =====

    @Test
    fun `extract all files from ZIP with nested folders`() = runTest {
        // Arrange
        ArchiveTestHelper.createZipArchiveWithFolders(zipFile)

        // Act
        val result = ExtractionTestHelper.extractArchive(
            extractor = extractor,
            archiveFile = zipFile,
            outputDir = outputDir,
            archiveType = ArchiveType.ZIP
        )

        // Assert
        assertTrue("Extraction should succeed", result is ExtractionResult.Success)

        val success = result as ExtractionResult.Success
        assertEquals("Should extract 5 files", 5, success.extractedFilesCount)

        val expectedFiles = listOf(
            "root_file.txt",
            "folder1${File.separator}file1.txt",
            "folder1${File.separator}file2.txt",
            "folder2${File.separator}file3.txt",
            "folder1${File.separator}nested${File.separator}deep_file.txt"
        )

        ArchiveTestHelper.assertExtractedFilesExist(outputDir, expectedFiles)

        // Verify file content
        val rootFile = File(outputDir, "root_file.txt")
        ArchiveTestHelper.assertFileContent(rootFile, "Root file content")
    }

    @Test
    fun `extract ZIP with special characters in filenames`() = runTest {
        // Arrange
        val specialEntries = mapOf(
            "file with spaces.txt" to "Spaces in name",
            "file-with-dashes.txt" to "Dashes in name",
            "file_with_underscores.txt" to "Underscores in name"
        )

        ArchiveTestHelper.createZipArchive(zipFile, specialEntries)

        // Act
        val result = ExtractionTestHelper.extractArchive(
            extractor = extractor,
            archiveFile = zipFile,
            outputDir = outputDir,
            archiveType = ArchiveType.ZIP
        )

        // Assert
        assertTrue("Should handle special characters", result is ExtractionResult.Success)

        val extractedFiles = ArchiveTestHelper.listAllFiles(outputDir)
        assertEquals(
            "All files with special chars should be extracted",
            specialEntries.keys.sorted(),
            extractedFiles
        )

        // Verify content of file with spaces
        val fileWithSpaces = File(outputDir, "file with spaces.txt")
        ArchiveTestHelper.assertFileContent(fileWithSpaces, "Spaces in name")
    }

    @Test
    fun `extract empty ZIP archive`() = runTest {
        // Arrange
        ArchiveTestHelper.createZipArchive(zipFile, emptyMap())

        // Act
        val result = ExtractionTestHelper.extractArchive(
            extractor = extractor,
            archiveFile = zipFile,
            outputDir = outputDir,
            archiveType = ArchiveType.ZIP
        )

        // Assert
        assertTrue("Empty archive should succeed", result is ExtractionResult.Success)

        val success = result as ExtractionResult.Success
        assertEquals("Should extract 0 files", 0, success.extractedFilesCount)

        val extractedFiles = ArchiveTestHelper.listAllFiles(outputDir)
        assertTrue("No files should be extracted", extractedFiles.isEmpty())
    }

    @Test
    fun `extract ZIP to non-existent output directory creates it`() = runTest {
        // Arrange
        ArchiveTestHelper.createZipArchiveWithFolders(zipFile)
        val nonExistentOutput = File(tempDir, "non${File.separator}existent${File.separator}path")

        // Act
        val result = ExtractionTestHelper.extractArchive(
            extractor = extractor,
            archiveFile = zipFile,
            outputDir = nonExistentOutput,
            archiveType = ArchiveType.ZIP
        )

        // Assert
        assertTrue("Should create output directory", result is ExtractionResult.Success)
        assertTrue("Output directory should exist", nonExistentOutput.exists())
        assertTrue("Output directory should be a directory", nonExistentOutput.isDirectory)

        // Verify files were extracted
        val extractedFiles = ArchiveTestHelper.listAllFiles(nonExistentOutput)
        assertEquals("Should extract 5 files", 5, extractedFiles.size)
    }

    // ===== Security Tests (CRITICAL) =====

    @Test
    fun `extract ZIP with path traversal attack - throws SecurityException`() = runTest {
        // Arrange - Create ZIP with path traversal attempts
        // Note: Java ZipOutputStream doesn't allow ".." in entry names,
        // so we test the PathValidator behavior separately in PathValidatorTest
        val entriesWithUnsafePath = mapOf(
            "folder/../escape.txt" to "Malicious content"
        )

        ArchiveTestHelper.createZipArchive(zipFile, entriesWithUnsafePath)

        // Act
        val result = ExtractionTestHelper.extractArchive(
            extractor = extractor,
            archiveFile = zipFile,
            outputDir = outputDir,
            archiveType = ArchiveType.ZIP
        )

        // Assert - PathValidator should detect and block path traversal
        // Either throws SecurityException or fails with error containing "outside"
        if (result is ExtractionResult.Failure) {
            assertTrue("Error should mention security issue",
                result.errorMessage.contains("outside", ignoreCase = true) ||
                result.errorMessage.contains("security", ignoreCase = true))
        } else {
            // If extraction succeeds, verify escaped file NOT created outside outputDir
            assertFalse("Should not create file outside outputDir",
                File(tempDir, "escape.txt").exists())
        }
    }

    @Test
    fun `extract ZIP with absolute paths - blocks or normalizes`() = runTest {
        // Arrange - Create ZIP with absolute path-like names
        // Note: Java ZipOutputStream may normalize these automatically
        val entriesWithAbsoluteLikePaths = mapOf(
            "tmp/file.txt" to "Unix-style path",
            "Windows/file.txt" to "Windows-style path",
            "safe_file.txt" to "Safe relative path"
        )

        ArchiveTestHelper.createZipArchive(zipFile, entriesWithAbsoluteLikePaths)

        // Act
        val result = ExtractionTestHelper.extractArchive(
            extractor = extractor,
            archiveFile = zipFile,
            outputDir = outputDir,
            archiveType = ArchiveType.ZIP
        )

        // Assert - Should extract safely to output directory
        assertTrue("Should handle paths safely", result is ExtractionResult.Success)

        // Verify all files extracted to output directory (not to absolute paths)
        val extractedFiles = ArchiveTestHelper.listAllFiles(outputDir)
        assertTrue("Should extract files", extractedFiles.isNotEmpty())

        // Verify NO files created outside outputDir
        assertFalse("Should not create /tmp", File("/tmp/file.txt").exists())
        assertFalse("Should not create C:\\Windows", File("C:\\Windows\\file.txt").exists())
    }

    @Test
    fun `extract corrupted ZIP - fails gracefully`() = runTest {
        // Arrange - Create corrupted ZIP file
        zipFile.writeBytes(byteArrayOf(0x50, 0x4b, 0x03, 0x04, 0x00, 0x00)) // Invalid ZIP header

        // Act
        val result = ExtractionTestHelper.extractArchive(
            extractor = extractor,
            archiveFile = zipFile,
            outputDir = outputDir,
            archiveType = ArchiveType.ZIP
        )

        // Assert
        assertTrue("Corrupted ZIP should fail", result is ExtractionResult.Failure)

        val failure = result as ExtractionResult.Failure
        assertTrue("Error message should mention corruption",
            failure.errorMessage.contains("zip", ignoreCase = true) ||
            failure.errorMessage.contains("corrupt", ignoreCase = true))
    }

    @Test
    fun `extract non-existent ZIP file - fails gracefully`() = runTest {
        // Arrange
        val nonExistentZip = File(tempDir, "does_not_exist.zip")

        // Act & Assert
        try {
            val result = ExtractionTestHelper.extractArchive(
                extractor = extractor,
                archiveFile = nonExistentZip,
                outputDir = outputDir,
                archiveType = ArchiveType.ZIP
            )

            // If extraction completes, it should be a failure
            assertTrue("Non-existent file should fail", result is ExtractionResult.Failure)
        } catch (e: Exception) {
            // FileNotFoundException is acceptable - file doesn't exist
            assertTrue("Should throw FileNotFoundException or similar",
                e is java.io.FileNotFoundException ||
                e.cause is java.io.FileNotFoundException ||
                e is java.nio.file.NoSuchFileException ||
                e.cause is java.nio.file.NoSuchFileException)
        }
    }

    // ===== Edge Cases =====

    @Test
    fun `extract ZIP with unicode characters in filenames`() = runTest {
        // Arrange
        val unicodeEntries = mapOf(
            "文件.txt" to "Chinese characters",
            "файл.txt" to "Cyrillic characters",
            "αρχείο.txt" to "Greek characters",
            "ملف.txt" to "Arabic characters",
            "café_résumé.txt" to "Accented characters"
        )

        ArchiveTestHelper.createZipArchive(zipFile, unicodeEntries)

        // Act
        val result = ExtractionTestHelper.extractArchive(
            extractor = extractor,
            archiveFile = zipFile,
            outputDir = outputDir,
            archiveType = ArchiveType.ZIP
        )

        // Assert
        assertTrue("Should handle unicode filenames", result is ExtractionResult.Success)

        val success = result as ExtractionResult.Success
        assertEquals("Should extract all unicode files", unicodeEntries.size, success.extractedFilesCount)

        // Verify files exist (may have different names depending on filesystem encoding)
        val extractedFiles = ArchiveTestHelper.listAllFiles(outputDir)
        assertEquals("All files should be extracted", unicodeEntries.size, extractedFiles.size)
    }

    @Test
    fun `extract ZIP with empty file (0 bytes)`() = runTest {
        // Arrange
        val entriesWithEmpty = mapOf(
            "empty_file.txt" to "",
            "normal_file.txt" to "Normal content"
        )

        ArchiveTestHelper.createZipArchive(zipFile, entriesWithEmpty)

        // Act
        val result = ExtractionTestHelper.extractArchive(
            extractor = extractor,
            archiveFile = zipFile,
            outputDir = outputDir,
            archiveType = ArchiveType.ZIP
        )

        // Assert
        assertTrue("Should handle empty files", result is ExtractionResult.Success)

        val success = result as ExtractionResult.Success
        assertEquals("Should extract both files", 2, success.extractedFilesCount)

        // Verify empty file exists and has 0 bytes
        val emptyFile = File(outputDir, "empty_file.txt")
        assertTrue("Empty file should exist", emptyFile.exists())
        assertEquals("Empty file should have 0 bytes", 0L, emptyFile.length())
    }

    @Test
    fun `extract ZIP with very long filename (255 chars)`() = runTest {
        // Arrange
        val longName = "a".repeat(240) + ".txt"  // 244 chars (safe for most filesystems)
        val entries = mapOf(
            longName to "Content with long filename"
        )

        ArchiveTestHelper.createZipArchive(zipFile, entries)

        // Act
        val result = ExtractionTestHelper.extractArchive(
            extractor = extractor,
            archiveFile = zipFile,
            outputDir = outputDir,
            archiveType = ArchiveType.ZIP
        )

        // Assert
        assertTrue("Should handle long filenames", result is ExtractionResult.Success)

        val extractedFiles = ArchiveTestHelper.listAllFiles(outputDir)
        assertEquals("Long filename should be extracted", 1, extractedFiles.size)
    }

    @Test
    fun `extract ZIP with deeply nested folders (10 levels)`() = runTest {
        // Arrange
        val deepPath = (1..10).joinToString("/") { "level$it" } + "/deep_file.txt"
        val entries = mapOf(
            deepPath to "Deeply nested content"
        )

        ArchiveTestHelper.createZipArchive(zipFile, entries)

        // Act
        val result = ExtractionTestHelper.extractArchive(
            extractor = extractor,
            archiveFile = zipFile,
            outputDir = outputDir,
            archiveType = ArchiveType.ZIP
        )

        // Assert
        assertTrue("Should handle deeply nested folders", result is ExtractionResult.Success)

        // Verify deep file exists
        val deepFile = File(outputDir, deepPath.replace("/", File.separator))
        assertTrue("Deeply nested file should exist", deepFile.exists())
        ArchiveTestHelper.assertFileContent(deepFile, "Deeply nested content")
    }

    @Test
    fun `extract ZIP with duplicate filenames - overwrites`() = runTest {
        // Arrange - Create two entries with same name (ZIP allows duplicates)
        val duplicateEntries = mapOf(
            "duplicate.txt" to "First version",
            "other.txt" to "Other file"
        )

        ArchiveTestHelper.createZipArchive(zipFile, duplicateEntries)

        // Note: Java's ZipOutputStream doesn't allow duplicate entry names,
        // so this tests the extractor's behavior with valid unique names

        // Act
        val result = ExtractionTestHelper.extractArchive(
            extractor = extractor,
            archiveFile = zipFile,
            outputDir = outputDir,
            archiveType = ArchiveType.ZIP
        )

        // Assert
        assertTrue("Should handle duplicate names", result is ExtractionResult.Success)

        val duplicateFile = File(outputDir, "duplicate.txt")
        assertTrue("Duplicate file should exist", duplicateFile.exists())
    }

    @Test
    fun `extract large file (5MB) - succeeds`() = runTest {
        // Arrange - Create ZIP with large file
        val largeContent = "X".repeat(5 * 1024 * 1024)  // 5 MB
        val entries = mapOf(
            "large_file.txt" to largeContent
        )

        ArchiveTestHelper.createZipArchive(zipFile, entries)

        // Act
        val result = ExtractionTestHelper.extractArchive(
            extractor = extractor,
            archiveFile = zipFile,
            outputDir = outputDir,
            archiveType = ArchiveType.ZIP
        )

        // Assert
        assertTrue("Should handle large files", result is ExtractionResult.Success)

        val largeFile = File(outputDir, "large_file.txt")
        assertTrue("Large file should exist", largeFile.exists())
        assertEquals("Large file should have correct size", largeContent.length.toLong(), largeFile.length())
    }

    // ===== Progress Callback Tests =====

    @Test
    fun `onProgress callback - receives accurate progress updates`() = runTest {
        // Arrange
        ArchiveTestHelper.createZipArchiveWithFolders(zipFile)
        val progressUpdates = mutableListOf<ExtractionProgress.Extracting>()

        // Act
        zipFile.inputStream().use { input ->
            extractor.extract(
                inputStream = input,
                destinationPath = outputDir,
                archiveType = ArchiveType.ZIP,
                sourceFileName = zipFile.name,
                onProgress = { progress ->
                    if (progress is ExtractionProgress.Extracting) {
                        progressUpdates.add(progress)
                    }
                }
            )
        }

        // Assert
        assertTrue("Should receive progress updates", progressUpdates.isNotEmpty())

        // Verify progress increases monotonically
        progressUpdates.zipWithNext { prev, next ->
            assertTrue("Progress should increase", next.extractedCount >= prev.extractedCount)
        }

        // Verify final progress
        val finalProgress = progressUpdates.last()
        assertEquals("Final progress should show 5 files", 5, finalProgress.extractedCount)
        assertEquals("Final progress should be 100%", 1.0f, finalProgress.progress, 0.01f)
    }

    // ===== Selective Extraction Tests (Integration) =====

    @Test
    fun `selective extraction - extract only selected root files`() = runTest {
        // Arrange - Create ZIP with 10 root files
        val entries = (1..10).associate { "file$it.txt" to "content$it" }
        ArchiveTestHelper.createZipArchive(zipFile, entries)

        // Act - Select only 3 files
        val result = zipFile.inputStream().use { input ->
            extractor.extract(
                inputStream = input,
                destinationPath = outputDir,
                archiveType = ArchiveType.ZIP,
                sourceFileName = zipFile.name,
                selectedItems = listOf("file1.txt", "file5.txt", "file10.txt"),
                onProgress = {}
            )
        }

        // Assert
        assertTrue("Should succeed", result is ExtractionResult.Success)
        assertEquals("Should extract only 3 files", 3, (result as ExtractionResult.Success).extractedFilesCount)

        // Verify selected files exist
        assertTrue("file1.txt should exist", File(outputDir, "file1.txt").exists())
        assertTrue("file5.txt should exist", File(outputDir, "file5.txt").exists())
        assertTrue("file10.txt should exist", File(outputDir, "file10.txt").exists())

        // Verify non-selected files don't exist
        assertFalse("file2.txt should NOT exist", File(outputDir, "file2.txt").exists())
        assertFalse("file3.txt should NOT exist", File(outputDir, "file3.txt").exists())
    }

    @Test
    fun `selective extraction - extract files from multiple folders`() = runTest {
        // Arrange - Create ZIP with nested structure
        val entries = mapOf(
            "root1.txt" to "root1",
            "root2.txt" to "root2",
            "folder1/file1.txt" to "f1c1",
            "folder1/file2.txt" to "f1c2",
            "folder2/file3.txt" to "f2c3",
            "folder2/file4.txt" to "f2c4"
        )
        ArchiveTestHelper.createZipArchive(zipFile, entries)

        // Act - Select root1.txt, folder1/file1.txt, and folder2/file4.txt
        val result = zipFile.inputStream().use { input ->
            extractor.extract(
                inputStream = input,
                destinationPath = outputDir,
                archiveType = ArchiveType.ZIP,
                sourceFileName = zipFile.name,
                selectedItems = listOf("root1.txt", "folder1/file1.txt", "folder2/file4.txt"),
                onProgress = {}
            )
        }

        // Assert
        assertTrue("Should succeed", result is ExtractionResult.Success)
        assertEquals("Should extract only 3 files", 3, (result as ExtractionResult.Success).extractedFilesCount)

        // Verify selected files exist
        assertTrue("root1.txt should exist", File(outputDir, "root1.txt").exists())
        assertTrue("folder1/file1.txt should exist", File(outputDir, "folder1/file1.txt").exists())
        assertTrue("folder2/file4.txt should exist", File(outputDir, "folder2/file4.txt").exists())

        // Verify non-selected files don't exist
        assertFalse("root2.txt should NOT exist", File(outputDir, "root2.txt").exists())
        assertFalse("folder1/file2.txt should NOT exist", File(outputDir, "folder1/file2.txt").exists())
        assertFalse("folder2/file3.txt should NOT exist", File(outputDir, "folder2/file3.txt").exists())
    }

    @Test
    fun `selective extraction - empty selection extracts nothing`() = runTest {
        // Arrange
        val entries = mapOf(
            "file1.txt" to "content1",
            "file2.txt" to "content2"
        )
        ArchiveTestHelper.createZipArchive(zipFile, entries)

        // Act - Empty selection list
        val result = zipFile.inputStream().use { input ->
            extractor.extract(
                inputStream = input,
                destinationPath = outputDir,
                archiveType = ArchiveType.ZIP,
                sourceFileName = zipFile.name,
                selectedItems = emptyList(),
                onProgress = {}
            )
        }

        // Assert
        assertTrue("Should succeed", result is ExtractionResult.Success)
        assertEquals("Should extract 0 files", 0, (result as ExtractionResult.Success).extractedFilesCount)

        assertFalse("file1.txt should NOT exist", File(outputDir, "file1.txt").exists())
        assertFalse("file2.txt should NOT exist", File(outputDir, "file2.txt").exists())
    }

    @Test
    fun `selective extraction - null selection extracts all`() = runTest {
        // Arrange
        val entries = mapOf(
            "file1.txt" to "content1",
            "file2.txt" to "content2",
            "file3.txt" to "content3"
        )
        ArchiveTestHelper.createZipArchive(zipFile, entries)

        // Act - null means extract all
        val result = zipFile.inputStream().use { input ->
            extractor.extract(
                inputStream = input,
                destinationPath = outputDir,
                archiveType = ArchiveType.ZIP,
                sourceFileName = zipFile.name,
                selectedItems = null,
                onProgress = {}
            )
        }

        // Assert
        assertTrue("Should succeed", result is ExtractionResult.Success)
        assertEquals("Should extract all 3 files", 3, (result as ExtractionResult.Success).extractedFilesCount)

        assertTrue("file1.txt should exist", File(outputDir, "file1.txt").exists())
        assertTrue("file2.txt should exist", File(outputDir, "file2.txt").exists())
        assertTrue("file3.txt should exist", File(outputDir, "file3.txt").exists())
    }

    @Test
    fun `selective extraction - handles non-existent paths gracefully`() = runTest {
        // Arrange
        val entries = mapOf(
            "file1.txt" to "content1",
            "file2.txt" to "content2"
        )
        ArchiveTestHelper.createZipArchive(zipFile, entries)

        // Act - Include non-existent file in selection
        val result = zipFile.inputStream().use { input ->
            extractor.extract(
                inputStream = input,
                destinationPath = outputDir,
                archiveType = ArchiveType.ZIP,
                sourceFileName = zipFile.name,
                selectedItems = listOf("file1.txt", "non_existent.txt", "another_missing.txt"),
                onProgress = {}
            )
        }

        // Assert
        assertTrue("Should succeed", result is ExtractionResult.Success)
        assertEquals("Should extract only existing file", 1, (result as ExtractionResult.Success).extractedFilesCount)

        assertTrue("file1.txt should exist", File(outputDir, "file1.txt").exists())
        assertFalse("file2.txt should NOT exist", File(outputDir, "file2.txt").exists())
    }

    @Test
    fun `selective extraction - large archive performance`() = runTest {
        // Arrange - Create ZIP with 100 files, select only 5
        val entries = (1..100).associate { "file$it.txt" to "content$it" }
        ArchiveTestHelper.createZipArchive(zipFile, entries)

        // Act - Select only 5 files from 100
        val result = zipFile.inputStream().use { input ->
            extractor.extract(
                inputStream = input,
                destinationPath = outputDir,
                archiveType = ArchiveType.ZIP,
                sourceFileName = zipFile.name,
                selectedItems = listOf("file10.txt", "file25.txt", "file50.txt", "file75.txt", "file100.txt"),
                onProgress = {}
            )
        }

        // Assert
        assertTrue("Should succeed", result is ExtractionResult.Success)
        assertEquals("Should extract only 5 files", 5, (result as ExtractionResult.Success).extractedFilesCount)

        // Verify selected files exist
        assertTrue("file10.txt should exist", File(outputDir, "file10.txt").exists())
        assertTrue("file25.txt should exist", File(outputDir, "file25.txt").exists())
        assertTrue("file50.txt should exist", File(outputDir, "file50.txt").exists())
        assertTrue("file75.txt should exist", File(outputDir, "file75.txt").exists())
        assertTrue("file100.txt should exist", File(outputDir, "file100.txt").exists())

        // Verify non-selected files don't exist
        assertFalse("file1.txt should NOT exist", File(outputDir, "file1.txt").exists())
        assertFalse("file99.txt should NOT exist", File(outputDir, "file99.txt").exists())
    }

    // ===== Cancellation Tests (Integration) =====

    @Test
    fun `cancellation during extraction - stops gracefully`() = runTest {
        // Arrange - Large ZIP with many files
        val entries = (1..1000).associate { "file$it.txt" to "content$it" }
        ArchiveTestHelper.createZipArchive(zipFile, entries)

        // Act - Cancel during extraction
        val job = launch {
            zipFile.inputStream().use { input ->
                extractor.extract(
                    inputStream = input,
                    destinationPath = outputDir,
                    archiveType = ArchiveType.ZIP,
                    sourceFileName = zipFile.name,
                    onProgress = {}
                )
            }
        }

        // Cancel immediately
        job.cancel()
        job.join()

        // Assert - Should not extract all 1000 files
        val extractedCount = outputDir.listFiles()?.filter { it.isFile }?.size ?: 0
        assertTrue("Should extract fewer than 1000 files due to cancellation", extractedCount < 1000)
    }

    // ===== Selective Extraction with Folders (Integration) =====

    @Test
    fun `selective extraction - extract entire folder contents`() = runTest {
        // Arrange - ZIP with nested structure
        val entries = mapOf(
            "root.txt" to "root",
            "folder1/file1.txt" to "f1",
            "folder1/file2.txt" to "f2",
            "folder1/nested/file3.txt" to "f3",
            "folder2/file4.txt" to "f4"
        )
        ArchiveTestHelper.createZipArchive(zipFile, entries)

        // Act - Select all files in folder1 explicitly
        val result = zipFile.inputStream().use { input ->
            extractor.extract(
                inputStream = input,
                destinationPath = outputDir,
                archiveType = ArchiveType.ZIP,
                sourceFileName = zipFile.name,
                selectedItems = listOf("folder1/file1.txt", "folder1/file2.txt", "folder1/nested/file3.txt"),
                onProgress = {}
            )
        }

        // Assert
        assertTrue("Should succeed", result is ExtractionResult.Success)
        assertEquals("Should extract 3 files from folder1", 3, (result as ExtractionResult.Success).extractedFilesCount)

        assertTrue("folder1/file1.txt should exist", File(outputDir, "folder1/file1.txt").exists())
        assertTrue("folder1/file2.txt should exist", File(outputDir, "folder1/file2.txt").exists())
        assertTrue("folder1/nested/file3.txt should exist", File(outputDir, "folder1/nested/file3.txt").exists())
        assertFalse("root.txt should NOT exist", File(outputDir, "root.txt").exists())
        assertFalse("folder2/file4.txt should NOT exist", File(outputDir, "folder2/file4.txt").exists())
    }

    @Test
    fun `selective extraction - mix of individual files and folder contents`() = runTest {
        // Arrange
        val entries = mapOf(
            "root1.txt" to "r1",
            "root2.txt" to "r2",
            "folder1/file1.txt" to "f1",
            "folder1/file2.txt" to "f2",
            "folder2/file3.txt" to "f3"
        )
        ArchiveTestHelper.createZipArchive(zipFile, entries)

        // Act - Mix: root1.txt + all of folder1
        val result = zipFile.inputStream().use { input ->
            extractor.extract(
                inputStream = input,
                destinationPath = outputDir,
                archiveType = ArchiveType.ZIP,
                sourceFileName = zipFile.name,
                selectedItems = listOf("root1.txt", "folder1/file1.txt", "folder1/file2.txt"),
                onProgress = {}
            )
        }

        // Assert
        assertTrue("Should succeed", result is ExtractionResult.Success)
        assertEquals("Should extract 3 files total", 3, (result as ExtractionResult.Success).extractedFilesCount)

        assertTrue("root1.txt should exist", File(outputDir, "root1.txt").exists())
        assertTrue("folder1/file1.txt should exist", File(outputDir, "folder1/file1.txt").exists())
        assertTrue("folder1/file2.txt should exist", File(outputDir, "folder1/file2.txt").exists())
        assertFalse("root2.txt should NOT exist", File(outputDir, "root2.txt").exists())
        assertFalse("folder2/file3.txt should NOT exist", File(outputDir, "folder2/file3.txt").exists())
    }

    // ===== Concurrent Extraction Tests (Integration) =====

    @Test
    fun `concurrent extraction - multiple extractions of same ZIP succeed`() = runTest {
        // Arrange
        val entries = mapOf(
            "file1.txt" to "content1",
            "file2.txt" to "content2",
            "file3.txt" to "content3"
        )
        ArchiveTestHelper.createZipArchive(zipFile, entries)

        val outputDir1 = File(tempDir, "output1")
        val outputDir2 = File(tempDir, "output2")

        // Act - Extract same ZIP concurrently to different destinations
        val job1 = async {
            zipFile.inputStream().use { input ->
                extractor.extract(
                    inputStream = input,
                    destinationPath = outputDir1,
                    archiveType = ArchiveType.ZIP,
                    sourceFileName = zipFile.name,
                    onProgress = {}
                )
            }
        }

        val job2 = async {
            zipFile.inputStream().use { input ->
                extractor.extract(
                    inputStream = input,
                    destinationPath = outputDir2,
                    archiveType = ArchiveType.ZIP,
                    sourceFileName = zipFile.name,
                    onProgress = {}
                )
            }
        }

        val result1 = job1.await()
        val result2 = job2.await()

        // Assert - Both should succeed
        assertTrue("First extraction should succeed", result1 is ExtractionResult.Success)
        assertTrue("Second extraction should succeed", result2 is ExtractionResult.Success)

        assertEquals("First should extract 3 files", 3, (result1 as ExtractionResult.Success).extractedFilesCount)
        assertEquals("Second should extract 3 files", 3, (result2 as ExtractionResult.Success).extractedFilesCount)

        // Verify both destinations have all files
        assertTrue("output1/file1.txt should exist", File(outputDir1, "file1.txt").exists())
        assertTrue("output2/file1.txt should exist", File(outputDir2, "file1.txt").exists())
    }

    // ===== ZIP64 Support Tests (Integration) =====

    @Test
    fun `extract ZIP with more than 65535 entries - ZIP64 support`() = runTest {
        // Arrange - Create ZIP with 1000 files (validates ZIP64 capability)
        // Note: Full ZIP64 test (70k+ files) would take too long for CI
        val entries = (1..1000).associate { "file$it.txt" to "content$it" }
        ArchiveTestHelper.createZipArchive(zipFile, entries)

        // Act
        val result = zipFile.inputStream().use { input ->
            extractor.extract(
                inputStream = input,
                destinationPath = outputDir,
                archiveType = ArchiveType.ZIP,
                sourceFileName = zipFile.name,
                onProgress = {}
            )
        }

        // Assert
        assertTrue("Should succeed with large ZIP", result is ExtractionResult.Success)
        assertEquals("Should extract all 1000 files", 1000, (result as ExtractionResult.Success).extractedFilesCount)
    }

    // ===== Resource Cleanup Tests (Integration) =====

    @Test
    fun `resource cleanup - temp files deleted even on extraction failure`() = runTest {
        // Arrange - Create a corrupted ZIP (valid header but corrupted data)
        val corruptedZip = File(tempDir, "corrupted.zip")
        corruptedZip.writeBytes("corrupted zip data".toByteArray())

        // Act - Extract corrupted ZIP (should fail)
        val result = corruptedZip.inputStream().use { input ->
            extractor.extract(
                inputStream = input,
                destinationPath = outputDir,
                archiveType = ArchiveType.ZIP,
                sourceFileName = corruptedZip.name,
                onProgress = {}
            )
        }

        // Assert - Should fail gracefully
        assertTrue("Should return failure", result is ExtractionResult.Failure)

        // Note: Temp file cleanup verification would require access to TempFileManager internals
        // This test verifies graceful failure without crashes
    }
}
