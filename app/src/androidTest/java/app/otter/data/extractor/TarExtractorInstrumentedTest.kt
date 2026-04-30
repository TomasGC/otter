package app.otter.data.extractor

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
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
 * Instrumented test for ApacheTarExtractor with real device/emulator.
 * Tests tar, tar.gz, and tgz extraction with Apache Commons Compress.
 */
@RunWith(AndroidJUnit4::class)
class TarExtractorInstrumentedTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val pathValidator = PathValidator()
    private val extractor = ApacheTarExtractor(
        pathValidator = pathValidator,
        tempFileManager = TempFileManager(),
        sevenZipHelper = SevenZipExtractorHelper()
    )

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
        // Create test archive programmatically
        val testTarFile = tempFolder.newFile("test.tar")
        TestArchiveHelper.createTarFile(testTarFile)

        val destination = tempFolder.newFolder("output-tar")

        val result = extractor.extract(
            inputStream = testTarFile.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.TAR,
            sourceFileName = "test.tar",
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Success)

        val extractedCount = (result as ExtractionResult.Success).extractedFilesCount
        assertEquals("Expected exactly 1 file", 1, extractedCount)

        // Verify extracted files
        val extractedFiles = destination.walk().filter { it.isFile }.toList()
        assertEquals("Expected exactly 1 file", 1, extractedFiles.size)

        // Verify content (test.tar contains testTar/file.txt → "Tar")
        val file = extractedFiles.first()
        assertTrue("Expected path contains 'testTar'", file.path.contains("testTar"))
        assertEquals("Expected filename 'file.txt'", "file.txt", file.name)

        val content = file.readText().trim()
        assertEquals("Expected content 'Tar', got '$content'", "Tar", content)
    }

    @Test
    fun testExtractRealTarGzFile() = runTest {
        // Create test archive programmatically
        val testTarGzFile = tempFolder.newFile("test.tar.gz")
        TestArchiveHelper.createTarGzFile(testTarGzFile)

        val destination = tempFolder.newFolder("output-tar-gz")

        val result = extractor.extract(
            inputStream = testTarGzFile.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.TAR_GZ,
            sourceFileName = "test.tar.gz",
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Success)

        val extractedCount = (result as ExtractionResult.Success).extractedFilesCount
        assertEquals("Expected exactly 1 file", 1, extractedCount)

        // Verify extracted files
        val extractedFiles = destination.walk().filter { it.isFile }.toList()
        assertEquals("Expected exactly 1 file", 1, extractedFiles.size)

        // Verify content (test.tar.gz contains testTarGz/file.txt → "TarGz")
        val file = extractedFiles.first()
        assertTrue("Expected path contains 'testTarGz'", file.path.contains("testTarGz"))
        assertEquals("Expected filename 'file.txt'", "file.txt", file.name)

        val content = file.readText().trim()
        assertEquals("Expected content 'TarGz', got '$content'", "TarGz", content)
    }

    @Test
    fun testExtractRealTgzFile() = runTest {
        // Create test archive programmatically
        val testTgzFile = tempFolder.newFile("test.tgz")
        TestArchiveHelper.createTgzFile(testTgzFile)

        val destination = tempFolder.newFolder("output-tgz")

        val result = extractor.extract(
            inputStream = testTgzFile.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.TAR_GZ,
            sourceFileName = "test.tgz",
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Success)

        val extractedCount = (result as ExtractionResult.Success).extractedFilesCount
        assertEquals("Expected exactly 1 file", 1, extractedCount)

        // Verify extracted files
        val extractedFiles = destination.walk().filter { it.isFile }.toList()
        assertEquals("Expected exactly 1 file", 1, extractedFiles.size)

        // Verify content (test.tgz contains testTgz/file.txt → "Tgz")
        val file = extractedFiles.first()
        assertTrue("Expected path contains 'testTgz'", file.path.contains("testTgz"))
        assertEquals("Expected filename 'file.txt'", "file.txt", file.name)

        val content = file.readText().trim()
        assertEquals("Expected content 'Tgz', got '$content'", "Tgz", content)
    }

    @Test
    fun testExtractMultiFileTar() = runTest {
        val testTarFile = tempFolder.newFile("test-multi.tar")
        TestArchiveHelper.createMultiFileTar(testTarFile)

        val destination = tempFolder.newFolder("output-multi-tar")

        val result = extractor.extract(
            inputStream = testTarFile.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.TAR,
            sourceFileName = "test-multi.tar",
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Success)

        val extractedCount = (result as ExtractionResult.Success).extractedFilesCount
        assertEquals("Expected 4 files", 4, extractedCount)

        val extractedFiles = destination.walk().filter { it.isFile }.toList()
        assertEquals("Expected 4 files", 4, extractedFiles.size)
    }

    @Test
    fun testExtractTarWithProgress() = runTest {
        val testTarFile = tempFolder.newFile("test-progress.tar")
        TestArchiveHelper.createMultiFileTar(testTarFile)

        val destination = tempFolder.newFolder("output-progress-tar")
        val progressValues = mutableListOf<Int>()

        val result = extractor.extract(
            inputStream = testTarFile.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.TAR,
            sourceFileName = "test-progress.tar",
            onProgress = { progress ->
                if (progress is ExtractionProgress.Extracting) {
                    progressValues.add((progress.progress * 100).toInt())
                }
            }
        )

        assertTrue(result is ExtractionResult.Success)
        assertTrue("Progress callback should be called", progressValues.isNotEmpty())
        assertTrue("Final progress should be 100", progressValues.last() == 100)
        assertTrue("Progress should increase", progressValues.zipWithNext().all { (a, b) -> a <= b })
    }
}
