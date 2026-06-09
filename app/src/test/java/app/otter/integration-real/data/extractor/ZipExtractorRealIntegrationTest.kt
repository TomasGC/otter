package app.otter.data.extractor

import app.otter.util.PathValidator
import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionResult
import app.otter.test.ArchiveTestHelper
import app.otter.test.ExtractionTestHelper
import app.otter.test.fakes.SimpleTempFileManager
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * REAL integration tests for ZipExtractor WITHOUT mocks.
 * Uses real file I/O and real implementations (SimpleTempFileManager).
 * These tests are slower but validate the complete extraction pipeline.
 * All common scenarios inherited from ZipExtractorIntegrationTestBase.
 */
class ZipExtractorRealIntegrationTest : ZipExtractorIntegrationTestBase() {

    private lateinit var tempFileManager: SimpleTempFileManager

    override fun createExtractor(): ZipExtractor {
        tempFileManager = SimpleTempFileManager()
        return ZipExtractor(
            pathValidator = PathValidator(),                                    // Real
            tempFileManager = tempFileManager,                                  // Real
            sevenZipHelper = mockk(relaxed = true)                              // Mock (external lib)
        )
    }

    override fun cleanupExtractor() {
        tempFileManager.cleanup()
    }

    // ===== Performance-specific tests (not in base class) =====

    @Test
    fun `extract 100 files - performance test`() = runTest {
        // Arrange - Create ZIP with 100 files
        val manyEntries = (1..100).associate { i ->
            "file_$i.txt" to "Content of file $i"
        }

        ArchiveTestHelper.createZipArchive(zipFile, manyEntries)

        // Act
        val startTime = System.currentTimeMillis()
        val result = ExtractionTestHelper.extractArchive(
            extractor = extractor,
            archiveFile = zipFile,
            outputDir = outputDir,
            archiveType = ArchiveType.ZIP
        )
        val duration = System.currentTimeMillis() - startTime

        // Assert
        assertTrue("Should extract all files", result is ExtractionResult.Success)

        val success = result as ExtractionResult.Success
        assertEquals("Should extract 100 files", 100, success.extractedFilesCount)

        val extractedFiles = ArchiveTestHelper.listAllFiles(outputDir)
        assertEquals("All files should be present", 100, extractedFiles.size)

        // Performance assertion - should complete in reasonable time
        assertTrue(
            "Extraction should complete in < 10 seconds (was ${duration}ms)",
            duration < 10000
        )
    }

    @Test
    fun `verify actual file content - deep I_O validation`() = runTest {
        // Arrange
        ArchiveTestHelper.createZipArchiveWithFolders(zipFile)

        // Act
        val result = ExtractionTestHelper.extractArchive(
            extractor = extractor,
            archiveFile = zipFile,
            outputDir = outputDir,
            archiveType = ArchiveType.ZIP
        )

        // Assert
        assertTrue("Extraction should succeed", result is ExtractionResult.Success)

        // Verify actual file content (real I/O validation)
        val nestedFile = File(outputDir, "folder1${File.separator}file1.txt")
        ArchiveTestHelper.assertFileContent(nestedFile, "File 1 in folder1")
    }
}
