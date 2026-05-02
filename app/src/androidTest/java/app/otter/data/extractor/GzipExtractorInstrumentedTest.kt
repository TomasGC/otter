package app.otter.data.extractor

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import app.otter.util.PathValidator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * Instrumented test for ApacheGzipExtractor with real device/emulator.
 * Tests .gz and .gzip decompression with Apache Commons Compress.
 */
@RunWith(AndroidJUnit4::class)
class GzipExtractorInstrumentedTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val pathValidator = PathValidator()
    private val extractor = ApacheGzipExtractor(
        pathValidator = pathValidator,
        tempFileManager = TempFileManager(),
        sevenZipHelper = SevenZipExtractorHelper()
    )

    @Test
    fun testSupportsGzipType() {
        assertTrue(extractor.supports(ArchiveType.GZIP))
    }

    @Test
    fun testExtractRealGzFile() = runTest {
        // Create test archive programmatically
        val testGzFile = tempFolder.newFile("file.txt.gz")
        TestArchiveHelper.createGzFile(testGzFile)

        val destination = tempFolder.newFolder("output-gz")

        val result = extractor.extract(
            inputStream = testGzFile.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.GZIP,
            sourceFileName = "file.txt.gz",
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Success)

        val extractedCount = (result as ExtractionResult.Success).extractedFilesCount
        assertEquals("Expected exactly 1 file", 1, extractedCount)

        // Verify extracted file
        val extractedFiles = destination.walk().filter { it.isFile }.toList()
        assertEquals("Expected exactly 1 file", 1, extractedFiles.size)

        // Verify filename (file.txt.gz → file.txt)
        val extractedFile = extractedFiles.first()
        assertEquals("Expected filename 'file.txt', got '${extractedFile.name}'", "file.txt", extractedFile.name)

        // Verify content (file.txt.gz contains "Gz")
        val content = extractedFile.readText().trim()
        assertEquals("Expected 'Gz', got '$content'", "Gz", content)
    }

    @Test
    fun testExtractRealGzipFile() = runTest {
        // Create test archive programmatically
        val testGzipFile = tempFolder.newFile("file.txt.gzip")
        TestArchiveHelper.createGzipFile(testGzipFile)

        val destination = tempFolder.newFolder("output-gzip")

        val result = extractor.extract(
            inputStream = testGzipFile.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.GZIP,
            sourceFileName = "file.txt.gzip",
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Success)

        val extractedCount = (result as ExtractionResult.Success).extractedFilesCount
        assertEquals("Expected exactly 1 file", 1, extractedCount)

        // Verify extracted file
        val extractedFiles = destination.walk().filter { it.isFile }.toList()
        assertEquals("Expected exactly 1 file", 1, extractedFiles.size)

        // Verify filename (file.txt.gzip → file.txt)
        val extractedFile = extractedFiles.first()
        assertEquals("Expected filename 'file.txt', got '${extractedFile.name}'", "file.txt", extractedFile.name)

        // Verify content (file.txt.gzip contains "Gzip")
        val content = extractedFile.readText().trim()
        assertEquals("Expected 'Gzip', got '$content'", "Gzip", content)
    }

    @Test
    fun testExtractGzWithProgress() = runTest {
        val testGzFile = tempFolder.newFile("file-progress.txt.gz")
        TestArchiveHelper.createGzFile(testGzFile)

        val destination = tempFolder.newFolder("output-gz-progress")
        val progressValues = mutableListOf<Int>()

        val result = extractor.extract(
            inputStream = testGzFile.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.GZIP,
            sourceFileName = "file-progress.txt.gz",
            onProgress = { progress ->
                if (progress is ExtractionProgress.Extracting) {
                    progressValues.add((progress.progress * 100).toInt())
                }
            }
        )

        assertTrue(result is ExtractionResult.Success)
        assertTrue("Progress callback should be called", progressValues.isNotEmpty())
        assertTrue("Final progress should be 100", progressValues.last() == 100)
    }
}
