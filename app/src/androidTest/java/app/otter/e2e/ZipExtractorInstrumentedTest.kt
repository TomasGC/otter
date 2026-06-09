package app.otter.data.extractor

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * Instrumented test for ZipExtractor with real device/emulator.
 * Tests ZIP extraction with Android's native ZipFile.
 * Uses Hilt for dependency injection.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ZipExtractorInstrumentedTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val tempFolder = TemporaryFolder()

    @Inject
    lateinit var extractor: ZipExtractor

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun testSupportsZipType() {
        assertTrue(extractor.supports(ArchiveType.ZIP))
    }

    @Test
    fun testExtractRealZipFile() = runTest {
        // Create test archive programmatically
        val testZipFile = tempFolder.newFile("test.zip")
        TestArchiveHelper.createZipFile(testZipFile)

        val destination = tempFolder.newFolder("output-zip")

        val result = extractor.extract(
            inputStream = testZipFile.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test.zip",
            selectedItems = null,
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Success)

        val extractedCount = (result as ExtractionResult.Success).extractedFilesCount
        assertEquals("Expected exactly 1 file", 1, extractedCount)

        // Verify extracted files
        val extractedFiles = destination.walk().filter { it.isFile }.toList()
        assertEquals("Expected exactly 1 file", 1, extractedFiles.size)

        // Verify content (test.zip contains testZip/file.txt → "Zip")
        val file = extractedFiles.first()
        assertTrue("Expected path contains 'testZip'", file.path.contains("testZip"))
        assertEquals("Expected filename 'file.txt'", "file.txt", file.name)

        val content = file.readText().trim()
        assertEquals("Expected content 'Zip', got '$content'", "Zip", content)
    }

    @Test
    fun testExtractMultiFileZip() = runTest {
        val testZipFile = tempFolder.newFile("test-multi.zip")
        TestArchiveHelper.createMultiFileZip(testZipFile)

        val destination = tempFolder.newFolder("output-multi")

        val result = extractor.extract(
            inputStream = testZipFile.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test-multi.zip",
            selectedItems = null,
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Success)

        val extractedCount = (result as ExtractionResult.Success).extractedFilesCount
        assertEquals("Expected 4 files", 4, extractedCount)

        val extractedFiles = destination.walk().filter { it.isFile }.toList()
        assertEquals("Expected 4 files", 4, extractedFiles.size)

        // Verify all files exist
        val fileNames = extractedFiles.map { it.name }.sorted()
        assertTrue("Missing file1.txt", fileNames.contains("file1.txt"))
        assertTrue("Missing file2.txt", fileNames.contains("file2.txt"))
        assertTrue("Missing file3.txt", fileNames.contains("file3.txt"))
        assertTrue("Missing data.bin", fileNames.contains("data.bin"))
    }

    @Test
    fun testExtractZipWithProgress() = runTest {
        val testZipFile = tempFolder.newFile("test-progress.zip")
        TestArchiveHelper.createMultiFileZip(testZipFile)

        val destination = tempFolder.newFolder("output-progress")
        val progressValues = mutableListOf<Int>()

        val result = extractor.extract(
            inputStream = testZipFile.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test-progress.zip",
            selectedItems = null,
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

    @Test
    fun testExtractDeepStructureZip() = runTest {
        val testZipFile = tempFolder.newFile("test-deep.zip")
        TestArchiveHelper.createDeepStructureZip(testZipFile)

        val destination = tempFolder.newFolder("output-deep")

        val result = extractor.extract(
            inputStream = testZipFile.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test-deep.zip",
            selectedItems = null,
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Success)

        val extractedCount = (result as ExtractionResult.Success).extractedFilesCount
        assertEquals("Expected 3 files", 3, extractedCount)

        // Verify deep file exists
        val deepFile = destination.walk()
            .filter { it.isFile && it.name == "deep.txt" }
            .firstOrNull()

        assertTrue("Deep file should exist", deepFile != null)
        assertTrue("Deep file should be in nested structure",
            deepFile!!.path.contains("level5"))
    }

    @Test
    fun testExtractCorruptedZip() = runTest {
        val testZipFile = tempFolder.newFile("test-corrupted.zip")
        TestArchiveHelper.createCorruptedZip(testZipFile)

        val destination = tempFolder.newFolder("output-corrupted")

        val result = extractor.extract(
            inputStream = testZipFile.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test-corrupted.zip",
            selectedItems = null,
            onProgress = {}
        )

        assertTrue("Corrupted ZIP should return Failure",
            result is ExtractionResult.Failure)
    }

    @Test
    fun testExtractMaliciousZipWithPathTraversal() = runTest {
        val testZipFile = tempFolder.newFile("test-malicious.zip")
        TestArchiveHelper.createMaliciousZipWithPathTraversal(testZipFile)

        val destination = tempFolder.newFolder("output-malicious")

        val result = extractor.extract(
            inputStream = testZipFile.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test-malicious.zip",
            selectedItems = null,
            onProgress = {}
        )

        // Should either fail or skip malicious entries
        // Verify no files created outside destination
        val allFiles = destination.parentFile?.walk()
            ?.filter { it.isFile && !it.path.startsWith(destination.path) }
            ?.toList() ?: emptyList()

        assertTrue("No files should be created outside destination",
            allFiles.isEmpty() || allFiles.none { it.name == "malicious.txt" })
    }

    @Test
    fun testExtractZipWithSpecialChars() = runTest {
        val testZipFile = tempFolder.newFile("test-special.zip")
        TestArchiveHelper.createZipWithSpecialChars(testZipFile)

        val destination = tempFolder.newFolder("output-special")

        val result = extractor.extract(
            inputStream = testZipFile.inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.ZIP,
            sourceFileName = "test-special.zip",
            selectedItems = null,
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Success)

        val extractedCount = (result as ExtractionResult.Success).extractedFilesCount
        assertEquals("Expected 5 files", 5, extractedCount)

        val fileNames = destination.walk().filter { it.isFile }.map { it.name }.toList()
        assertTrue("Should handle spaces", fileNames.any { it.contains("spaces") })
        assertTrue("Should handle dashes", fileNames.any { it.contains("dashes") })
        assertTrue("Should handle underscores", fileNames.any { it.contains("underscores") })
    }
}
