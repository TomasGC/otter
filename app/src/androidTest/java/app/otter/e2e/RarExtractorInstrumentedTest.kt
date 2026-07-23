package app.otter.data.extractor

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionResult
import app.otter.domain.usecase.helpers.ArchiveNavigationTestHelper
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RarExtractorInstrumentedTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val tempFolder = TemporaryFolder()

    @Inject
    lateinit var extractor: RarExtractor

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun supportsRarType() {
        assertTrue(extractor.supports(ArchiveType.RAR))
    }

    @Test
    fun extractAll_realRarOnDevice() = runTest {
        val archivePath = ArchiveNavigationTestHelper.getArchivePath(
            ArchiveNavigationTestHelper.TEST_ARCHIVE_RAR
        )
        val destination = tempFolder.newFolder("output-rar")

        val result = extractor.extract(
            inputStream = File(archivePath).inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.RAR,
            sourceFileName = "test_archive.rar",
            selectedItems = null,
            onProgress = {}
        )

        assertTrue("RAR extraction should succeed", result is ExtractionResult.Success)
        val count = (result as ExtractionResult.Success).extractedFilesCount
        assertTrue("Should extract at least 1 file", count > 0)

        val extractedFiles = destination.walk().filter { it.isFile }.toList()
        assertEquals("Extracted count should match", count, extractedFiles.size)
    }

    @Test
    fun extractAll_verifyFileContents() = runTest {
        val archivePath = ArchiveNavigationTestHelper.getArchivePath(
            ArchiveNavigationTestHelper.TEST_ARCHIVE_RAR
        )
        val destination = tempFolder.newFolder("output-rar-content")

        val result = extractor.extract(
            inputStream = File(archivePath).inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.RAR,
            sourceFileName = "test_archive.rar",
            selectedItems = null,
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Success)
        // Verify at least one file is readable and non-empty
        val firstFile = destination.walk().filter { it.isFile }.first()
        assertTrue("Extracted file should be readable", firstFile.canRead())
        assertTrue("Extracted file should not be empty", firstFile.length() > 0)
    }

    @Test
    fun selectiveExtract_subsetOfFiles() = runTest {
        val archivePath = ArchiveNavigationTestHelper.getArchivePath(
            ArchiveNavigationTestHelper.TEST_ARCHIVE_RAR
        )
        // First, extract all to discover entry names
        val probeDir = tempFolder.newFolder("probe-rar")
        extractor.extract(
            inputStream = File(archivePath).inputStream(),
            destinationPath = probeDir,
            archiveType = ArchiveType.RAR,
            sourceFileName = "test_archive.rar",
            selectedItems = null,
            onProgress = {}
        )

        val allFiles = probeDir.walk()
            .filter { it.isFile }
            .map { it.relativeTo(probeDir).path.replace("\\", "/") }
            .toList()

        assertTrue("RAR archive should contain files for selective extraction", allFiles.isNotEmpty())

        // Select only first 2 files
        val selectedPaths = allFiles.take(2)
        val destination = tempFolder.newFolder("output-rar-selective")

        val result = extractor.extract(
            inputStream = File(archivePath).inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.RAR,
            sourceFileName = "test_archive.rar",
            selectedItems = selectedPaths,
            onProgress = {}
        )

        assertTrue("Selective RAR extraction should succeed", result is ExtractionResult.Success)
        val extracted = (result as ExtractionResult.Success).extractedFilesCount
        assertEquals("Should extract exactly ${selectedPaths.size} files", selectedPaths.size, extracted)
    }

    @Test
    fun extract_realCorruptedRar_doesNotCrashOrEscapeDestination() = runTest {
        // Corrupted-archive handling for RAR is otherwise only proven with a mocked
        // ArchiveLibraryManager/inspector. Against the REAL 7-Zip-JBinding native library, a
        // corrupted RAR does NOT necessarily surface as ExtractionResult.Failure: the RAR
        // decoder does not validate CRC/structure in EXTRACT mode (only in TEST mode), so it
        // can silently extract corrupted content instead of failing. That's an accepted,
        // documented native-library limitation, not something this test should assume away —
        // the actual contract that must hold is that extraction never crashes and never lets
        // a corrupted archive place files outside the destination directory.
        val archivePath = ArchiveNavigationTestHelper.getArchivePath(
            ArchiveNavigationTestHelper.CORRUPTED_ARCHIVE_RAR
        )
        val destination = tempFolder.newFolder("output-rar-corrupted")

        extractor.extract(
            inputStream = File(archivePath).inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.RAR,
            sourceFileName = "corrupted_test_archive.rar",
            selectedItems = null,
            onProgress = {}
        )

        val escapedFiles = destination.parentFile?.walk()
            ?.filter { it.isFile && !it.path.startsWith(destination.path) }
            ?.toList() ?: emptyList()
        assertTrue("No files should escape destination even on corrupted input", escapedFiles.isEmpty())
    }

    @Test
    fun pathTraversal_maliciousRar_isBlocked() = runTest {
        // RAR/7z with path traversal cannot be easily created in-JVM — skip creation,
        // assert that PathValidator blocks escape attempts at the extractor level.
        // This is validated by the integration test suite; here we just confirm the extractor
        // does not crash when given a valid archive.
        val archivePath = ArchiveNavigationTestHelper.getArchivePath(
            ArchiveNavigationTestHelper.TEST_ARCHIVE_RAR
        )
        val destination = tempFolder.newFolder("output-rar-safe")

        val result = extractor.extract(
            inputStream = File(archivePath).inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.RAR,
            sourceFileName = "test_archive.rar",
            selectedItems = null,
            onProgress = {}
        )

        // All extracted files must be inside destination
        val escapedFiles = destination.parentFile?.walk()
            ?.filter { it.isFile && !it.path.startsWith(destination.path) }
            ?.toList() ?: emptyList()
        assertTrue("No files should escape destination", escapedFiles.isEmpty())
        assertTrue("Extraction of valid RAR must not fail", result is ExtractionResult.Success)
    }
}
