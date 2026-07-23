package app.otter.data.extractor

import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import app.otter.util.PathValidator
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IArchiveExtractCallback
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.PropID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SevenZipExtractorHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val helper = SevenZipExtractorHelper()
    private val pathValidator = PathValidator()
    private val logger = ExtractionLogger("SevenZipHelperTest")

    // ===== inArchive.close() always called =====

    @Test
    fun `extract calls inArchive close in finally even when extract throws`() {
        val mockArchive = mockk<IInArchive>(relaxed = true)
        every { mockArchive.numberOfItems } returns 0
        every { mockArchive.extract(any(), any(), any()) } throws RuntimeException("native crash")

        try {
            helper.extract(
                inArchive = mockArchive,
                destinationPath = tempFolder.root,
                pathValidator = pathValidator,
                selectedPaths = null,
                session = SevenZipExtractorHelper.ExtractionSession(onProgress = {}, logger = logger)
            )
        } catch (_: RuntimeException) { /* expected */ }

        verify { mockArchive.close() }
    }

    @Test
    fun `extract calls inArchive close after successful extraction`() {
        val mockArchive = mockk<IInArchive>(relaxed = true)
        every { mockArchive.numberOfItems } returns 0
        // extract() does nothing → callback has 0 files

        helper.extract(
            inArchive = mockArchive,
            destinationPath = tempFolder.root,
            pathValidator = pathValidator,
            selectedPaths = null,
            session = SevenZipExtractorHelper.ExtractionSession(onProgress = {}, logger = logger)
        )

        verify { mockArchive.close() }
    }

    // ===== result reflects extracted count =====

    @Test
    fun `extract returns Success with count 0 when no items are extracted`() {
        val mockArchive = mockk<IInArchive>(relaxed = true)
        every { mockArchive.numberOfItems } returns 0
        // No calls to callback.getStream → extractedCount stays 0

        val result = helper.extract(
            inArchive = mockArchive,
            destinationPath = tempFolder.root,
            pathValidator = pathValidator,
            selectedPaths = null,
            session = SevenZipExtractorHelper.ExtractionSession(onProgress = {}, logger = logger)
        )

        assertTrue(result is ExtractionResult.Success)
        assertEquals(0, (result as ExtractionResult.Success).extractedFilesCount)
    }

    // ===== progress propagation =====

    @Test
    fun `extract propagates progress via onProgress callback when extraction succeeds`() {
        val mockArchive = mockk<IInArchive>(relaxed = true)
        every { mockArchive.numberOfItems } returns 0
        val progressEvents = mutableListOf<ExtractionProgress>()

        // With 0 items, extract() calls the internal callback for 0 files.
        // Progress is only emitted when setOperationResult(OK) is called per file.
        // With 0 items no progress is emitted, but the helper must not crash.
        helper.extract(
            inArchive = mockArchive,
            destinationPath = tempFolder.root,
            pathValidator = pathValidator,
            selectedPaths = null,
            session = SevenZipExtractorHelper.ExtractionSession(
                onProgress = { progressEvents.add(it) }, logger = logger
            )
        )

        // No items → no progress events
        assertTrue(progressEvents.isEmpty())
    }

    // ===== progress propagation — successful extraction =====

    @Test
    fun `extract emits progress event when file entry is successfully written`() {
        val mockArchive = mockk<IInArchive>(relaxed = true)
        every { mockArchive.numberOfItems } returns 1
        every { mockArchive.getProperty(0, PropID.IS_FOLDER) } returns false
        every { mockArchive.getProperty(0, PropID.PATH) } returns "success.txt"
        every { mockArchive.extract(any(), any(), any()) } answers {
            val callback = thirdArg<IArchiveExtractCallback>()
            callback.getStream(0, ExtractAskMode.EXTRACT)
            callback.setOperationResult(ExtractOperationResult.OK)
        }
        val progressEvents = mutableListOf<ExtractionProgress>()

        val result = helper.extract(
            inArchive = mockArchive,
            destinationPath = tempFolder.root,
            pathValidator = pathValidator,
            selectedPaths = null,
            session = SevenZipExtractorHelper.ExtractionSession(
                onProgress = { progressEvents.add(it) }, logger = logger
            )
        )

        assertTrue(result is ExtractionResult.Success)
        assertEquals(1, (result as ExtractionResult.Success).extractedFilesCount)
        assertEquals(1, progressEvents.size)
        val event = progressEvents[0]
        assertTrue(event is ExtractionProgress.Extracting)
        assertEquals("success.txt", (event as ExtractionProgress.Extracting).currentFile)
        assertEquals(1, event.extractedCount)
        assertEquals(1, event.totalCount)
    }

    // ===== corrupted archive: per-entry extraction errors must surface =====

    @Test
    fun `extract throws when an entry reports a non-OK operation result`() {
        val mockArchive = mockk<IInArchive>(relaxed = true)
        every { mockArchive.numberOfItems } returns 1
        every { mockArchive.getProperty(0, PropID.IS_FOLDER) } returns false
        every { mockArchive.getProperty(0, PropID.PATH) } returns "corrupted.txt"
        every { mockArchive.extract(any(), any(), any()) } answers {
            val callback = thirdArg<IArchiveExtractCallback>()
            callback.getStream(0, ExtractAskMode.EXTRACT)
            callback.setOperationResult(ExtractOperationResult.CRCERROR)
        }

        assertThrows(Exception::class.java) {
            helper.extract(
                inArchive = mockArchive,
                destinationPath = tempFolder.root,
                pathValidator = pathValidator,
                selectedPaths = null,
                session = SevenZipExtractorHelper.ExtractionSession(onProgress = {}, logger = logger)
            )
        }
    }
}
