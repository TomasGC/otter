package app.otter.data.extractor

import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipExtractorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val extractor = ZipExtractor()

    @Test
    fun `should support ZIP type`() {
        assertTrue(extractor.supports(ArchiveType.ZIP))
    }

    @Test
    fun `should extract simple ZIP file`() = runTest {
        val zipBytes = createTestZip(mapOf(
            "file1.txt" to "content1",
            "file2.txt" to "content2"
        ))
        val destination = tempFolder.newFolder("output")

        val result = extractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Success)
        assertEquals(2, (result as ExtractionResult.Success).extractedFilesCount)
        assertTrue(File(destination, "file1.txt").exists())
        assertTrue(File(destination, "file2.txt").exists())
        assertEquals("content1", File(destination, "file1.txt").readText())
    }

    @Test
    fun `should block path traversal attack`() = runTest {
        val zipBytes = createTestZip(mapOf(
            "../../../etc/passwd" to "hacked"
        ))
        val destination = tempFolder.newFolder("output")

        val result = extractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Failure)
        assertTrue((result as ExtractionResult.Failure).errorMessage.contains("failed"))
    }

    @Test
    fun `should extract ZIP with subdirectories`() = runTest {
        val zipBytes = createTestZip(mapOf(
            "folder1/file1.txt" to "content1",
            "folder1/file2.txt" to "content2",
            "folder2/subfolder/file3.txt" to "content3"
        ))
        val destination = tempFolder.newFolder("output")

        val result = extractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Success)
        assertEquals(3, (result as ExtractionResult.Success).extractedFilesCount)
        assertTrue(File(destination, "folder1/file1.txt").exists())
        assertTrue(File(destination, "folder2/subfolder/file3.txt").exists())
        assertEquals("content3", File(destination, "folder2/subfolder/file3.txt").readText())
    }

    @Test
    fun `should return success for empty ZIP`() = runTest {
        val zipBytes = createTestZip(emptyMap())
        val destination = tempFolder.newFolder("output")

        val result = extractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Success)
        assertEquals(0, (result as ExtractionResult.Success).extractedFilesCount)
    }

    @Test
    fun `should handle files with special characters in names`() = runTest {
        val zipBytes = createTestZip(mapOf(
            "file with spaces.txt" to "content1",
            "file-with-dashes.txt" to "content2",
            "file_with_underscores.txt" to "content3"
        ))
        val destination = tempFolder.newFolder("output")

        val result = extractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Success)
        assertEquals(3, (result as ExtractionResult.Success).extractedFilesCount)
        assertTrue(File(destination, "file with spaces.txt").exists())
        assertTrue(File(destination, "file-with-dashes.txt").exists())
    }

    @Test
    fun `should call onProgress during extraction`() = runTest {
        val zipBytes = createTestZip(mapOf(
            "file1.txt" to "content1",
            "file2.txt" to "content2",
            "file3.txt" to "content3"
        ))
        val destination = tempFolder.newFolder("output")
        val progressEvents = mutableListOf<ExtractionProgress>()

        extractor.extract(
            inputStream = zipBytes.inputStream(),
            destinationPath = destination,
            onProgress = { progressEvents.add(it) }
        )

        assertEquals(3, progressEvents.size)
        progressEvents.forEachIndexed { index, progress ->
            assertTrue(progress is ExtractionProgress.Extracting)
            val extracting = progress as ExtractionProgress.Extracting
            assertEquals(index + 1, extracting.extractedCount)
            assertEquals(3, extracting.totalCount)
        }
    }

    @Test
    fun `should return success with zero files for corrupted ZIP`() = runTest {
        val corruptedBytes = "not a zip file".toByteArray()
        val destination = tempFolder.newFolder("output")

        val result = extractor.extract(
            inputStream = corruptedBytes.inputStream(),
            destinationPath = destination,
            onProgress = {}
        )

        // ZipInputStream doesn't throw exception for corrupted files,
        // it just returns null for nextEntry(), resulting in 0 extracted files
        assertTrue(result is ExtractionResult.Success)
        assertEquals(0, (result as ExtractionResult.Success).extractedFilesCount)
    }

    private fun createTestZip(files: Map<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
