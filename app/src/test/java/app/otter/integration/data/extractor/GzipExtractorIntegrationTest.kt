package app.otter.data.extractor

import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class GzipExtractorIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val extractor = GzipExtractor(TempFileManager(), SevenZipExtractorHelper())

    // ===== Filename derivation =====

    @Test
    fun `extract gz - output filename strips dot-gz extension`() = runTest {
        val gz = createGzip("photo.jpg.gz", byteArrayOf(1, 2, 3))
        val outDir = tempFolder.newFolder("out1")

        extract(gz, "photo.jpg.gz", outDir)

        assertTrue("photo.jpg should exist", File(outDir, "photo.jpg").exists())
    }

    @Test
    fun `extract gzip - output filename strips dot-gzip extension`() = runTest {
        val gzip = createGzip("document.txt.gzip", "hello".toByteArray())
        val outDir = tempFolder.newFolder("out2")

        extract(gzip, "document.txt.gzip", outDir)

        assertTrue("document.txt should exist", File(outDir, "document.txt").exists())
    }

    @Test
    fun `extract gz - output filename fallback when no known extension`() = runTest {
        val gz = createGzip("data.gz", "bytes".toByteArray())
        val outDir = tempFolder.newFolder("out3")

        extract(gz, "data.gz", outDir)

        assertTrue("data should exist", File(outDir, "data").exists())
    }

    // ===== Content verification =====

    @Test
    fun `extract gz - output file contains exact decompressed bytes`() = runTest {
        val content = "Hello, Otter! This is a test.".toByteArray()
        val gz = createGzip("readme.txt.gz", content)
        val outDir = tempFolder.newFolder("out4")

        val result = extract(gz, "readme.txt.gz", outDir)

        assertTrue(result is ExtractionResult.Success)
        assertEquals(1, (result as ExtractionResult.Success).extractedFilesCount)
        val output = File(outDir, "readme.txt")
        assertTrue(output.exists())
        assertEquals(content.toList(), output.readBytes().toList())
    }

    // ===== Failure cases =====

    @Test
    fun `extract corrupted gz - fails gracefully`() = runTest {
        val corrupted = tempFolder.newFile("bad.gz")
        corrupted.writeBytes(byteArrayOf(0x1F, 0x8B.toByte(), 0x00, 0x00, 0x42)) // invalid GZIP body
        val outDir = tempFolder.newFolder("out5")

        val result = extract(corrupted, "bad.gz", outDir)

        assertTrue("Corrupted gz should fail", result is ExtractionResult.Failure)
    }

    // ===== Zip-bomb protection =====

    @Test
    fun `extract gz - decompressed content exceeding max file size fails (zip bomb protection)`() = runTest {
        val guardedExtractor = GzipExtractor(
            TempFileManager(),
            SevenZipExtractorHelper(),
            sizeGuardFactory = { ArchiveSizeGuard(maxFileSizeBytes = 10L, maxTotalSizeBytes = 1000L) }
        )
        val gz = createGzip("bomb.bin.gz", "x".repeat(50).toByteArray())
        val outDir = tempFolder.newFolder("out-bomb")

        val result = gz.inputStream().use { input ->
            guardedExtractor.extract(input, outDir, ArchiveType.GZIP, "bomb.bin.gz", null) {}
        }

        assertTrue("Should fail when decompressed content exceeds size limit", result is ExtractionResult.Failure)
    }

    // ===== Result metadata =====

    @Test
    fun `extract gz - ExtractionResult reports 1 extracted file`() = runTest {
        val gz = createGzip("single.bin.gz", byteArrayOf(0x01))
        val outDir = tempFolder.newFolder("out6")

        val result = extract(gz, "single.bin.gz", outDir)

        assertTrue(result is ExtractionResult.Success)
        assertEquals(1, (result as ExtractionResult.Success).extractedFilesCount)
    }

    @Test
    fun `extract gz - empty gzip stream returns Failure`() = runTest {
        // A valid gzip stream that decompresses to 0 bytes.
        // GzipCompressorOutputStream writing 0 bytes produces a valid header+footer but 0 content.
        val emptyGz = tempFolder.newFile("empty.bin.gz")
        GzipCompressorOutputStream(java.io.FileOutputStream(emptyGz)).use { /* write nothing */ }
        val outDir = tempFolder.newFolder("out-empty")

        val result = extract(emptyGz, "empty.bin.gz", outDir)

        assertTrue(
            "Gzip with 0 decompressed bytes must return Failure",
            result is ExtractionResult.Failure
        )
    }

    @Test
    fun `extract gz - selectedItems emptyList still extracts single file (GZIP ignores selection)`() = runTest {
        // PINNED: GzipExtractor never inspects selectedItems. Empty list does not suppress extraction.
        val gz = createGzip("document.txt.gz", "hello gzip".toByteArray())
        val outDir = tempFolder.newFolder("out-selected")

        val result = gz.inputStream().use { input ->
            extractor.extract(input, outDir, ArchiveType.GZIP, "document.txt.gz", emptyList()) {}
        }

        assertTrue("Success even with empty selectedItems", result is ExtractionResult.Success)
        assertTrue("Output file must exist", java.io.File(outDir, "document.txt").exists())
    }

    @Test
    fun `deriveOutputFileName with no gz or gzip extension leaves filename unchanged`() = runTest {
        // sourceFileName without .gz/.gzip → fallback returns unchanged name
        val gz = createGzip("noextension.gz", "data".toByteArray()) // real file is gz
        val outDir = tempFolder.newFolder("out-noext")

        // Pass sourceFileName without extension to simulate the fallback path
        val result = gz.inputStream().use { input ->
            extractor.extract(input, outDir, ArchiveType.GZIP, "noextension", null) {}
        }

        // Fallback: output filename = "noextension" (sourceFileName returned unchanged)
        assertTrue(result is ExtractionResult.Success)
        assertTrue("Output file named 'noextension' must exist",
            java.io.File(outDir, "noextension").exists())
    }

    // ===== Cancellation contract =====

    @Test
    fun `extract gz - already-cancelled coroutine throws CancellationException`() {
        val gz = createGzip("cancel-pin.bin.gz", "content".toByteArray())
        val outDir = tempFolder.newFolder("out-cancel-pin")

        val cancelled = Job().also { it.cancel() }
        assertThrows(CancellationException::class.java) {
            runBlocking(cancelled) {
                gz.inputStream().use { input ->
                    extractor.extract(input, outDir, ArchiveType.GZIP, gz.name, null) {}
                }
            }
        }
    }

    @Test
    fun `extract gz - cancelling mid-copy stops the loop instead of running to completion`() {
        // Random (incompressible) so the compressed stream stays ~2MB too — a repeating
        // pattern would compress down to a few KB and finish before cancellation can land.
        val largeContent = ByteArray(2_000_000).also { java.util.Random(42).nextBytes(it) }
        val gz = createGzip("large.bin.gz", largeContent)
        val outDir = tempFolder.newFolder("out-cancel-midloop")

        // Throttles each read() so the copy loop takes long enough for a cancel() issued
        // after a short real delay to land mid-loop deterministically, instead of racing
        // completion. Uses runBlocking (real time) rather than runTest: the extractor's own
        // withContext(Dispatchers.IO) hop is a real dispatcher switch that a virtual-time
        // test scheduler cannot coordinate with reliably.
        val throttled = object : InputStream() {
            private val delegate = gz.inputStream()
            override fun read(): Int = delegate.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                Thread.sleep(20)
                return delegate.read(b, off, len)
            }

            override fun close() = delegate.close()
        }

        runBlocking {
            val job = launch(Dispatchers.Default) {
                throttled.use { input ->
                    extractor.extract(input, outDir, ArchiveType.GZIP, gz.name, null) {}
                }
            }
            delay(60) // let the loop run through a couple of throttled reads first
            job.cancel()
            job.join()

            assertTrue("Extraction job must be cancelled, not left to run to completion", job.isCancelled)
        }
    }

    // ===== Helpers =====

    private suspend fun extract(file: File, sourceFileName: String, outDir: File): ExtractionResult =
        file.inputStream().use { input ->
            extractor.extract(input, outDir, ArchiveType.GZIP, sourceFileName, null) {}
        }

    private fun createGzip(name: String, content: ByteArray): File {
        val file = tempFolder.newFile(name)
        GzipCompressorOutputStream(FileOutputStream(file)).use { it.write(content) }
        return file
    }
}
