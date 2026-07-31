package app.otter.data.extractor

import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import app.otter.util.PathValidator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class RpaExtractorMockIntegrationTest {

    private lateinit var outputDir: File

    @Before
    fun setup() {
        outputDir = File.createTempFile("rpa_extract", "").apply {
            delete()
            mkdir()
        }
    }

    @After
    fun cleanup() {
        outputDir.deleteRecursively()
    }

    @Test
    fun `extract files from RPA archive`() = runBlocking {
        val archivesDir = System.getProperty("archives.dir")
            ?: error("archives.dir system property not set (should be set by Gradle)")
        val rpaFile = File(archivesDir, "test_archive.rpa")
        require(rpaFile.exists()) { "Test archive not found at: ${rpaFile.absolutePath}" }

        println("=== Testing RPA extraction ===")

        // Use real implementations with defaults
        val extractor = RpaExtractor(
            pathValidator = PathValidator(),
            tempFileManager = TempFileManager(),
            sevenZipHelper = SevenZipExtractorHelper()
        )

        var extractedCount = 0

        val result = rpaFile.inputStream().use { input ->
            extractor.extract(
                inputStream = input,
                destinationPath = outputDir,
                archiveType = ArchiveType.RPA,
                sourceFileName = "test_archive.rpa",
                onProgress = { progress ->
                    if (progress is ExtractionProgress.Extracting) {
                        extractedCount = progress.extractedCount
                        if (extractedCount <= 10 || extractedCount % 50000 == 0) {
                            println("  [$extractedCount/${progress.totalCount}] ${progress.currentFile}")
                        }
                    }
                }
            )
        }

        when (result) {
            is ExtractionResult.Success -> {
                println("✅ Extracted ${result.extractedFilesCount} files to ${outputDir.absolutePath}")
                
                // Verify files were extracted
                val extractedFiles = outputDir.walkTopDown().filter { it.isFile }.toList()
                require(extractedFiles.isNotEmpty()) { "No files extracted to ${outputDir.absolutePath}" }
                require(result.extractedFilesCount > 0) { "extractedFilesCount should be > 0" }

                println("✅ Verified ${extractedFiles.size} files extracted")
                println("✅ RPA extraction test PASSED!")
            }
            is ExtractionResult.Failure -> {
                println("❌ Extraction FAILED: ${result.errorMessage}")
                throw AssertionError("Extraction failed", result.cause)
            }
        }
    }
}
