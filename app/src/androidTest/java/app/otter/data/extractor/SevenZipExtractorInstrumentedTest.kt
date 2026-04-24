package app.otter.data.extractor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import java.io.File

/**
 * Instrumented test for SevenZipExtractor with real device/emulator.
 * Tests 7z extraction with native 7-Zip-JBinding libraries.
 */
@RunWith(AndroidJUnit4::class)
class SevenZipExtractorInstrumentedTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val pathValidator = PathValidator()
    private val archiveLibraryManager = ArchiveLibraryManager()
    private val extractor = SevenZipExtractor(pathValidator, archiveLibraryManager)

    @Test
    fun testSupportsSevenZipType() {
        assertTrue(extractor.supports(ArchiveType.SEVEN_ZIP))
    }

    @Test
    fun testExtractReal7zFile() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().context
        val test7z = context.assets.open("archives/test.7z")
        val destination = tempFolder.newFolder("output")

        val result = extractor.extract(
            inputStream = test7z,
            destinationPath = destination,
            onProgress = {}
        )

        // Validate successful extraction
        assertTrue("Expected Success, got ${result::class.simpleName}",
            result is ExtractionResult.Success)

        val extractedCount = (result as ExtractionResult.Success).extractedFilesCount
        assertTrue("Expected at least 1 file, got $extractedCount", extractedCount >= 1)

        // Verify extracted files
        val extractedFiles = destination.walk().filter { it.isFile }.toList()
        assertTrue("No files extracted", extractedFiles.isNotEmpty())

        // Verify content
        val firstFile = extractedFiles.first()
        val content = firstFile.readText().trim()
        assertTrue("Expected '7z', got '$content'", content.contains("7z", ignoreCase = true))
    }
}
