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

/**
 * Unit tests for RarExtractor.
 *
 * Full end-to-end extraction requires native libraries (7-Zip-JBinding .so files),
 * which are not available in unit tests (JVM only) — see RarExtractorInstrumentedTest
 * for that. Below the native archive-opening layer (ArchiveLibraryManager) is mocked,
 * which lets us test RarExtractor's own delegation and error-handling logic on the JVM.
 */
class RarExtractorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val realPathValidator = PathValidator()
    private val archiveLibraryManager = ArchiveLibraryManager()
    private val tempFileManager = TempFileManager()
    private val sevenZipHelper = SevenZipExtractorHelper()
    private val extractor = RarExtractor(realPathValidator, archiveLibraryManager, tempFileManager, sevenZipHelper)

    @Test
    fun `should support RAR type`() {
        assertTrue(extractor.supports(ArchiveType.RAR))
    }

    @Test
    fun `should not support ZIP type`() {
        assertFalse(extractor.supports(ArchiveType.ZIP))
    }

    @Test
    fun `should open archive via ArchiveLibraryManager and delegate to sevenZipHelper`() = runTest {
        val mockManager = mockk<ArchiveLibraryManager>(relaxed = true)
        val mockArchive = mockk<IInArchive>(relaxed = true)
        every { mockManager.openArchive(any()) } returns mockArchive
        every { mockArchive.numberOfItems } returns 0

        val mockedExtractor = RarExtractor(realPathValidator, mockManager, tempFileManager, sevenZipHelper)
        val destination = tempFolder.newFolder("output")

        val result = mockedExtractor.extract(
            "rar content".toByteArray().inputStream(), destination, ArchiveType.RAR, "test.rar"
        ) {}

        assertTrue("Should succeed once the archive is opened", result is ExtractionResult.Success)
        verify { mockManager.openArchive(any()) }
    }

    @Test
    fun `should return Failure when archive fails to open (corrupted archive)`() = runTest {
        val mockManager = mockk<ArchiveLibraryManager>(relaxed = true)
        every { mockManager.openArchive(any()) } throws
            IllegalStateException("Failed to open archive: unsupported format or corrupted")

        val mockedExtractor = RarExtractor(realPathValidator, mockManager, tempFileManager, sevenZipHelper)
        val destination = tempFolder.newFolder("output")

        val result = mockedExtractor.extract(
            "not a real rar".toByteArray().inputStream(), destination, ArchiveType.RAR, "bad.rar"
        ) {}

        assertTrue("Corrupted archive must produce Failure, not crash", result is ExtractionResult.Failure)
    }

    @Test
    fun `extract rar - already-cancelled coroutine throws CancellationException`() {
        // Mirrors RpaExtractorIntegrationTest's cancellation contract test: withContext on a
        // dead Job throws immediately, before ever reaching the native archive-opening layer.
        val destination = tempFolder.newFolder("output-cancel")
        val cancelled = Job().also { it.cancel() }

        assertThrows(CancellationException::class.java) {
            runBlocking(cancelled) {
                extractor.extract(
                    "rar content".toByteArray().inputStream(), destination, ArchiveType.RAR, "test.rar"
                ) {}
            }
        }
    }
}
