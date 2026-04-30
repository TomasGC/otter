package app.otter.data.extractor

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otter.domain.model.ArchiveType
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
 * Instrumented test for ZipExtractor with real device/emulator.
 * Tests ZIP extraction with Android's native ZipFile.
 */
@RunWith(AndroidJUnit4::class)
class ZipExtractorInstrumentedTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val pathValidator = PathValidator()
    private val extractor = ZipExtractor(pathValidator)

    @Test
    fun testSupportsZipType() {
        assertTrue(extractor.supports(ArchiveType.ZIP))
    }

    @Test
    fun testExtractRealZipFile() = runTest {
        // Create test archive programmatically
        val testZipFile = tempFolder.newFile("test.zip")
        TestArchiveHelper.createZipFile(testZipFile)

        val destination = tempFolder.newFolder("output-zip")

        val result = extractor.extract(
            inputStream = testZipFile.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test.zip",
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Success)

        val extractedCount = (result as ExtractionResult.Success).extractedFilesCount
        assertEquals("Expected exactly 1 file", 1, extractedCount)

        // Verify extracted files
        val extractedFiles = destination.walk().filter { it.isFile }.toList()
        assertEquals("Expected exactly 1 file", 1, extractedFiles.size)

        // Verify content (test.zip contains testZip/file.txt → "Zip")
        val file = extractedFiles.first()
        assertTrue("Expected path contains 'testZip'", file.path.contains("testZip"))
        assertEquals("Expected filename 'file.txt'", "file.txt", file.name)

        val content = file.readText().trim()
        assertEquals("Expected content 'Zip', got '$content'", "Zip", content)
    }
}
