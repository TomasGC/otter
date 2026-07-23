package app.otter.data.extractor

import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionResult
import app.otter.util.PathValidator
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Integration-real tests for RpaExtractor with corrupted real files.
 * No mocks — only real files on disk.
 */
class RpaExtractorRealIntegrationTest {

    private lateinit var outputDir: File
    private lateinit var extractor: RpaExtractor

    @Before
    fun setup() {
        outputDir = File.createTempFile("rpa_real_corrupt_", "").apply { delete(); mkdir() }
        extractor = RpaExtractor(PathValidator(), TempFileManager(), SevenZipExtractorHelper())
    }

    @After
    fun cleanup() {
        outputDir.deleteRecursively()
    }

    @Test
    fun `extract returns Failure for file with rpa extension but non-rpa content`() = runTest {
        // Rename any non-RPA file to .rpa — extractor must handle gracefully.
        val corruptFile = File.createTempFile("corrupt", ".rpa")
        try {
            corruptFile.writeText("This is not an RPA file at all. Just plain text.")

            val result = corruptFile.inputStream().use { input ->
                extractor.extract(input, outputDir, ArchiveType.RPA, corruptFile.name, null) {}
            }

            assertTrue(
                "Corrupted .rpa file must return ExtractionResult.Failure",
                result is ExtractionResult.Failure
            )
        } finally {
            corruptFile.delete()
        }
    }

    @Test
    fun `extract returns Failure for zero-byte rpa file`() = runTest {
        val emptyFile = File.createTempFile("empty", ".rpa")
        try {
            // emptyFile is 0 bytes by default

            val result = emptyFile.inputStream().use { input ->
                extractor.extract(input, outputDir, ArchiveType.RPA, emptyFile.name, null) {}
            }

            assertTrue(
                "Zero-byte .rpa must return ExtractionResult.Failure",
                result is ExtractionResult.Failure
            )
        } finally {
            emptyFile.delete()
        }
    }
}
