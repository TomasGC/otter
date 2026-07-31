package app.otter.data.extractor

import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionResult
import app.otter.util.PathValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class RpaExtractorSelectiveMockIntegrationTest {

    private lateinit var outputDir: File
    private lateinit var extractor: RpaExtractor
    private lateinit var rpaFile: File

    @Before
    fun setup() {
        val archivesDir = System.getProperty("archives.dir")
            ?: error("archives.dir system property not set")
        rpaFile = File(archivesDir, "test_archive.rpa")
        assumeTrue("test_archive.rpa must exist", rpaFile.exists())

        outputDir = File.createTempFile("rpa_selective_test", "").apply {
            delete()
            mkdir()
        }
        extractor = RpaExtractor(PathValidator(), TempFileManager(), SevenZipExtractorHelper())
    }

    @After
    fun cleanup() {
        outputDir.deleteRecursively()
    }

    @Test
    fun `null selectedItems extracts all entries`() = runTest {
        val result = extract(selectedItems = null)

        assertTrue(result is ExtractionResult.Success)
        assertTrue((result as ExtractionResult.Success).extractedFilesCount > 0)
    }

    @Test
    fun `empty selectedItems extracts nothing`() = runTest {
        val result = extract(selectedItems = emptyList())

        assertTrue(result is ExtractionResult.Success)
        assertEquals(0, (result as ExtractionResult.Success).extractedFilesCount)
        assertTrue("No files should be extracted", outputDir.listFiles()?.isEmpty() ?: true)
    }

    @Test
    fun `single selected item extracts only that file`() = runTest {
        // First do a full extraction to discover real entry names
        val fullOutputDir = File.createTempFile("rpa_full_", "").apply { delete(); mkdir() }
        try {
            val fullResult = rpaFile.inputStream().use { input ->
                extractor.extract(input, fullOutputDir, ArchiveType.RPA, rpaFile.name, ) {}
            }
            assertTrue(fullResult is ExtractionResult.Success)
            val allFiles = fullOutputDir.walkTopDown().filter { it.isFile }.toList()
            assumeTrue("Archive must have at least 1 file", allFiles.isNotEmpty())

            // Pick the first file's relative path as selection
            val relPath = allFiles.first().relativeTo(fullOutputDir).path.replace(File.separator, "/")

            val result = extract(selectedItems = listOf(relPath))

            assertEquals(1, (result as ExtractionResult.Success).extractedFilesCount)
            val extractedFiles = outputDir.walkTopDown().filter { it.isFile }.toList()
            assertEquals(1, extractedFiles.size)
        } finally {
            fullOutputDir.deleteRecursively()
        }
    }

    @Test
    fun `directory prefix selected item extracts all children but not siblings`() = runTest {
        val fullOutputDir = File.createTempFile("rpa_dir_prefix_full_", "").apply { delete(); mkdir() }
        try {
            val fullResult = rpaFile.inputStream().use { input ->
                extractor.extract(input, fullOutputDir, ArchiveType.RPA, rpaFile.name, ) {}
            }
            assertTrue(fullResult is ExtractionResult.Success)

            val allRelPaths = fullOutputDir.walkTopDown()
                .filter { it.isFile }
                .map { it.relativeTo(fullOutputDir).path.replace(File.separator, "/") }
                .toList()

            val filesInDirs = allRelPaths.filter { it.contains("/") }
            assumeTrue("RPA test archive has no directory structure for prefix test", filesInDirs.isNotEmpty())

            val dirPrefix = filesInDirs.first().substringBefore("/") + "/"
            val expectedCount = filesInDirs.count { it.startsWith(dirPrefix) }

            val result = extract(selectedItems = listOf(dirPrefix))

            assertEquals(expectedCount, (result as ExtractionResult.Success).extractedFilesCount)
            val extractedFiles = outputDir.walkTopDown().filter { it.isFile }.toList()
            assertEquals(expectedCount, extractedFiles.size)
        } finally {
            fullOutputDir.deleteRecursively()
        }
    }

    @Test
    fun `extract rpa - already-cancelled coroutine throws CancellationException`() {
        assumeTrue("test_archive.rpa must exist", rpaFile.exists())

        // withContext(cancelledJob) throws CancellationException immediately — the coroutine
        // runtime refuses to enter a dead context. Asserting the throw makes this test meaningful.
        val cancelled = Job().also { it.cancel() }
        assertThrows(CancellationException::class.java) {
            runBlocking(cancelled) {
                rpaFile.inputStream().use { input ->
                    extractor.extract(input, outputDir, ArchiveType.RPA, rpaFile.name, ) {}
                }
            }
        }
    }

    @Test
    fun `extract fails gracefully when file has wrong magic version (RPA-2 dot 0)`() = runTest {
        // RPA-2.0 is a different format; extractor should return Failure, not crash.
        val wrongMagic = "RPA-2.0 0000000000000020 DEADBEEF\n".toByteArray(Charsets.US_ASCII)
        val fakeRpa = java.io.File.createTempFile("wrong_magic", ".rpa")
        fakeRpa.writeBytes(wrongMagic)

        val outDir = java.io.File.createTempFile("rpa_wrong_", "").apply { delete(); mkdir() }
        try {
            val result = fakeRpa.inputStream().use { input ->
                extractor.extract(input, outDir, ArchiveType.RPA, fakeRpa.name, ) {}
            }
            assertTrue(
                "Wrong RPA magic must produce Failure",
                result is ExtractionResult.Failure
            )
        } finally {
            fakeRpa.delete()
            outDir.deleteRecursively()
        }
    }

    // ===== Zip-bomb protection =====

    @Test
    fun `extract rpa - entry exceeding max file size fails (zip bomb protection)`() = runTest {
        val bombFile = File.createTempFile("rpa_bomb_", ".rpa")
        val bombOutputDir = File.createTempFile("rpa_bomb_out_", "").apply { delete(); mkdir() }
        try {
            TestArchiveHelper.createRpaArchive(bombFile)
            val guardedExtractor = RpaExtractor(
                PathValidator(),
                TempFileManager(),
                SevenZipExtractorHelper(),
                sizeGuardFactory = { ArchiveSizeGuard(maxFileSizeBytes = 2L, maxTotalSizeBytes = 1000L) }
            )

            val result = bombFile.inputStream().use { input ->
                guardedExtractor.extract(input, bombOutputDir, ArchiveType.RPA, bombFile.name, ) {}
            }

            assertTrue("Should fail when an entry exceeds the per-file size limit", result is ExtractionResult.Failure)
        } finally {
            bombFile.delete()
            bombOutputDir.deleteRecursively()
        }
    }

    private suspend fun extract(selectedItems: List<String>?): ExtractionResult =
        rpaFile.inputStream().use { input ->
            extractor.extract(input, outputDir, ArchiveType.RPA, rpaFile.name, ExtractionOptions(selectedItems = selectedItems)) {}
        }
}
