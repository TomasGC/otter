package app.otter.data.extractor

import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionResult
import app.otter.util.PathValidator
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import net.sf.sevenzipjbinding.IInArchive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for SevenZipExtractor.
 *
 * Full end-to-end extraction requires native libraries (7-Zip-JBinding .so files),
 * which are not available in unit tests (JVM only) — see SevenZipExtractorInstrumentedTest
 * for that. Below the native archive-opening layer (ArchiveLibraryManager) is mocked,
 * which lets us test SevenZipExtractor's own delegation and error-handling logic on the JVM.
 */
class SevenZipExtractorMockIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val realPathValidator = PathValidator()
    private val archiveLibraryManager = ArchiveLibraryManager()
    private val tempFileManager = TempFileManager()
    private val sevenZipHelper = SevenZipExtractorHelper()
    private val extractor = SevenZipExtractor(realPathValidator, archiveLibraryManager, tempFileManager, sevenZipHelper)

    @Test
    fun `should support SEVEN_ZIP type`() {
        assertTrue(extractor.supports(ArchiveType.SEVEN_ZIP))
    }

    @Test
    fun `should not support ZIP type`() {
        assertFalse(extractor.supports(ArchiveType.ZIP))
    }

    @Test
    fun `should not support RAR type`() {
        assertFalse(extractor.supports(ArchiveType.RAR))
    }

    @Test
    fun `should open archive via ArchiveLibraryManager and delegate to sevenZipHelper`() = runTest {
        val mockManager = mockk<ArchiveLibraryManager>(relaxed = true)
        val mockArchive = mockk<IInArchive>(relaxed = true)
        every { mockManager.openArchive(any()) } returns mockArchive
        every { mockArchive.numberOfItems } returns 0

        val mockedExtractor = SevenZipExtractor(realPathValidator, mockManager, tempFileManager, sevenZipHelper)
        val destination = tempFolder.newFolder("output")

        val result = mockedExtractor.extract(
            "7z content".toByteArray().inputStream(), destination, ArchiveType.SEVEN_ZIP, "test.7z"
        ) {}

        assertTrue("Should succeed once the archive is opened", result is ExtractionResult.Success)
        verify { mockManager.openArchive(any()) }
    }

    @Test
    fun `should return Failure when archive fails to open (corrupted archive)`() = runTest {
        val mockManager = mockk<ArchiveLibraryManager>(relaxed = true)
        every { mockManager.openArchive(any()) } throws
            IllegalStateException("Failed to open archive: unsupported format or corrupted")

        val mockedExtractor = SevenZipExtractor(realPathValidator, mockManager, tempFileManager, sevenZipHelper)
        val destination = tempFolder.newFolder("output")

        val result = mockedExtractor.extract(
            "not a real 7z".toByteArray().inputStream(), destination, ArchiveType.SEVEN_ZIP, "bad.7z"
        ) {}

        assertTrue("Corrupted archive must produce Failure, not crash", result is ExtractionResult.Failure)
    }

    @Test
    fun `when sourceFile provided, should use openVolumedArchive instead of openArchive`() = runTest {
        val mockManager = mockk<ArchiveLibraryManager>(relaxed = true)
        val mockArchive = mockk<IInArchive>(relaxed = true)
        val mockCallback = mockk<MultiVolumeCallback>(relaxed = true)
        every { mockManager.openVolumedArchive(any()) } returns (mockArchive to mockCallback)
        every { mockArchive.numberOfItems } returns 0

        val mockedExtractor = SevenZipExtractor(realPathValidator, mockManager, tempFileManager, sevenZipHelper)
        val destination = tempFolder.newFolder("output")
        val fakeSourceFile = tempFolder.newFile("archive.7z.001")

        val result = mockedExtractor.extract(
            "7z content".toByteArray().inputStream(), destination, ArchiveType.SEVEN_ZIP, "archive.7z.001",
            options = ExtractionOptions(sourceFile = fakeSourceFile)
        ) {}

        assertTrue("Should succeed with sourceFile path", result is ExtractionResult.Success)
        verify { mockManager.openVolumedArchive(fakeSourceFile) }
        verify(exactly = 0) { mockManager.openArchive(any()) }
        verify { mockCallback.close() }
    }

    @Test
    fun `when sourceFile provided and openVolumedArchive fails, should return Failure`() = runTest {
        val mockManager = mockk<ArchiveLibraryManager>(relaxed = true)
        every { mockManager.openVolumedArchive(any()) } throws
            IllegalStateException("Failed to open multi-volume archive")

        val mockedExtractor = SevenZipExtractor(realPathValidator, mockManager, tempFileManager, sevenZipHelper)
        val destination = tempFolder.newFolder("output")
        val fakeSourceFile = tempFolder.newFile("archive.7z.001")

        val result = mockedExtractor.extract(
            "7z content".toByteArray().inputStream(), destination, ArchiveType.SEVEN_ZIP, "archive.7z.001",
            options = ExtractionOptions(sourceFile = fakeSourceFile)
        ) {}

        assertTrue("Failed multi-volume open must produce Failure", result is ExtractionResult.Failure)
    }

    @Test
    fun `when sourceFile provided, callback is closed even on extraction error`() = runTest {
        val mockManager = mockk<ArchiveLibraryManager>(relaxed = true)
        val mockArchive = mockk<IInArchive>(relaxed = true)
        val mockCallback = mockk<MultiVolumeCallback>(relaxed = true)
        every { mockManager.openVolumedArchive(any()) } returns (mockArchive to mockCallback)
        every { mockArchive.numberOfItems } returns 1
        every { mockArchive.getProperty(0, any()) } throws RuntimeException("Corrupt entry")

        val mockedExtractor = SevenZipExtractor(realPathValidator, mockManager, tempFileManager, sevenZipHelper)
        val destination = tempFolder.newFolder("output")
        val fakeSourceFile = tempFolder.newFile("archive.7z.001")

        mockedExtractor.extract(
            "7z content".toByteArray().inputStream(), destination, ArchiveType.SEVEN_ZIP, "archive.7z.001",
            options = ExtractionOptions(sourceFile = fakeSourceFile)
        ) {}

        verify { mockCallback.close() }
    }

    @Test
    fun `extract 7z - already-cancelled coroutine throws CancellationException`() {
        val destination = tempFolder.newFolder("output-cancel")
        val cancelled = Job().also { it.cancel() }

        assertThrows(CancellationException::class.java) {
            runBlocking(cancelled) {
                extractor.extract(
                    "7z content".toByteArray().inputStream(), destination, ArchiveType.SEVEN_ZIP, "test.7z"
                ) {}
            }
        }
    }
}
