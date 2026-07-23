package app.otter.domain.usecase.helpers

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import app.otter.data.extractor.*
import app.otter.domain.model.ArchiveType
import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ResourcePath
import app.otter.util.PathValidator
import org.junit.Assert.*
import java.io.File

/**
 * Helper for archive extraction and verification in instrumented tests.
 */
object ArchiveExtractionTestHelper {

    // Expected counts (from template: 100 root files + 6 folders + folder contents)
    const val EXPECTED_TOTAL_FILES = 4010
    const val EXPECTED_ROOT_FILES = 100
    const val EXPECTED_FOLDERS = 6

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    // System.currentTimeMillis() alone can collide when multiple output dirs are requested
    // within the same millisecond (e.g. several coroutines launched back-to-back), which let
    // one test's early cleanup delete a directory another concurrent extraction was still
    // writing into. The counter guarantees a unique name per call regardless of timing.
    private val outputDirCounter = java.util.concurrent.atomic.AtomicLong(0)

    // Create extractor dependencies
    private val pathValidator = PathValidator()
    private val tempFileManager = TempFileManager()
    private val sevenZipHelper = SevenZipExtractorHelper()
    private val archiveLibraryManager = ArchiveLibraryManager()

    // ========== Archive Type ==========

    fun getArchiveType(archiveName: String): ArchiveType {
        return when {
            archiveName.endsWith(".rpa") -> ArchiveType.RPA
            archiveName.endsWith(".zip") -> ArchiveType.ZIP
            archiveName.endsWith(".rar") -> ArchiveType.RAR
            archiveName.endsWith(".tar.gz") -> ArchiveType.TAR_GZ
            archiveName.endsWith(".tar") -> ArchiveType.TAR
            archiveName.endsWith(".7z") -> ArchiveType.SEVEN_ZIP
            else -> throw IllegalArgumentException("Unknown archive type: $archiveName")
        }
    }

    fun createOutputDir(): File {
        // Use external cache (SD card partition) to avoid filling internal data partition
        // with 264K-file extractions across multiple tests
        val baseDir = context.externalCacheDir ?: context.cacheDir
        val unique = "${System.currentTimeMillis()}_${outputDirCounter.incrementAndGet()}"
        return File(baseDir, "test_extraction_$unique").apply {
            mkdirs()
        }
    }

    // ========== Extract ==========

    suspend fun extractArchive(
        archivePath: String,
        archiveType: ArchiveType,
        outputDir: File,
        selectedItems: List<BrowsableItem> = emptyList()
    ): File {
        // Create appropriate extractor based on archive type
        val extractor = when (archiveType) {
            ArchiveType.RPA -> RpaExtractor(pathValidator, tempFileManager, sevenZipHelper)
            ArchiveType.ZIP -> ZipExtractor(pathValidator, tempFileManager, sevenZipHelper)
            ArchiveType.RAR -> RarExtractor(pathValidator, archiveLibraryManager, tempFileManager, sevenZipHelper)
            ArchiveType.TAR -> TarExtractor(pathValidator, tempFileManager, sevenZipHelper)
            ArchiveType.TAR_GZ -> TarExtractor(pathValidator, tempFileManager, sevenZipHelper)
            ArchiveType.GZIP -> GzipExtractor(tempFileManager, sevenZipHelper)
            ArchiveType.TAR_BZ2 -> TarExtractor(pathValidator, tempFileManager, sevenZipHelper)
            ArchiveType.SEVEN_ZIP -> SevenZipExtractor(pathValidator, archiveLibraryManager, tempFileManager, sevenZipHelper)
        }

        // Read archive file
        val archiveFile = File(archivePath)
        val inputStream = archiveFile.inputStream()

        // Convert BrowsableItem list to entry path strings for selective extraction
        val selectedPaths = if (selectedItems.isNotEmpty()) {
            selectedItems.map { item ->
                when (val p = item.path) {
                    is app.otter.domain.model.ResourcePath.ArchiveEntry -> p.entryPath
                    is app.otter.domain.model.ResourcePath.FileSystem -> p.path
                }
            }
        } else {
            null
        }

        // Extract
        val result = extractor.extract(
            inputStream = inputStream,
            destinationPath = outputDir,
            archiveType = archiveType,
            sourceFileName = archiveFile.name,
            selectedItems = selectedPaths,
            onProgress = { _: ExtractionProgress -> /* no-op */ }
        )

        // Close stream
        inputStream.close()

        // Verify success
        assertTrue("Extraction failed: $result", result is app.otter.domain.model.ExtractionResult.Success)

        return outputDir
    }

    // ========== File & Folder Counting ==========

    fun countFilesRecursively(directory: File): Int {
        return directory.walkTopDown()
            .filter { it.isFile }
            .count()
    }

    fun countDirectoriesRecursively(directory: File, excludeRoot: Boolean = true): Int {
        val dirs = directory.walkTopDown()
            .filter { it.isDirectory }
            .toList()

        return if (excludeRoot) dirs.size - 1 else dirs.size
    }

    fun getFileNamesRecursively(directory: File): Set<String> {
        return directory.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(directory).path.replace("\\", "/") }
            .toSet()
    }

    fun getFolderNamesRecursively(directory: File): Set<String> {
        return directory.walkTopDown()
            .filter { it.isDirectory && it != directory }
            .map { it.relativeTo(directory).path.replace("\\", "/") }
            .toSet()
    }

    // ========== Assertions ==========

    fun assertExtractedFilesMatch(
        extractedDir: File,
        expectedFileNames: Set<String>,
        expectedFolderNames: Set<String>
    ) {
        val actualFileNames = getFileNamesRecursively(extractedDir)
        val actualFolderNames = getFolderNamesRecursively(extractedDir)

        // Verify file counts
        assertEquals(
            "File count mismatch",
            expectedFileNames.size,
            actualFileNames.size
        )

        // Verify folder counts
        assertEquals(
            "Folder count mismatch",
            expectedFolderNames.size,
            actualFolderNames.size
        )

        // Verify file names
        expectedFileNames.forEach { expectedName ->
            assertTrue(
                "Missing file: $expectedName",
                actualFileNames.contains(expectedName)
            )
        }

        // Verify folder names
        expectedFolderNames.forEach { expectedName ->
            assertTrue(
                "Missing folder: $expectedName",
                actualFolderNames.contains(expectedName)
            )
        }
    }

    fun assertTotalExtractedFilesAndFolders(
        extractedDir: File,
        expectedFiles: Int = EXPECTED_TOTAL_FILES,
        expectedFolders: Int = EXPECTED_FOLDERS
    ) {
        val fileCount = countFilesRecursively(extractedDir)
        val folderCount = countDirectoriesRecursively(extractedDir)

        assertEquals("Expected $expectedFiles files", expectedFiles, fileCount)
        assertEquals("Expected $expectedFolders folders", expectedFolders, folderCount)
    }
}
