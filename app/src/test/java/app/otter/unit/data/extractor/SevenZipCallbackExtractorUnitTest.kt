package app.otter.data.extractor

import app.otter.util.PathValidator
import io.mockk.every
import io.mockk.mockk
import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.PropID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SevenZipCallbackExtractorUnitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val mockArchive = mockk<IInArchive>(relaxed = true)

    private fun makeCallback(
        selectedPaths: Set<String>? = null,
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): SevenZipCallbackExtractor {
        every { mockArchive.numberOfItems } returns 3
        return SevenZipCallbackExtractor(
            inArchive = mockArchive,
            destinationPath = tempFolder.root,
            pathValidator = PathValidator(),
            selectedPaths = selectedPaths,
            config = SevenZipCallbackExtractor.Config(onProgress = onProgress)
        )
    }

    // ===== getStream() — directory entries =====

    @Test
    fun `getStream returns null for directory entry`() {
        val callback = makeCallback()
        every { mockArchive.getProperty(0, PropID.IS_FOLDER) } returns true
        every { mockArchive.getProperty(0, PropID.PATH) } returns "subdir/"

        val stream = callback.getStream(0, ExtractAskMode.EXTRACT)

        assertNull("Directory entries must produce null stream", stream)
    }

    // ===== getStream() — non-EXTRACT mode =====

    @Test
    fun `getStream returns null for SKIP operation mode`() {
        val callback = makeCallback()
        every { mockArchive.getProperty(0, PropID.IS_FOLDER) } returns false
        every { mockArchive.getProperty(0, PropID.PATH) } returns "file.txt"

        val stream = callback.getStream(0, ExtractAskMode.SKIP)

        assertNull("Non-EXTRACT mode must produce null stream", stream)
    }

    @Test
    fun `getStream returns null for TEST operation mode`() {
        val callback = makeCallback()
        val stream = callback.getStream(0, ExtractAskMode.TEST)
        assertNull(stream)
    }

    // ===== getStream() — path traversal =====

    @Test
    fun `getStream throws SecurityException for path traversal entry`() {
        val callback = makeCallback()
        every { mockArchive.getProperty(0, PropID.IS_FOLDER) } returns false
        every { mockArchive.getProperty(0, PropID.PATH) } returns "../etc/passwd"

        var threw = false
        try {
            callback.getStream(0, ExtractAskMode.EXTRACT)
        } catch (e: SecurityException) {
            threw = true
        }
        assertTrue("Path traversal must throw SecurityException", threw)
    }

    @Test
    fun `getStream throws SecurityException for absolute path entry`() {
        val callback = makeCallback()
        every { mockArchive.getProperty(0, PropID.IS_FOLDER) } returns false
        every { mockArchive.getProperty(0, PropID.PATH) } returns "/etc/passwd"

        var threw = false
        try {
            callback.getStream(0, ExtractAskMode.EXTRACT)
        } catch (e: SecurityException) {
            threw = true
        }
        assertTrue("Absolute path must throw SecurityException", threw)
    }

    // ===== getStream() — selective extraction =====

    @Test
    fun `getStream returns null when entry path not in selectedPaths`() {
        val callback = makeCallback(selectedPaths = setOf("wanted.txt"))
        every { mockArchive.getProperty(0, PropID.IS_FOLDER) } returns false
        every { mockArchive.getProperty(0, PropID.PATH) } returns "not-wanted.txt"

        val stream = callback.getStream(0, ExtractAskMode.EXTRACT)

        assertNull("Non-selected entry must produce null stream", stream)
    }

    @Test
    fun `getStream returns non-null when entry path is in selectedPaths`() {
        val callback = makeCallback(selectedPaths = setOf("wanted.txt"))
        every { mockArchive.getProperty(0, PropID.IS_FOLDER) } returns false
        every { mockArchive.getProperty(0, PropID.PATH) } returns "wanted.txt"

        val stream = callback.getStream(0, ExtractAskMode.EXTRACT)

        assertNotNull("Selected entry must produce non-null stream", stream)
    }

    @Test
    fun `getStream returns non-null when selectedPaths is null (extract all)`() {
        val callback = makeCallback(selectedPaths = null)
        every { mockArchive.getProperty(0, PropID.IS_FOLDER) } returns false
        every { mockArchive.getProperty(0, PropID.PATH) } returns "any.txt"

        val stream = callback.getStream(0, ExtractAskMode.EXTRACT)

        assertNotNull("Null selectedPaths means extract all — stream must not be null", stream)
    }

    // ===== setOperationResult() — count and stream lifecycle =====

    @Test
    fun `setOperationResult OK increments extractedCount for file entry`() {
        val callback = makeCallback()
        // Setup a valid file entry first
        every { mockArchive.getProperty(0, PropID.IS_FOLDER) } returns false
        every { mockArchive.getProperty(0, PropID.PATH) } returns "file.txt"
        callback.getStream(0, ExtractAskMode.EXTRACT) // open stream

        every { mockArchive.getProperty(0, PropID.IS_FOLDER) } returns false
        every { mockArchive.getProperty(0, PropID.PATH) } returns "file.txt"
        callback.setOperationResult(ExtractOperationResult.OK)

        assertEquals(1, callback.getExtractedCount())
    }

    @Test
    fun `setOperationResult non-OK does not increment extractedCount`() {
        val callback = makeCallback()
        every { mockArchive.getProperty(0, PropID.IS_FOLDER) } returns false
        every { mockArchive.getProperty(0, PropID.PATH) } returns "file.txt"
        callback.getStream(0, ExtractAskMode.EXTRACT) // open stream

        callback.setOperationResult(ExtractOperationResult.UNKNOWN_OPERATION_RESULT)

        assertEquals(0, callback.getExtractedCount())
    }

    @Test
    fun `setOperationResult does not increment count for directory entry`() {
        val callback = makeCallback()
        every { mockArchive.getProperty(0, PropID.IS_FOLDER) } returns true
        every { mockArchive.getProperty(0, PropID.PATH) } returns "subdir/"

        callback.setOperationResult(ExtractOperationResult.OK)

        assertEquals(0, callback.getExtractedCount())
    }

    // ===== hasErrors() — corrupted archive detection =====

    @Test
    fun `hasErrors is false when all operations succeed`() {
        val callback = makeCallback()
        every { mockArchive.getProperty(0, PropID.IS_FOLDER) } returns false
        every { mockArchive.getProperty(0, PropID.PATH) } returns "file.txt"
        callback.getStream(0, ExtractAskMode.EXTRACT)
        callback.setOperationResult(ExtractOperationResult.OK)

        assertTrue("No errors reported, hasErrors must be false", !callback.hasErrors())
    }

    @Test
    fun `setOperationResult OK does not increment extractedCount for an entry skipped by selective extraction`() {
        val callback = makeCallback(selectedPaths = setOf("keep.txt"))
        every { mockArchive.getProperty(0, PropID.IS_FOLDER) } returns false
        every { mockArchive.getProperty(0, PropID.PATH) } returns "skip.txt"
        val stream = callback.getStream(0, ExtractAskMode.EXTRACT) // not selected -> null, entry skipped

        callback.setOperationResult(ExtractOperationResult.OK)

        assertNull("Unselected entry must not produce a stream", stream)
        assertEquals(
            "7-Zip reports OK even for entries getStream() skipped; extractedCount must only " +
                "count entries actually written to disk",
            0,
            callback.getExtractedCount()
        )
    }

    @Test
    fun `hasErrors is true after a non-OK operation result`() {
        val callback = makeCallback()
        every { mockArchive.getProperty(0, PropID.IS_FOLDER) } returns false
        every { mockArchive.getProperty(0, PropID.PATH) } returns "corrupted.txt"
        callback.getStream(0, ExtractAskMode.EXTRACT)

        callback.setOperationResult(ExtractOperationResult.CRCERROR)

        assertTrue("CRC error must be recorded as a failed operation", callback.hasErrors())
    }

    // ===== Zip-bomb protection =====

    @Test
    fun `write throws SecurityException when entry data exceeds size guard file limit`() {
        every { mockArchive.numberOfItems } returns 1
        every { mockArchive.getProperty(0, PropID.IS_FOLDER) } returns false
        every { mockArchive.getProperty(0, PropID.PATH) } returns "bomb.bin"

        val callback = SevenZipCallbackExtractor(
            inArchive = mockArchive,
            destinationPath = tempFolder.root,
            pathValidator = PathValidator(),
            selectedPaths = null,
            config = SevenZipCallbackExtractor.Config(
                sizeGuard = ArchiveSizeGuard(maxFileSizeBytes = 10L, maxTotalSizeBytes = 1000L),
                onProgress = { _, _, _ -> }
            )
        )

        val stream = callback.getStream(0, ExtractAskMode.EXTRACT)
        assertNotNull(stream)

        var threw = false
        try {
            stream!!.write(ByteArray(20))
        } catch (e: SecurityException) {
            threw = true
        }
        assertTrue("Oversized chunk must throw SecurityException", threw)
    }

    // ===== Cancellation =====

    @Test
    fun `getStream throws CancellationException when isActiveCheck returns false`() {
        every { mockArchive.numberOfItems } returns 1
        every { mockArchive.getProperty(0, PropID.IS_FOLDER) } returns false
        every { mockArchive.getProperty(0, PropID.PATH) } returns "file.txt"

        val callback = SevenZipCallbackExtractor(
            inArchive = mockArchive,
            destinationPath = tempFolder.root,
            pathValidator = PathValidator(),
            selectedPaths = null,
            config = SevenZipCallbackExtractor.Config(isActiveCheck = { false }, onProgress = { _, _, _ -> })
        )

        var threw = false
        try {
            callback.getStream(0, ExtractAskMode.EXTRACT)
        } catch (e: kotlinx.coroutines.CancellationException) {
            threw = true
        }
        assertTrue("Cancelled coroutine must abort before opening a new entry", threw)
    }

    @Test
    fun `write throws CancellationException mid-file once isActiveCheck flips to false`() {
        every { mockArchive.numberOfItems } returns 1
        every { mockArchive.getProperty(0, PropID.IS_FOLDER) } returns false
        every { mockArchive.getProperty(0, PropID.PATH) } returns "file.txt"

        var active = true
        val callback = SevenZipCallbackExtractor(
            inArchive = mockArchive,
            destinationPath = tempFolder.root,
            pathValidator = PathValidator(),
            selectedPaths = null,
            config = SevenZipCallbackExtractor.Config(isActiveCheck = { active }, onProgress = { _, _, _ -> })
        )

        val stream = callback.getStream(0, ExtractAskMode.EXTRACT)
        assertNotNull(stream)

        active = false
        var threw = false
        try {
            stream!!.write(ByteArray(10))
        } catch (e: kotlinx.coroutines.CancellationException) {
            threw = true
        }
        assertTrue("Cancellation mid-file must abort the native extract() call", threw)
    }

    // ===== No-op interface methods (IProgress / IArchiveExtractCallback) =====

    @Test
    fun `setTotal accepts value without throwing`() {
        val callback = makeCallback()
        callback.setTotal(1024L)
    }

    @Test
    fun `setCompleted accepts value without throwing`() {
        val callback = makeCallback()
        callback.setCompleted(512L)
    }

    @Test
    fun `prepareOperation accepts mode without throwing`() {
        val callback = makeCallback()
        callback.prepareOperation(ExtractAskMode.EXTRACT)
    }

    @Test
    fun `totalCount returns numberOfItems from archive`() {
        // Construct directly so the stub is not overridden by makeCallback()'s internal every-returns-3
        every { mockArchive.numberOfItems } returns 42
        val callback = SevenZipCallbackExtractor(
            inArchive = mockArchive,
            destinationPath = tempFolder.root,
            pathValidator = PathValidator(),
            selectedPaths = null,
            config = SevenZipCallbackExtractor.Config(onProgress = { _, _, _ -> })
        )
        assertEquals(42, callback.totalCount)
    }
}
