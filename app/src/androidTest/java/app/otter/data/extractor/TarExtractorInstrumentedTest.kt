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
 * Instrumented test for TarExtractor with real device/emulator.
 * Tests tar, tar.gz, and tgz extraction with Apache Commons Compress.
 */
@RunWith(AndroidJUnit4::class)
class TarExtractorInstrumentedTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val pathValidator = PathValidator()
    private val extractor = TarExtractor(pathValidator)

    @Test
    fun testSupportsTarType() {
        assertTrue(extractor.supports(ArchiveType.TAR))
    }

    @Test
    fun testSupportsTarGzType() {
        assertTrue(extractor.supports(ArchiveType.TAR_GZ))
    }

    @Test
    fun testExtractRealTarFile() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().context
        val testTar = context.assets.open("archives/test-plain.tar")
        val destination = tempFolder.newFolder("output-tar")

        val result = extractor.extract(
            inputStream = testTar,
            destinationPath = destination,
            onProgress = {}
        )

        // Validate successful extraction
        assertTrue(
            "Expected Success, got ${result::class.simpleName}",
            result is ExtractionResult.Success
        )

        val extractedCount = (result as ExtractionResult.Success).extractedFilesCount
        assertTrue("Expected at least 1 file, got $extractedCount", extractedCount >= 1)

        // Verify extracted files
        val extractedFiles = destination.walk().filter { it.isFile }.toList()
        assertTrue("No files extracted", extractedFiles.isNotEmpty())

        // Verify content (test-plain.tar contains testTar/ folder with file.txt containing "Tar")
        val firstFile = extractedFiles.first()
        val content = firstFile.readText().trim()
        assertTrue("Expected 'Tar', got '$content'", content.contains("Tar"))
    }

    @Test
    fun testExtractRealTarGzFile() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().context
        val testTarGz = context.assets.open("archives/test.tar.gz")
        val destination = tempFolder.newFolder("output-tar-gz")

        val result = extractor.extract(
            inputStream = testTarGz,
            destinationPath = destination,
            onProgress = {}
        )

        // Validate successful extraction
        assertTrue(
            "Expected Success, got ${result::class.simpleName}",
            result is ExtractionResult.Success
        )

        val extractedCount = (result as ExtractionResult.Success).extractedFilesCount
        assertTrue("Expected at least 1 file, got $extractedCount", extractedCount >= 1)

        // Verify extracted files
        val extractedFiles = destination.walk().filter { it.isFile }.toList()
        assertTrue("No files extracted", extractedFiles.isNotEmpty())

        // Verify content (test.tar.gz contains testTarGz/ folder with file.txt containing "TarGz")
        val firstFile = extractedFiles.first()
        val content = firstFile.readText().trim()
        assertTrue("Expected 'TarGz', got '$content'", content.contains("TarGz"))
    }

    @Test
    fun testExtractRealTgzFile() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().context
        val testTgz = context.assets.open("archives/test.tgz")
        val destination = tempFolder.newFolder("output-tgz")

        val result = extractor.extract(
            inputStream = testTgz,
            destinationPath = destination,
            onProgress = {}
        )

        // Validate successful extraction
        assertTrue(
            "Expected Success, got ${result::class.simpleName}",
            result is ExtractionResult.Success
        )

        val extractedCount = (result as ExtractionResult.Success).extractedFilesCount
        assertTrue("Expected at least 1 file, got $extractedCount", extractedCount >= 1)

        // Verify extracted files
        val extractedFiles = destination.walk().filter { it.isFile }.toList()
        assertTrue("No files extracted", extractedFiles.isNotEmpty())

        // Verify content (test.tgz contains testTgz/ folder with file.txt containing "Tgz")
        val firstFile = extractedFiles.first()
        val content = firstFile.readText().trim()
        assertTrue("Expected 'Tgz', got '$content'", content.contains("Tgz"))
    }
}
