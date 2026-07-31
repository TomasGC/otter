package app.otter.data.extractor

import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionResult
import app.otter.util.PathValidator
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

class TarExtractorMockIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var outputDir: File
    private lateinit var extractor: TarExtractor

    @Before
    fun setup() {
        outputDir = tempFolder.newFolder("output")
        extractor = TarExtractor(PathValidator(), TempFileManager(), SevenZipExtractorHelper())
    }

    // ===== TAR — content verification =====

    @Test
    fun `extract TAR - files land in output directory with correct content`() = runTest {
        val tar = createTar("archive.tar", mapOf(
            "hello.txt" to "world".toByteArray(),
            "data/bin.bin" to byteArrayOf(0x01, 0x02, 0x03)
        ))

        val result = extract(tar, ArchiveType.TAR)

        assertTrue(result is ExtractionResult.Success)
        assertEquals(2, (result as ExtractionResult.Success).extractedFilesCount)
        assertEquals("world", File(outputDir, "hello.txt").readText())
        assertEquals(3, File(outputDir, "data/bin.bin").length())
    }

    @Test
    fun `extract TAR - nested directories are created`() = runTest {
        val tar = createTar("nested.tar", mapOf(
            "a/b/c/deep.txt" to "deep".toByteArray()
        ))

        val result = extract(tar, ArchiveType.TAR)

        assertTrue(result is ExtractionResult.Success)
        assertTrue(File(outputDir, "a/b/c/deep.txt").exists())
        assertEquals("deep", File(outputDir, "a/b/c/deep.txt").readText())
    }

    @Test
    fun `extract TAR - directory entries are skipped (not counted)`() = runTest {
        val tar = createTar("dirs.tar", mapOf(
            "subdir/" to byteArrayOf(),
            "subdir/file.txt" to "content".toByteArray()
        ))

        val result = extract(tar, ArchiveType.TAR)

        assertTrue(result is ExtractionResult.Success)
        assertEquals(1, (result as ExtractionResult.Success).extractedFilesCount)
        assertEquals("content", File(outputDir, "subdir/file.txt").readText())
    }

    // ===== TAR_GZ =====

    @Test
    fun `extract TAR_GZ - files land in output directory with correct content`() = runTest {
        val tarGz = createTarGz("archive.tar.gz", mapOf(
            "readme.md" to "# Hello".toByteArray()
        ))

        val result = extract(tarGz, ArchiveType.TAR_GZ)

        assertTrue(result is ExtractionResult.Success)
        assertEquals(1, (result as ExtractionResult.Success).extractedFilesCount)
        assertEquals("# Hello", File(outputDir, "readme.md").readText())
    }

    // ===== TAR_BZ2 =====

    @Test
    fun `extract TAR_BZ2 - files land in output directory with correct content`() = runTest {
        val tarBz2 = createTarBz2("archive.tar.bz2", mapOf(
            "config.json" to """{"key":"val"}""".toByteArray()
        ))

        val result = extract(tarBz2, ArchiveType.TAR_BZ2)

        assertTrue(result is ExtractionResult.Success)
        assertEquals(1, (result as ExtractionResult.Success).extractedFilesCount)
        assertEquals("""{"key":"val"}""", File(outputDir, "config.json").readText())
    }

    // ===== Selective extraction =====

    @Test
    fun `selective TAR - null selectedItems extracts all`() = runTest {
        val tar = createTar("all.tar", mapOf(
            "a.txt" to "a".toByteArray(),
            "b.txt" to "b".toByteArray(),
            "c.txt" to "c".toByteArray()
        ))

        val result = extractSelective(tar, ArchiveType.TAR, selectedItems = null)

        assertEquals(3, (result as ExtractionResult.Success).extractedFilesCount)
        assertTrue(File(outputDir, "a.txt").exists())
        assertTrue(File(outputDir, "b.txt").exists())
        assertTrue(File(outputDir, "c.txt").exists())
    }

    @Test
    fun `selective TAR - extracts only specified files`() = runTest {
        val tar = createTar("selective.tar", mapOf(
            "keep.txt" to "keep".toByteArray(),
            "skip.txt" to "skip".toByteArray(),
            "also_keep.txt" to "also".toByteArray()
        ))

        val result = extractSelective(tar, ArchiveType.TAR, selectedItems = listOf("keep.txt", "also_keep.txt"))

        assertEquals(2, (result as ExtractionResult.Success).extractedFilesCount)
        assertTrue(File(outputDir, "keep.txt").exists())
        assertTrue(File(outputDir, "also_keep.txt").exists())
        assertFalse(File(outputDir, "skip.txt").exists())
    }

    @Test
    fun `selective TAR - empty selection extracts nothing`() = runTest {
        val tar = createTar("empty_sel.tar", mapOf(
            "file.txt" to "content".toByteArray()
        ))

        val result = extractSelective(tar, ArchiveType.TAR, selectedItems = emptyList())

        assertEquals(0, (result as ExtractionResult.Success).extractedFilesCount)
        assertFalse(File(outputDir, "file.txt").exists())
    }

    @Test
    fun `selective TAR - unknown selection path extracts nothing`() = runTest {
        val tar = createTar("unknown_sel.tar", mapOf(
            "file.txt" to "content".toByteArray()
        ))

        val result = extractSelective(tar, ArchiveType.TAR, selectedItems = listOf("nonexistent.txt"))

        assertEquals(0, (result as ExtractionResult.Success).extractedFilesCount)
        assertFalse(File(outputDir, "file.txt").exists())
    }

    @Test
    fun `selective TAR - directory prefix extracts all children but not siblings`() = runTest {
        val tar = createTar("dir_sel.tar", mapOf(
            "images/a.jpg" to byteArrayOf(1, 2, 3),
            "images/b.jpg" to byteArrayOf(4, 5, 6),
            "docs/readme.txt" to byteArrayOf(7, 8, 9)
        ))

        val result = extractSelective(tar, ArchiveType.TAR, selectedItems = listOf("images/"))

        assertTrue(result is ExtractionResult.Success)
        assertEquals(2, (result as ExtractionResult.Success).extractedFilesCount)
        assertTrue(File(outputDir, "images/a.jpg").exists())
        assertTrue(File(outputDir, "images/b.jpg").exists())
        assertFalse(File(outputDir, "docs/readme.txt").exists())
    }

    // ===== Cancellation contract =====

    @Test
    fun `extract tar - always returns Success even when result is partial (cancellation contract)`() = runTest {
        // TAR extracts with isActive checks but always returns ExtractionResult.Success.
        // This test pins the success contract by verifying a normal single-entry TAR returns Success.
        // Cancellation mid-loop also produces Success (partial count) — documented here.
        val tarFile = createTar("test_cancel_pin.tar", mapOf("entry.txt" to "content".toByteArray()))
        val outDir = tempFolder.newFolder("tar-cancel-pin")

        val result = tarFile.inputStream().use { input ->
            extractor.extract(input, outDir, ArchiveType.TAR, tarFile.name, ) {}
        }

        assertTrue(
            "TAR extractor always returns ExtractionResult.Success (even for partial extraction on cancel)",
            result is ExtractionResult.Success
        )
    }

    // ===== Zip-bomb protection =====

    @Test
    fun `extract TAR - entry exceeding max file size fails (zip bomb protection)`() = runTest {
        val guardedExtractor = TarExtractor(
            PathValidator(),
            TempFileManager(),
            SevenZipExtractorHelper(),
            sizeGuardFactory = { ArchiveSizeGuard(maxFileSizeBytes = 10L, maxTotalSizeBytes = 1000L) }
        )
        val tar = createTar("bomb.tar", mapOf("bomb.txt" to "x".repeat(50).toByteArray()))

        val result = tar.inputStream().use { input ->
            guardedExtractor.extract(input, outputDir, ArchiveType.TAR, tar.name, ) {}
        }

        assertTrue("Should fail when entry exceeds per-file size limit", result is ExtractionResult.Failure)
    }

    // ===== Empty archives =====

    @Test
    fun `extract TAR - empty archive returns Success with 0 files`() = runTest {
        val tar = createTar("empty.tar", emptyMap())

        val result = extract(tar, ArchiveType.TAR)

        assertTrue(result is ExtractionResult.Success)
        assertEquals(0, (result as ExtractionResult.Success).extractedFilesCount)
    }

    @Test
    fun `extract TAR_GZ - empty archive returns Success with 0 files`() = runTest {
        val tarGz = createTarGz("empty.tar.gz", emptyMap())

        val result = extract(tarGz, ArchiveType.TAR_GZ)

        assertTrue(result is ExtractionResult.Success)
        assertEquals(0, (result as ExtractionResult.Success).extractedFilesCount)
    }

    @Test
    fun `extract TAR_BZ2 - empty archive returns Success with 0 files`() = runTest {
        val tarBz2 = createTarBz2("empty.tar.bz2", emptyMap())

        val result = extract(tarBz2, ArchiveType.TAR_BZ2)

        assertTrue(result is ExtractionResult.Success)
        assertEquals(0, (result as ExtractionResult.Success).extractedFilesCount)
    }

    // ===== Corruption =====

    @Test
    fun `extract corrupted TAR - fails gracefully`() = runTest {
        val corrupted = tempFolder.newFile("corrupted.tar")
        // TAR reads 512-byte blocks; fill with non-zero garbage to trigger checksum mismatch
        corrupted.writeBytes(ByteArray(512) { (it % 255 + 1).toByte() })

        val result = extract(corrupted, ArchiveType.TAR)

        assertTrue("Corrupted TAR should fail", result is ExtractionResult.Failure)
    }

    // ===== Helpers =====

    private suspend fun extract(file: File, type: ArchiveType): ExtractionResult =
        file.inputStream().use { input ->
            extractor.extract(input, outputDir, type, file.name) {}
        }

    private suspend fun extractSelective(
        file: File,
        type: ArchiveType,
        selectedItems: List<String>?
    ): ExtractionResult =
        file.inputStream().use { input ->
            extractor.extract(input, outputDir, type, file.name, ExtractionOptions(selectedItems = selectedItems)) {}
        }

    private fun createTar(name: String, entries: Map<String, ByteArray>): File {
        val file = tempFolder.newFile(name)
        TarArchiveOutputStream(FileOutputStream(file)).use { tar ->
            for ((path, data) in entries) {
                val entry = TarArchiveEntry(path)
                entry.size = data.size.toLong()
                tar.putArchiveEntry(entry)
                tar.write(data)
                tar.closeArchiveEntry()
            }
        }
        return file
    }

    private fun createTarGz(name: String, entries: Map<String, ByteArray>): File {
        val file = tempFolder.newFile(name)
        TarArchiveOutputStream(GzipCompressorOutputStream(FileOutputStream(file))).use { tar ->
            for ((path, data) in entries) {
                val entry = TarArchiveEntry(path)
                entry.size = data.size.toLong()
                tar.putArchiveEntry(entry)
                tar.write(data)
                tar.closeArchiveEntry()
            }
        }
        return file
    }

    private fun createTarBz2(name: String, entries: Map<String, ByteArray>): File {
        val file = tempFolder.newFile(name)
        TarArchiveOutputStream(BZip2CompressorOutputStream(FileOutputStream(file))).use { tar ->
            for ((path, data) in entries) {
                val entry = TarArchiveEntry(path)
                entry.size = data.size.toLong()
                tar.putArchiveEntry(entry)
                tar.write(data)
                tar.closeArchiveEntry()
            }
        }
        return file
    }
}
