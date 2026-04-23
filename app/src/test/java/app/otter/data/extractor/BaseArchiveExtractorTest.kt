package app.otter.data.extractor

import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class BaseArchiveExtractorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeArchiveExtractor(
        private val shouldSucceed: Boolean = true,
        private val shouldThrowCancellation: Boolean = false,
        private val shouldThrowException: Boolean = false
    ) : BaseArchiveExtractor() {

        var extractedFilesCount = 0
        var wasExtractCalled = false

        override fun getTag(): String = "FakeExtractor"

        override fun supports(type: ArchiveType): Boolean = type == ArchiveType.ZIP

        override suspend fun extractFromTempFile(
            tempFile: File,
            destinationPath: File,
            onProgress: (ExtractionProgress) -> Unit
        ): ExtractionResult {
            wasExtractCalled = true

            if (shouldThrowCancellation) {
                throw CancellationException("Extraction cancelled")
            }

            if (shouldThrowException) {
                throw RuntimeException("Extraction failed")
            }

            if (shouldSucceed) {
                extractedFilesCount = 5
                onProgress(ExtractionProgress.Extracting("file.txt", extractedFilesCount, 10, 0.5f))
                return ExtractionResult.Success(destinationPath.absolutePath, extractedFilesCount)
            }

            return ExtractionResult.Failure("Extraction failed", RuntimeException("Test failure"))
        }

        override fun getFilePrefix(): String = "fake_archive"

        override fun getFileExtension(): String = ".tmp"

        // Expose protected methods for testing
        fun testValidatePath(outputFile: File, destinationPath: File, entryName: String) {
            validatePath(outputFile, destinationPath, entryName)
        }

        fun testCreateTempFile(inputStream: java.io.InputStream): File {
            return createTempFile(inputStream)
        }
    }

    @Test
    fun `should create temp file from input stream`() = runTest {
        // Given
        val content = "test content"
        val inputStream = ByteArrayInputStream(content.toByteArray())
        val extractor = FakeArchiveExtractor()
        val destination = tempFolder.newFolder("output")

        // When
        val result = extractor.extract(inputStream, destination) {}

        // Then
        assertTrue("Should succeed", result is ExtractionResult.Success)
        assertTrue("Extract should be called", extractor.wasExtractCalled)
    }

    @Test
    fun `should delete temp file after successful extraction`() = runTest {
        // Given
        val content = "test content"
        val inputStream = ByteArrayInputStream(content.toByteArray())
        val extractor = FakeArchiveExtractor()
        val destination = tempFolder.newFolder("output")

        // Count temp files before
        val tempDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        val filesBefore = tempDir.listFiles { file -> file.name.startsWith("fake_archive") }?.size ?: 0

        // When
        extractor.extract(inputStream, destination) {}

        // Then - Verify no new temp files left behind
        val filesAfter = tempDir.listFiles { file -> file.name.startsWith("fake_archive") }?.size ?: 0
        assertEquals("Temp files should be cleaned up", filesBefore, filesAfter)
    }

    @Test
    fun `should delete temp file after failed extraction`() = runTest {
        // Given
        val content = "test content"
        val inputStream = ByteArrayInputStream(content.toByteArray())
        val extractor = FakeArchiveExtractor(shouldThrowException = true)
        val destination = tempFolder.newFolder("output")

        // Count temp files before
        val tempDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        val filesBefore = tempDir.listFiles { file -> file.name.startsWith("fake_archive") }?.size ?: 0

        // When
        val result = extractor.extract(inputStream, destination) {}

        // Then
        assertTrue("Should return failure", result is ExtractionResult.Failure)

        // Verify no new temp files left behind
        val filesAfter = tempDir.listFiles { file -> file.name.startsWith("fake_archive") }?.size ?: 0
        assertEquals("Temp files should be cleaned up", filesBefore, filesAfter)
    }

    @Test
    fun `should propagate cancellation exception`() = runTest {
        // Given
        val content = "test content"
        val inputStream = ByteArrayInputStream(content.toByteArray())
        val extractor = FakeArchiveExtractor(shouldThrowCancellation = true)
        val destination = tempFolder.newFolder("output")

        // When/Then
        try {
            extractor.extract(inputStream, destination) {}
            assertTrue("Should throw CancellationException", false)
        } catch (e: CancellationException) {
            // Expected
            assertTrue("Should catch CancellationException", true)
        }
    }

    @Test
    fun `should handle extraction failure gracefully`() = runTest {
        // Given
        val content = "test content"
        val inputStream = ByteArrayInputStream(content.toByteArray())
        val extractor = FakeArchiveExtractor(shouldThrowException = true)
        val destination = tempFolder.newFolder("output")

        // When
        val result = extractor.extract(inputStream, destination) {}

        // Then
        assertTrue("Should return failure result", result is ExtractionResult.Failure)
        val failure = result as ExtractionResult.Failure
        assertTrue("Error message should mention tag", failure.errorMessage.contains("FakeExtractor"))
    }

    @Test
    fun `should reject empty temp file`() = runTest {
        // Given
        val emptyStream = ByteArrayInputStream(ByteArray(0))
        val extractor = FakeArchiveExtractor()
        val destination = tempFolder.newFolder("output")

        // When
        val result = extractor.extract(emptyStream, destination) {}

        // Then
        assertTrue("Should return failure for empty stream", result is ExtractionResult.Failure)
        val failure = result as ExtractionResult.Failure
        assertTrue("Error should mention empty file", failure.errorMessage.contains("empty"))
    }

    @Test
    fun `should validate path traversal attack`() {
        // Given
        val extractor = FakeArchiveExtractor()
        val destination = tempFolder.newFolder("output")
        val maliciousFile = File(destination.parentFile, "../../../etc/passwd")

        // When/Then
        try {
            extractor.testValidatePath(maliciousFile, destination, "../../../etc/passwd")
            assertTrue("Should throw SecurityException", false)
        } catch (e: SecurityException) {
            assertTrue("Error should mention entry outside destination", e.message?.contains("outside") ?: false)
        }
    }

    @Test
    fun `should accept valid paths`() {
        // Given
        val extractor = FakeArchiveExtractor()
        val destination = tempFolder.newFolder("output")
        val validFile = File(destination, "subfolder/file.txt")

        // When/Then - Should not throw
        extractor.testValidatePath(validFile, destination, "subfolder/file.txt")
    }

    @Test
    fun `should handle large input stream`() = runTest {
        // Given
        val largeContent = ByteArray(1024 * 1024) { it.toByte() } // 1 MB
        val inputStream = ByteArrayInputStream(largeContent)
        val extractor = FakeArchiveExtractor()
        val destination = tempFolder.newFolder("output")

        // When
        val result = extractor.extract(inputStream, destination) {}

        // Then
        assertTrue("Should succeed with large content", result is ExtractionResult.Success)
    }

    @Test
    fun `should call onProgress callback`() = runTest {
        // Given
        val content = "test content"
        val inputStream = ByteArrayInputStream(content.toByteArray())
        val extractor = FakeArchiveExtractor()
        val destination = tempFolder.newFolder("output")
        val progressEvents = mutableListOf<ExtractionProgress>()

        // When
        extractor.extract(inputStream, destination) { progressEvents.add(it) }

        // Then
        assertTrue("Should emit progress events", progressEvents.isNotEmpty())
        val extracting = progressEvents.first() as ExtractionProgress.Extracting
        assertEquals("Should report correct extracted count", 5, extracting.extractedCount)
    }

    @Test
    fun `should create temp file with correct prefix and extension`() = runTest {
        // Given
        val content = "test content"
        val inputStream = ByteArrayInputStream(content.toByteArray())
        val extractor = FakeArchiveExtractor()

        // When
        val tempFile = extractor.testCreateTempFile(inputStream)

        // Then
        assertTrue("Temp file should exist", tempFile.exists())
        assertTrue("Temp file name should start with prefix", tempFile.name.startsWith("fake_archive"))
        assertTrue("Temp file name should end with extension", tempFile.name.endsWith(".tmp"))
        assertEquals("Temp file should have correct content", content, tempFile.readText())

        // Cleanup
        tempFile.delete()
    }

    @Test
    fun `should handle path with multiple parent folders`() {
        // Given
        val extractor = FakeArchiveExtractor()
        val destination = tempFolder.newFolder("output")
        val validFile = File(destination, "folder1/folder2/folder3/file.txt")

        // When/Then - Should not throw
        extractor.testValidatePath(validFile, destination, "folder1/folder2/folder3/file.txt")
    }

    @Test
    fun `should block absolute paths`() {
        // Given
        val extractor = FakeArchiveExtractor()
        val destination = tempFolder.newFolder("output")
        val absolutePath = File("/etc/passwd")

        // When/Then
        try {
            extractor.testValidatePath(absolutePath, destination, "/etc/passwd")
            assertTrue("Should throw SecurityException", false)
        } catch (e: SecurityException) {
            assertTrue("Error should mention outside destination", e.message?.contains("outside") ?: false)
        }
    }
}
