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
        private val shouldThrowException: Boolean = false,
        tempFileManager: TempFileManager = TempFileManager(),
        sevenZipHelper: SevenZipExtractorHelper = SevenZipExtractorHelper()
    ) : BaseArchiveExtractor(tempFileManager, sevenZipHelper) {

        var extractedFilesCount = 0
        var wasExtractCalled = false

        override fun getTag(): String = "FakeExtractor"

        override fun supports(type: ArchiveType): Boolean = type == ArchiveType.ZIP

        override suspend fun extractInternal(
            inputStream: java.io.InputStream,
            destinationPath: File,
            archiveType: ArchiveType,
            sourceFileName: String,
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

        // Expose protected methods for testing
        fun testCreateTempFile(inputStream: java.io.InputStream, archiveType: ArchiveType): File {
            return tempFileManager.createTempFile(inputStream, archiveType, getTag())
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
        val result = extractor.extract(inputStream, destination, ArchiveType.ZIP, "test.zip") {}

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
        val filesBefore = tempDir.listFiles { file -> file.name.startsWith("otter_archive_") }?.size ?: 0

        // When
        extractor.extract(inputStream, destination, ArchiveType.ZIP, "test.zip") {}

        // Then - Verify no new temp files left behind
        val filesAfter = tempDir.listFiles { file -> file.name.startsWith("otter_archive_") }?.size ?: 0
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
        val filesBefore = tempDir.listFiles { file -> file.name.startsWith("otter_archive_") }?.size ?: 0

        // When
        val result = extractor.extract(inputStream, destination, ArchiveType.ZIP, "test.zip") {}

        // Then
        assertTrue("Should return failure", result is ExtractionResult.Failure)

        // Verify no new temp files left behind
        val filesAfter = tempDir.listFiles { file -> file.name.startsWith("otter_archive_") }?.size ?: 0
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
            extractor.extract(inputStream, destination, ArchiveType.ZIP, "test.zip") {}
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
        val result = extractor.extract(inputStream, destination, ArchiveType.ZIP, "test.zip") {}

        // Then
        assertTrue("Should return failure result", result is ExtractionResult.Failure)
        val failure = result as ExtractionResult.Failure
        assertTrue("Error message should mention tag", failure.errorMessage.contains("FakeExtractor"))
    }


    @Test
    fun `should handle large input stream`() = runTest {
        // Given
        val largeContent = ByteArray(1024 * 1024) { it.toByte() } // 1 MB
        val inputStream = ByteArrayInputStream(largeContent)
        val extractor = FakeArchiveExtractor()
        val destination = tempFolder.newFolder("output")

        // When
        val result = extractor.extract(inputStream, destination, ArchiveType.ZIP, "test.zip") {}

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
        extractor.extract(inputStream, destination, ArchiveType.ZIP, "test.zip") { progressEvents.add(it) }

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
        val tempFile = extractor.testCreateTempFile(inputStream, ArchiveType.ZIP)

        // Then
        assertTrue("Temp file should exist", tempFile.exists())
        assertTrue("Temp file name should start with prefix", tempFile.name.startsWith("otter_archive_"))
        assertTrue("Temp file name should end with .zip", tempFile.name.endsWith(".zip"))
        assertEquals("Temp file should have correct content", content, tempFile.readText())

        // Cleanup
        tempFile.delete()
    }

}
