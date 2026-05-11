package app.otter.data.extractor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import app.otter.util.PathValidator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented tests for RpaExtractor.
 *
 * These tests run on a real Android device/emulator to validate RPA-3.0 extraction.
 */
@RunWith(AndroidJUnit4::class)
class RpaExtractorInstrumentedTest {

    private lateinit var context: android.content.Context
    private lateinit var pathValidator: PathValidator
    private lateinit var extractor: RpaExtractor
    private lateinit var testOutputDir: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        pathValidator = PathValidator()

        // Create extractor instance
        extractor = RpaExtractor(
            pathValidator = pathValidator,
            tempFileManager = TempFileManager(),
            sevenZipHelper = SevenZipExtractorHelper(StandardProgressCalculator())
        )

        // Create test output directory
        testOutputDir = File(context.cacheDir, "test_rpa_output").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        // Clean up test output directory
        if (testOutputDir.exists()) {
            testOutputDir.deleteRecursively()
        }
    }

    @Test
    fun extractRpaArchive_withMultipleFiles_extractsSuccessfully() = runBlocking {
        // Arrange
        val rpaFile = File(testOutputDir, "test.rpa")
        TestArchiveHelper.createRpaArchive(rpaFile)

        val destinationDir = File(testOutputDir, "extracted")
        destinationDir.mkdirs()

        val progressUpdates = mutableListOf<Float>()

        // Act
        val result = extractor.extract(
            inputStream = rpaFile.inputStream(),
            destinationPath = destinationDir,
            archiveType = ArchiveType.RPA,
            sourceFileName = "test.rpa",
            onProgress = { progress ->
                if (progress is ExtractionProgress.Extracting) {
                    progressUpdates.add(progress.progress)
                }
            }
        )

        // Assert with detailed error message
        when (result) {
            is ExtractionResult.Success -> {
                // Continue with assertions
            }
            is ExtractionResult.Error -> {
                fail("Extraction failed: ${result.message}\nCause: ${result.cause?.message}")
            }
            else -> {
                fail("Unexpected result type: ${result::class.simpleName}")
            }
        }
        assertTrue("Extraction should succeed", result is ExtractionResult.Success)
        assertEquals(3, (result as ExtractionResult.Success).extractedFilesCount)

        // Verify extracted files
        val file1 = File(destinationDir, "testRpa/file.txt")
        assertTrue("testRpa/file.txt should exist", file1.exists())
        assertEquals("Rpa", file1.readText())

        val file2 = File(destinationDir, "testRpa/readme.md")
        assertTrue("testRpa/readme.md should exist", file2.exists())
        assertTrue(file2.readText().contains("# RPA Test"))

        val file3 = File(destinationDir, "testRpa/sub/data.bin")
        assertTrue("testRpa/sub/data.bin should exist", file3.exists())
        assertEquals("Binary content", file3.readText())

        // Verify progress callbacks
        assertTrue("Should have progress updates", progressUpdates.isNotEmpty())
        assertTrue("Final progress should be 100%", progressUpdates.last() == 1.0f)
    }

    @Test
    fun extractRpaArchive_withDeepStructure_createsNestedDirectories() = runBlocking {
        // Arrange
        val rpaFile = File(testOutputDir, "test.rpa")
        TestArchiveHelper.createRpaArchive(rpaFile)

        val destinationDir = File(testOutputDir, "extracted")
        destinationDir.mkdirs()

        // Act
        val result = extractor.extract(
            inputStream = rpaFile.inputStream(),
            destinationPath = destinationDir,
            archiveType = ArchiveType.RPA,
            sourceFileName = "test.rpa",
            onProgress = {}
        )

        // Assert with detailed error message
        when (result) {
            is ExtractionResult.Success -> {
                // Continue with assertions
            }
            is ExtractionResult.Error -> {
                fail("Extraction failed: ${result.message}\nCause: ${result.cause?.message}")
            }
            else -> {
                fail("Unexpected result type: ${result::class.simpleName}")
            }
        }
        assertTrue(result is ExtractionResult.Success)

        // Verify nested directory structure
        val subDir = File(destinationDir, "testRpa/sub")
        assertTrue("Nested subdirectory should exist", subDir.exists())
        assertTrue("Nested subdirectory should be a directory", subDir.isDirectory)

        val nestedFile = File(subDir, "data.bin")
        assertTrue("File in nested directory should exist", nestedFile.exists())
    }

    @Test
    fun supports_rpaType_returnsTrue() {
        // Act
        val supportsRpa = extractor.supports(ArchiveType.RPA)
        val supportsZip = extractor.supports(ArchiveType.ZIP)

        // Assert
        assertTrue("Should support RPA type", supportsRpa)
        assertTrue("Should not support ZIP type", !supportsZip)
    }
}
