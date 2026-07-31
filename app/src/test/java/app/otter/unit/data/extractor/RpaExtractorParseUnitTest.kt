package app.otter.data.extractor

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Step 3: Test RpaExtractor.parsePickleIndex() directly
 * to identify why 0 files are returned.
 */
class RpaExtractorParseUnitTest {

    @Test
    fun `parsePickleIndex should extract 3 file entries from RPA index`() = runBlocking {
        // Create RPA file
        val tempDir = File(System.getProperty("java.io.tmpdir"), "rpa-parse-test-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        val rpaFile = File(tempDir, "test.rpa")
        TestArchiveHelper.createRpaArchive(rpaFile)

        try {
            // Read RPA file
            val allBytes = rpaFile.readBytes()

            // Parse header
            val headerLine = String(allBytes.sliceArray(0 until 34), Charsets.US_ASCII)
            val parts = headerLine.trim().split(" ")
            val indexOffset = parts[1].toLong(16)
            val key = parts[2].toInt(16)

            // Test RpaExtractor.extract() with the created RPA file

            val extractor = RpaExtractor(
                pathValidator = app.otter.util.PathValidator(),
                tempFileManager = TempFileManager(),
                sevenZipHelper = SevenZipExtractorHelper(StandardProgressCalculator())
            )

            val destinationDir = File(tempDir, "extracted")
            destinationDir.mkdirs()

            val result = extractor.extract(
                inputStream = rpaFile.inputStream(),
                destinationPath = destinationDir,
                archiveType = app.otter.domain.model.ArchiveType.RPA,
                sourceFileName = "test.rpa",
                onProgress = { }
            )

            // Assertions
            assertTrue("Extraction should succeed", result is app.otter.domain.model.ExtractionResult.Success)
            val successResult = result as app.otter.domain.model.ExtractionResult.Success
            assertEquals("Should extract 3 files", 3, successResult.extractedFilesCount)

            // Verify files exist (use File constructor to handle path separators correctly)
            val file1 = File(File(destinationDir, "testRpa"), "file.txt")
            assertTrue("testRpa/file.txt should exist", file1.exists())
            assertEquals("Rpa", file1.readText())

            val file2 = File(File(destinationDir, "testRpa"), "readme.md")
            assertTrue("testRpa/readme.md should exist", file2.exists())
            assertTrue(file2.readText().contains("# RPA Test"))

            val file3 = File(File(File(destinationDir, "testRpa"), "sub"), "data.bin")
            assertTrue("testRpa/sub/data.bin should exist", file3.exists())
            assertEquals("Binary content", file3.readText())

        } finally {
            tempDir.deleteRecursively()
        }
    }
}
