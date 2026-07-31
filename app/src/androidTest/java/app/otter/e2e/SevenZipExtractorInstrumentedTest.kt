package app.otter.data.extractor

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otter.data.extractor.ExtractionOptions
import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionResult
import app.otter.domain.usecase.helpers.ArchiveNavigationTestHelper
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SevenZipExtractorInstrumentedTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val tempFolder = TemporaryFolder()

    @Inject
    lateinit var extractor: SevenZipExtractor

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun supportsSevenZipType() {
        assertTrue(extractor.supports(ArchiveType.SEVEN_ZIP))
    }

    @Test
    fun extractAll_realSevenZipOnDevice() = runTest {
        val archivePath = ArchiveNavigationTestHelper.getArchivePath(
            ArchiveNavigationTestHelper.TEST_ARCHIVE_7Z
        )
        val destination = tempFolder.newFolder("output-7z")

        val result = extractor.extract(
            inputStream = File(archivePath).inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.SEVEN_ZIP,
            sourceFileName = "test_archive.7z",
            options = ExtractionOptions(),
            onProgress = {}
        )

        assertTrue("7z extraction should succeed", result is ExtractionResult.Success)
        val count = (result as ExtractionResult.Success).extractedFilesCount
        assertTrue("Should extract at least 1 file", count > 0)
    }

    @Test
    fun extract_realCorrupted7z_failsGracefullyViaRealNativeLibrary() = runTest {
        // Corrupted-archive handling for 7z is otherwise only proven with a mocked
        // ArchiveLibraryManager/inspector — this exercises the REAL 7-Zip-JBinding native
        // library against a genuinely corrupted file to confirm it actually fails as expected,
        // rather than assuming the mock's simulated exception matches real library behavior.
        val archivePath = ArchiveNavigationTestHelper.getArchivePath(
            ArchiveNavigationTestHelper.CORRUPTED_ARCHIVE_7Z
        )
        val destination = tempFolder.newFolder("output-7z-corrupted")

        val result = extractor.extract(
            inputStream = File(archivePath).inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.SEVEN_ZIP,
            sourceFileName = "corrupted_test_archive.7z",
            options = ExtractionOptions(),
            onProgress = {}
        )

        assertTrue("Corrupted 7z must fail gracefully, not crash", result is ExtractionResult.Failure)
    }

    @Test
    fun extractAll_verifyFileCount_matchesExpected() = runTest {
        val archivePath = ArchiveNavigationTestHelper.getArchivePath(
            ArchiveNavigationTestHelper.TEST_ARCHIVE_7Z
        )
        val destination = tempFolder.newFolder("output-7z-count")

        val result = extractor.extract(
            inputStream = File(archivePath).inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.SEVEN_ZIP,
            sourceFileName = "test_archive.7z",
            options = ExtractionOptions(),
            onProgress = {}
        )

        assertTrue(result is ExtractionResult.Success)
        val reportedCount = (result as ExtractionResult.Success).extractedFilesCount
        val actualCount = destination.walk().filter { it.isFile }.count()
        assertEquals("Reported count should match actual file count", reportedCount, actualCount)
    }

    @Test
    fun selectiveExtract_subsetOfFiles() = runTest {
        val archivePath = ArchiveNavigationTestHelper.getArchivePath(
            ArchiveNavigationTestHelper.TEST_ARCHIVE_7Z
        )
        val probeDir = tempFolder.newFolder("probe-7z")
        extractor.extract(
            inputStream = File(archivePath).inputStream(),
            destinationPath = probeDir,
            archiveType = ArchiveType.SEVEN_ZIP,
            sourceFileName = "test_archive.7z",
            options = ExtractionOptions(),
            onProgress = {}
        )

        val allFiles = probeDir.walk()
            .filter { it.isFile }
            .map { it.relativeTo(probeDir).path.replace("\\", "/") }
            .toList()

        assertTrue("7z archive should contain files", allFiles.isNotEmpty())
        val selectedPaths = allFiles.take(2)
        val destination = tempFolder.newFolder("output-7z-selective")

        val result = extractor.extract(
            inputStream = File(archivePath).inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.SEVEN_ZIP,
            sourceFileName = "test_archive.7z",
            options = ExtractionOptions(selectedItems = selectedPaths),
            onProgress = {}
        )

        assertTrue("Selective 7z extraction should succeed", result is ExtractionResult.Success)
        val extracted = (result as ExtractionResult.Success).extractedFilesCount
        assertEquals("Should extract exactly ${selectedPaths.size} files", selectedPaths.size, extracted)
    }

    @Test
    fun pathTraversal_validArchive_noFilesEscape() = runTest {
        val archivePath = ArchiveNavigationTestHelper.getArchivePath(
            ArchiveNavigationTestHelper.TEST_ARCHIVE_7Z
        )
        val destination = tempFolder.newFolder("output-7z-safe")

        val result = extractor.extract(
            inputStream = File(archivePath).inputStream(),
            destinationPath = destination,
            archiveType = ArchiveType.SEVEN_ZIP,
            sourceFileName = "test_archive.7z",
            options = ExtractionOptions(),
            onProgress = {}
        )

        val escapedFiles = destination.parentFile?.walk()
            ?.filter { it.isFile && !it.path.startsWith(destination.path) }
            ?.toList() ?: emptyList()
        assertTrue("No files should escape destination", escapedFiles.isEmpty())
        assertTrue("Extraction of valid 7z must not fail", result is ExtractionResult.Success)
    }

    @Test
    fun extractMultiVolume_realSevenZipOnDevice() = runTest {
        val firstVolume = ArchiveNavigationTestHelper.getSplitArchiveFirstVolumeOrNull(
            ArchiveNavigationTestHelper.SPLIT_7Z_FIRST_VOLUME
        )
        assumeTrue("split_7z multi-volume archive not on device", firstVolume != null)

        val result = extractor.extract(
            inputStream = firstVolume!!.inputStream(),
            destinationPath = tempFolder.newFolder("output-7z-multi"),
            archiveType = ArchiveType.SEVEN_ZIP,
            sourceFileName = firstVolume.name,
            options = ExtractionOptions(sourceFile = firstVolume),
            onProgress = {}
        )

        assertTrue("7z multi-volume extraction must succeed", result is ExtractionResult.Success)
        assertTrue("Must extract at least 1 file", (result as ExtractionResult.Success).extractedFilesCount > 0)
    }
}
