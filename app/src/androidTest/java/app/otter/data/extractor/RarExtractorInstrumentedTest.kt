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
 * Instrumented test for RarExtractor with real device/emulator.
 * Tests RAR5 extraction with native 7-Zip-JBinding libraries.
 */
@RunWith(AndroidJUnit4::class)
class RarExtractorInstrumentedTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val pathValidator = PathValidator()
    private val extractor = RarExtractor(pathValidator)

    @Test
    fun testSupportsRarType() {
        assertTrue(extractor.supports(ArchiveType.RAR))
    }

    @Test
    fun testExtractRealRarFile() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().context
        val testRar = context.assets.open("archives/test.rar")
        val destination = tempFolder.newFolder("output")

        val result = extractor.extract(
            inputStream = testRar,
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
        assertTrue("Expected 'RAR', got '$content'", content.contains("RAR"))
    }
}
