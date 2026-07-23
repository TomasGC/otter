package app.otter.data.extractor

import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking
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
            selectedItems: List<String>?,
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

    @Test
    fun `TempFileManager - empty stream throws IllegalStateException`() {
        val emptyStream = ByteArrayInputStream(ByteArray(0))
        val extractor = FakeArchiveExtractor()

        val ex = assertThrows(IllegalStateException::class.java) {
            extractor.testCreateTempFile(emptyStream, ArchiveType.ZIP)
        }
        assertTrue("Error message should mention empty or doesn't exist",
            ex.message?.contains("empty") == true || ex.message?.contains("exist") == true)
    }

    @Test
    fun `TempFileManager - TAR_GZ type produces temp file ending with tar dot gz`() {
        val content = "some content"
        val inputStream = ByteArrayInputStream(content.toByteArray())
        val extractor = FakeArchiveExtractor()

        val tempFile = extractor.testCreateTempFile(inputStream, ArchiveType.TAR_GZ)
        try {
            assertTrue("Temp file must end with .tar.gz", tempFile.name.endsWith(".tar.gz"))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `TempFileManager - ZIP type produces temp file ending with dot zip`() {
        val content = "content"
        val inputStream = ByteArrayInputStream(content.toByteArray())
        val extractor = FakeArchiveExtractor()

        val tempFile = extractor.testCreateTempFile(inputStream, ArchiveType.ZIP)
        try {
            assertTrue("Temp file must end with .zip", tempFile.name.endsWith(".zip"))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `TempFileManager - TAR_BZ2 type produces temp file ending with tar dot bz2`() {
        val content = "content"
        val inputStream = ByteArrayInputStream(content.toByteArray())
        val extractor = FakeArchiveExtractor()

        val tempFile = extractor.testCreateTempFile(inputStream, ArchiveType.TAR_BZ2)
        try {
            assertTrue("Temp file must end with .tar.bz2", tempFile.name.endsWith(".tar.bz2"))
        } finally {
            tempFile.delete()
        }
    }

    // ===== isEntrySelected boundary tests =====

    private class EntrySelector : BaseArchiveExtractor(
        TempFileManager(), SevenZipExtractorHelper()
    ) {
        override fun getTag() = "Test"
        override fun supports(type: ArchiveType) = false
        override suspend fun extractInternal(
            inputStream: java.io.InputStream,
            destinationPath: File,
            archiveType: ArchiveType,
            sourceFileName: String,
            selectedItems: List<String>?,
            onProgress: (ExtractionProgress) -> Unit
        ): ExtractionResult = ExtractionResult.Success(destinationPath.absolutePath, 0)

        fun testIsEntrySelected(entryName: String, selectedPaths: Set<String>?) =
            isEntrySelected(entryName, selectedPaths)
    }

    @Test
    fun `isEntrySelected - null selectedPaths returns true for any entry`() {
        val sel = EntrySelector()
        assertTrue(sel.testIsEntrySelected("anything.txt", null))
    }

    @Test
    fun `isEntrySelected - exact match returns true`() {
        val sel = EntrySelector()
        assertTrue(sel.testIsEntrySelected("images/photo.jpg", setOf("images/photo.jpg")))
    }

    @Test
    fun `isEntrySelected - non-match returns false`() {
        val sel = EntrySelector()
        assertFalse(sel.testIsEntrySelected("videos/clip.mp4", setOf("images/photo.jpg")))
    }

    @Test
    fun `isEntrySelected - directory prefix match includes entries under dir`() {
        val sel = EntrySelector()
        assertTrue(sel.testIsEntrySelected("images/photo.jpg", setOf("images/")))
        assertTrue(sel.testIsEntrySelected("images/sub/thumb.jpg", setOf("images/")))
    }

    @Test
    fun `isEntrySelected - directory prefix does not match sibling directory with similar name`() {
        val sel = EntrySelector()
        // "images/" must NOT match "images-extra/photo.jpg"
        assertFalse(sel.testIsEntrySelected("images-extra/photo.jpg", setOf("images/")))
    }

    @Test
    fun `isEntrySelected - empty selectedPaths returns false for any entry`() {
        val sel = EntrySelector()
        assertFalse(sel.testIsEntrySelected("file.txt", emptySet()))
    }

    // ===== extractWithTempFile tests =====

    private class ExtractorWithTempAccess(
        private val fakeTempFileManager: ITempFileManager = TempFileManager()
    ) : BaseArchiveExtractor(fakeTempFileManager, SevenZipExtractorHelper()) {
        override fun getTag() = "TempTest"
        override fun supports(type: ArchiveType) = false
        override suspend fun extractInternal(
            inputStream: java.io.InputStream, destinationPath: File, archiveType: ArchiveType,
            sourceFileName: String, selectedItems: List<String>?, onProgress: (ExtractionProgress) -> Unit
        ): ExtractionResult = ExtractionResult.Success(destinationPath.absolutePath, 0)

        suspend fun callExtractWithTempFile(
            stream: java.io.InputStream,
            archiveType: ArchiveType,
            block: suspend (File) -> ExtractionResult
        ): ExtractionResult = extractWithTempFile(stream, archiveType, block)
    }

    private class TrackingTempFileManager : ITempFileManager {
        var createdFile: File? = null
        override fun createTempFile(inputStream: java.io.InputStream, archiveType: ArchiveType, tag: String): File {
            val file = File.createTempFile("tracking_", ".tmp")
            inputStream.copyTo(file.outputStream())
            createdFile = file
            return file
        }
    }

    private class FailingTempFileManager : ITempFileManager {
        override fun createTempFile(inputStream: java.io.InputStream, archiveType: ArchiveType, tag: String): File {
            throw IllegalStateException("Temp file creation failed")
        }
    }

    @Test
    fun `extractWithTempFile calls lambda and deletes temp file on success`() = runTest {
        val trackingManager = TrackingTempFileManager()
        val extractor = ExtractorWithTempAccess(trackingManager)
        val stream = ByteArrayInputStream("content".toByteArray())

        val result = extractor.callExtractWithTempFile(stream, ArchiveType.ZIP) { tempFile ->
            assertTrue("Lambda receives a real file", tempFile.exists())
            ExtractionResult.Success(tempFile.absolutePath, 1)
        }

        assertTrue(result is ExtractionResult.Success)
        val createdFile = trackingManager.createdFile
        assertNotNull("TrackingManager must have created a file", createdFile)
        assertFalse("Temp file must be deleted after success", createdFile!!.exists())
    }

    @Test
    fun `extractWithTempFile deletes temp file even when lambda throws`() = runTest {
        val trackingManager = TrackingTempFileManager()
        val extractor = ExtractorWithTempAccess(trackingManager)
        val stream = ByteArrayInputStream("content".toByteArray())

        try {
            extractor.callExtractWithTempFile(stream, ArchiveType.ZIP) { _ ->
                throw RuntimeException("lambda error")
            }
        } catch (_: RuntimeException) { /* expected */ }

        val createdFile = trackingManager.createdFile
        assertNotNull("TrackingManager must have created a file before throw", createdFile)
        assertFalse("Temp file must be deleted even when lambda throws", createdFile!!.exists())
    }

    @Test
    fun `extractWithTempFile propagates exception from failing TempFileManager`() = runTest {
        val extractor = ExtractorWithTempAccess(FailingTempFileManager())
        val stream = ByteArrayInputStream("content".toByteArray())

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                extractor.callExtractWithTempFile(stream, ArchiveType.ZIP) { file ->
                    ExtractionResult.Success(file.absolutePath, 0)
                }
            }
        }
    }

    // ===== ProgressThrottler tests =====

    private class ThrottlerAccessor : BaseArchiveExtractor(TempFileManager(), SevenZipExtractorHelper()) {
        override fun getTag() = "ThrottlerTest"
        override fun supports(type: ArchiveType) = false
        override suspend fun extractInternal(
            inputStream: java.io.InputStream, destinationPath: File, archiveType: ArchiveType,
            sourceFileName: String, selectedItems: List<String>?, onProgress: (ExtractionProgress) -> Unit
        ): ExtractionResult = ExtractionResult.Success(destinationPath.absolutePath, 0)

        fun makeThrottler(throttleMs: Long) = ProgressThrottler(throttleMs)
    }

    @Test
    fun `ProgressThrottler - shouldNotify returns true on very first call`() {
        val accessor = ThrottlerAccessor()
        val throttler = accessor.makeThrottler(1000L)
        assertTrue("First call must always notify", throttler.shouldNotify())
    }

    @Test
    fun `ProgressThrottler - shouldNotify returns false on immediate second call`() {
        val accessor = ThrottlerAccessor()
        val throttler = accessor.makeThrottler(1000L)
        throttler.shouldNotify() // set lastNotificationTime
        assertFalse("Immediate second call must not notify", throttler.shouldNotify())
    }

    @Test
    fun `ProgressThrottler - shouldNotify returns true after throttleMs elapsed`() {
        val accessor = ThrottlerAccessor()
        val throttler = accessor.makeThrottler(10L) // 10ms throttle
        throttler.shouldNotify()                    // first call sets time
        Thread.sleep(25)                            // wait > throttleMs
        assertTrue("After 25ms with 10ms throttle: must notify", throttler.shouldNotify())
    }

}
