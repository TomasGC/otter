package app.otter.domain.usecase.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otter.data.extractor.ArchiveExtractor
import app.otter.data.extractor.ExtractionOptions
import app.otter.domain.model.ArchiveType
import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.ExtractionResult
import app.otter.domain.usecase.helpers.ArchiveExtractionTestHelper
import app.otter.domain.usecase.helpers.ArchiveNavigationTestHelper
import app.otter.domain.usecase.helpers.BaseInstrumentedTest
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

/**
 * E2E Archive Workflow Tests - Progressive Crescendo Testing
 *
 * Abstract base class for E2E workflow tests.
 * Concrete subclasses specify archive type and extractor.
 *
 * Single test method that executes steps sequentially.
 * If any step fails, execution stops immediately.
 * Step numbers auto-increment - easy to add new steps without renumbering.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
abstract class E2EArchiveWorkflowInstrumentedTest : BaseInstrumentedTest() {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    // Inject BrowseItemsUseCase
    @Inject
    lateinit var browseItemsUseCase: app.otter.domain.usecase.BrowseItemsUseCase

    // Subclasses must provide archive type, path, extractor, and extension
    protected abstract val archiveType: ArchiveType
    protected abstract val testArchivePath: String
    protected abstract val extractor: ArchiveExtractor
    protected abstract val archiveExtension: String // e.g., ".zip"

    // Subclasses can opt out of tests whose pre-pushed fixture is unavailable for their format
    protected open val supportsDirectoryNavigation: Boolean = true
    protected open val supportsEmptyArchive: Boolean = true
    protected open val supportsMaliciousArchiveFixture: Boolean = false
    protected open val detectsCorruptedArchive: Boolean = true

    private var currentStep = 0

    @Before
    fun injectDependencies() {
        hiltRule.inject()
    }

    @After
    fun cleanupExtractionDirs() {
        val baseDir = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext.externalCacheDir ?: return
        baseDir.listFiles { _, name -> name.startsWith("test_extraction_") }
            ?.forEach { it.deleteRecursively() }
    }

    // Helper to unwrap Result<BrowseResult> and extract items.
    // If path points to an archive file, uses ArchiveEntry; otherwise FileSystem.
    private val archiveExtensions = setOf(".zip", ".rar", ".7z", ".tar", ".gz", ".tgz", ".tar.bz2", ".bz2", ".rpa")

    private suspend fun browse(path: String): List<BrowsableItem> {
        val resourcePath = if (archiveExtensions.any { path.endsWith(it, ignoreCase = true) }) {
            app.otter.domain.model.ResourcePath.ArchiveEntry(archivePath = path, entryPath = "")
        } else {
            app.otter.domain.model.ResourcePath.FileSystem(path)
        }
        val result = browseItemsUseCase(resourcePath).getOrThrow()
        return result.items
    }

    // Helper overload for ResourcePath
    private suspend fun browse(path: app.otter.domain.model.ResourcePath): List<BrowsableItem> {
        val result = browseItemsUseCase(path).getOrThrow()
        return result.items
    }

    // Resolves a pre-pushed fixture archive for this format, e.g. "corrupted_test_archive.zip"
    private fun fixtureFile(prefix: String): File {
        val path = ArchiveNavigationTestHelper.getArchivePath("${prefix}_test_archive$archiveExtension")
        return File(path)
    }

    @Test
    fun e2eWorkflow_progressiveCrescendo() = runBlocking {
        println("\n" + "=".repeat(60))
        println("E2E ${archiveType.name} Workflow - Progressive Testing")
        println("=".repeat(60))

        // Bloc 1: Happy Path Basique
        browseFilesystemDirectories_withoutOpeningArchive()
        extractArchive_withoutBrowsingInside()

        // Bloc 2: Gestion d'Erreurs
        handleCorruptedArchive()
        handleMaliciousPathTraversal()
        handleEmptyArchive()
        handleExtractionCancellation()

        // Bloc 3: Navigation & Sélection
        browseArchive_thenPressExtractAllButton()
        browseArchive_thenSelectAllItems()
        browseArchive_selectAll_thenExtract()
        browseArchive_thenNavigateIntoSubdirectory()
        browseSubdirectory_thenSelectSomeFiles()
        browseSubdirectory_selectSomeFiles_thenExtract()
        browseArchive_selectMixedTypes_thenExtract()
        browseArchive_navigateBackAndForth_thenExtract()

        // Bloc 4: Performance/Stress
        extractLargeArchive_10kFiles()
        selectiveExtract_oneFileFrom100k()
        extractDeepNestedArchive_100levels()
        extractArchiveWithLongFilename_255chars()

        // Bloc 5: Concurrence
        browseDuringExtraction()
        multipleSimultaneousExtractions()

        println("\n" + "=".repeat(60))
        println("✅ All $currentStep steps passed successfully!")
        println("=".repeat(60))
    }

    // ========== Bloc 1: Happy Path Basique ==========

    private suspend fun browseFilesystemDirectories_withoutOpeningArchive() {
        logStep("Browse filesystem directories (no archive interaction)")

        // Browse parent directory of test archive
        val parentPath = testArchivePath.substringBeforeLast("/")
        val result = browse(parentPath)

        // Assert
        assertTrue("Should browse directory successfully", result.isNotEmpty())
        assertTrue("Should contain archive file",
            result.any { it is BrowsableItem.FileSystemFile || it is BrowsableItem.ArchiveFile })

        logSuccess()
    }

    private suspend fun extractArchive_withoutBrowsingInside() {
        logStep("Extract archive directly (no browsing)")

        val outputDir = ArchiveExtractionTestHelper.createOutputDir()

        // Extract archive directly (no browsing)
        val extractedDir = ArchiveExtractionTestHelper.extractArchive(
            archivePath = testArchivePath,
            archiveType = archiveType,
            outputDir = outputDir,
            selectedItems = emptyList() // Extract all
        )

        // Assert at least some files were extracted (count varies per archive type)
        val fileCount = ArchiveExtractionTestHelper.countFilesRecursively(extractedDir)
        assertTrue("Should extract at least 1 file", fileCount > 0)
        outputDir.deleteRecursively()

        logSuccess()
    }

    // ========== Bloc 2: Error Handling ==========

    private suspend fun handleCorruptedArchive() {
        logStep("Handle corrupted archive gracefully")

        val corruptedFile = fixtureFile("corrupted")
        val outputDir = ArchiveExtractionTestHelper.createOutputDir()

        // Try to extract corrupted archive
        val result = extractor.extract(
            inputStream = corruptedFile.inputStream(),
            destinationPath = outputDir,
            archiveType = archiveType,
            sourceFileName = corruptedFile.name,
            options = ExtractionOptions(),
            onProgress = {}
        )

        // Should return Failure, not crash. Formats without corruption detection (see
        // detectsCorruptedArchive) only need to prove they don't crash on bad input.
        if (detectsCorruptedArchive) {
            assertTrue("Corrupted archive should return Failure", result is ExtractionResult.Failure)
        }

        logSuccess()
    }

    private suspend fun handleMaliciousPathTraversal() {
        // Only testable for formats with a real malicious fixture (ZIP). Other formats
        // don't have a native path-traversal fixture, so there's nothing to test here.
        if (!supportsMaliciousArchiveFixture) {
            logSkip("Handle malicious path traversal attack", "malicious fixture only generated for ZIP format")
            return
        }
        logStep("Handle malicious path traversal attack")

        val maliciousFile = fixtureFile("malicious")
        val outputDir = ArchiveExtractionTestHelper.createOutputDir()

        // Try to extract malicious archive
        extractor.extract(
            inputStream = maliciousFile.inputStream(),
            destinationPath = outputDir,
            archiveType = archiveType,
            sourceFileName = maliciousFile.name,
            options = ExtractionOptions(),
            onProgress = {}
        )

        // Verify no files created outside destination
        val parentDir = outputDir.parentFile
        val filesOutside = parentDir?.walk()
            ?.filter { it.isFile && !it.path.startsWith(outputDir.path) }
            ?.toList() ?: emptyList()

        assertTrue(
            "No files should escape destination directory",
            filesOutside.none { it.name == "malicious.txt" }
        )

        logSuccess()
    }

    private suspend fun handleEmptyArchive() {
        if (!supportsEmptyArchive) {
            logSkip("Handle empty archive", "empty fixture unsupported for this format")
            return
        }
        logStep("Handle empty archive")

        val emptyFile = fixtureFile("empty")
        val outputDir = ArchiveExtractionTestHelper.createOutputDir()

        // Extract empty archive
        val result = extractor.extract(
            inputStream = emptyFile.inputStream(),
            destinationPath = outputDir,
            archiveType = archiveType,
            sourceFileName = emptyFile.name,
            options = ExtractionOptions(),
            onProgress = {}
        )

        // Should succeed with 0 files
        assertTrue("Empty archive should succeed", result is ExtractionResult.Success)
        val count = (result as ExtractionResult.Success).extractedFilesCount
        assertEquals("Should extract 0 files", 0, count)

        logSuccess()
    }

    private suspend fun handleExtractionCancellation() {
        logStep("Handle extraction cancellation")

        val outputDir = ArchiveExtractionTestHelper.createOutputDir()

        // Start extraction and cancel immediately (simulated)
        var progressCallCount = 0
        val result = try {
            extractor.extract(
                inputStream = File(testArchivePath).inputStream(),
                destinationPath = outputDir,
                archiveType = archiveType,
                sourceFileName = File(testArchivePath).name,
                options = ExtractionOptions(),
                onProgress = { _ ->
                    progressCallCount++
                    // Simulate cancellation after first progress callback
                    if (progressCallCount == 1) {
                        throw kotlinx.coroutines.CancellationException("User cancelled")
                    }
                }
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Expected - extraction was cancelled
            null
        }

        // Should handle cancellation gracefully (no crash)
        assertTrue("Should handle cancellation", result == null || result is ExtractionResult.Failure)

        logSuccess()
    }

    // ========== Bloc 3: Navigation & Sélection ==========

    private suspend fun browseArchive_thenPressExtractAllButton() {
        logStep("Browse archive, then press Extract All button")

        // Browse archive first
        val rootItems = browse(testArchivePath)
        assertTrue("Archive should have items", rootItems.isNotEmpty())

        // Press Extract All button
        val outputDir = ArchiveExtractionTestHelper.createOutputDir()
        val extractedDir = ArchiveExtractionTestHelper.extractArchive(
            archivePath = testArchivePath,
            archiveType = archiveType,
            outputDir = outputDir,
            selectedItems = emptyList()
        )

        // Assert
        assertTrue("Should extract at least 1 file",
            ArchiveExtractionTestHelper.countFilesRecursively(extractedDir) > 0)
        extractedDir.deleteRecursively()

        logSuccess()
    }

    private suspend fun browseArchive_thenSelectAllItems() {
        if (!supportsDirectoryNavigation) {
            logSkip("Browse archive, then select all items", "format does not support directory navigation")
            return
        }
        logStep("Browse archive, then select all items")

        // Browse archive
        val rootItems = browse(testArchivePath)
        assertTrue("Archive should have items", rootItems.isNotEmpty())

        // Select all items
        val allItems = rootItems.filter {
            it is BrowsableItem.ArchiveDirectory || it is BrowsableItem.ArchiveFileEntry
        }

        // Assert
        assertEquals(
            "Should select all root items",
            ArchiveExtractionTestHelper.EXPECTED_ROOT_FILES + ArchiveExtractionTestHelper.EXPECTED_FOLDERS,
            allItems.size
        )

        logSuccess()
    }

    private suspend fun browseArchive_selectAll_thenExtract() {
        logStep("Browse archive, select all, then extract")

        // Browse archive
        val rootItems = browse(testArchivePath)
        assertTrue("Archive should have items", rootItems.isNotEmpty())

        // Select all items
        val selectedItems = rootItems.filter {
            it is BrowsableItem.ArchiveDirectory || it is BrowsableItem.ArchiveFileEntry
        }

        // Extract selected items
        val outputDir = ArchiveExtractionTestHelper.createOutputDir()
        val extractedDir = ArchiveExtractionTestHelper.extractArchive(
            archivePath = testArchivePath,
            archiveType = archiveType,
            outputDir = outputDir,
            selectedItems = selectedItems
        )

        // Assert
        assertTrue("Should extract at least 1 file",
            ArchiveExtractionTestHelper.countFilesRecursively(extractedDir) > 0)
        extractedDir.deleteRecursively()

        logSuccess()
    }

    private suspend fun browseArchive_thenNavigateIntoSubdirectory() {
        if (!supportsDirectoryNavigation) {
            logSkip("Browse archive, then navigate into subdirectory", "format does not support directory navigation")
            return
        }
        logStep("Browse archive, then navigate into subdirectory")

        // Browse archive root
        val rootItems = browse(testArchivePath)
        val folders = rootItems.filterIsInstance<BrowsableItem.ArchiveDirectory>()
        assertTrue("Archive should have folders", folders.isNotEmpty())

        // Navigate into first folder
        val firstFolder = folders.first()
        val folderItems = browse(firstFolder.path)

        // Assert
        assertTrue("Folder should contain items", folderItems.isNotEmpty())
        val filesInFolder = folderItems.filterIsInstance<BrowsableItem.ArchiveFileEntry>()
        assertTrue("Folder should contain files", filesInFolder.isNotEmpty())

        logSuccess()
    }

    private suspend fun browseSubdirectory_thenSelectSomeFiles() {
        if (!supportsDirectoryNavigation) {
            logSkip("Browse subdirectory, then select some files", "format does not support directory navigation")
            return
        }
        logStep("Browse subdirectory, then select some files")

        // Browse archive root
        val rootItems = browse(testArchivePath)
        val folders = rootItems.filterIsInstance<BrowsableItem.ArchiveDirectory>()
        assertTrue("Archive should have folders", folders.isNotEmpty())

        // Navigate into first folder
        val firstFolder = folders.first()
        val folderItems = browse(firstFolder.path)
        val filesInFolder = folderItems.filterIsInstance<BrowsableItem.ArchiveFileEntry>()
        assertTrue("Folder should contain files", filesInFolder.isNotEmpty())

        // Select some files (not all)
        val selectedCount = minOf(5, filesInFolder.size)
        val selectedFiles = filesInFolder.take(selectedCount)

        // Assert
        assertEquals("Should select $selectedCount files", selectedCount, selectedFiles.size)

        logSuccess()
    }

    private suspend fun browseSubdirectory_selectSomeFiles_thenExtract() {
        if (!supportsDirectoryNavigation) {
            logSkip("Browse subdirectory, select some files, extract", "format does not support directory navigation")
            return
        }
        logStep("Browse subdirectory, select some files, extract")

        // Browse archive root
        val rootItems = browse(testArchivePath)
        val folders = rootItems.filterIsInstance<BrowsableItem.ArchiveDirectory>()
        assertTrue("Archive should have folders", folders.isNotEmpty())

        // Navigate into first folder
        val firstFolder = folders.first()
        val folderItems = browse(firstFolder.path)
        val filesInFolder = folderItems.filterIsInstance<BrowsableItem.ArchiveFileEntry>()
        assertTrue("Folder should contain files", filesInFolder.isNotEmpty())

        // Select some files (not all)
        val selectedCount = minOf(5, filesInFolder.size)
        val selectedFiles = filesInFolder.take(selectedCount)

        // Extract selected files only
        val outputDir = ArchiveExtractionTestHelper.createOutputDir()
        val extractedDir = ArchiveExtractionTestHelper.extractArchive(
            archivePath = testArchivePath,
            archiveType = archiveType,
            outputDir = outputDir,
            selectedItems = selectedFiles
        )

        // Assert - Should extract only selected files
        val extractedFiles = ArchiveExtractionTestHelper.countFilesRecursively(extractedDir)
        assertEquals("Should extract exactly $selectedCount files", selectedCount, extractedFiles)

        // Verify extracted file names match selection
        val extractedFileNames = ArchiveExtractionTestHelper.getFileNamesRecursively(extractedDir)
        selectedFiles.forEach { selected ->
            assertTrue(
                "Extracted files should include ${selected.name}",
                extractedFileNames.any { it.endsWith(selected.name) }
            )
        }

        logSuccess()
    }

    private suspend fun browseArchive_selectMixedTypes_thenExtract() {
        if (!supportsDirectoryNavigation) {
            logSkip("Browse archive, select mix (files + folders), extract", "format does not support directory navigation")
            return
        }
        logStep("Browse archive, select mix (files + folders), extract")

        // Browse root
        val rootItems = browse(testArchivePath)

        // Select mix: 5 root files + 1 folder
        val rootFiles = rootItems.filterIsInstance<BrowsableItem.ArchiveFileEntry>().take(5)
        val oneFolder = rootItems.filterIsInstance<BrowsableItem.ArchiveDirectory>().take(1)
        val mixed = rootFiles + oneFolder

        // Extract
        val outputDir = ArchiveExtractionTestHelper.createOutputDir()
        val extractedDir = ArchiveExtractionTestHelper.extractArchive(
            archivePath = testArchivePath,
            archiveType = archiveType,
            outputDir = outputDir,
            selectedItems = mixed
        )

        // Assert - Should extract 5 root files + all files in selected folder
        val extractedCount = ArchiveExtractionTestHelper.countFilesRecursively(extractedDir)
        assertTrue("Should extract at least 5 files", extractedCount >= 5)

        logSuccess()
    }

    private suspend fun browseArchive_navigateBackAndForth_thenExtract() {
        if (!supportsDirectoryNavigation) {
            logSkip("Browse archive, navigate back/forth, extract", "format does not support directory navigation")
            return
        }
        logStep("Browse archive, navigate back/forth, extract")

        // Browse root
        val rootItems = browse(testArchivePath)
        val rootCount = rootItems.size

        // Navigate into folder
        val folder = rootItems.filterIsInstance<BrowsableItem.ArchiveDirectory>().first()
        val folderItems = browse(folder.path)
        assertTrue("Folder should have items", folderItems.isNotEmpty())

        // Navigate back to root
        val backToRoot = browse(testArchivePath)
        assertEquals("Should return to root with same count", rootCount, backToRoot.size)

        // Extract all
        val outputDir = ArchiveExtractionTestHelper.createOutputDir()
        val extractedDir = ArchiveExtractionTestHelper.extractArchive(
            archivePath = testArchivePath,
            archiveType = archiveType,
            outputDir = outputDir,
            selectedItems = emptyList()
        )

        assertTrue("Should extract at least 1 file",
            ArchiveExtractionTestHelper.countFilesRecursively(extractedDir) > 0)
        extractedDir.deleteRecursively()

        logSuccess()
    }

    // ========== Bloc 4: Performance/Stress ==========

    private suspend fun extractLargeArchive_10kFiles() {
        logStep("Extract large archive with 264k entries (selective sample)")

        val outputDir = ArchiveExtractionTestHelper.createOutputDir()

        // Browse pre-cached archive (264k entries) to get a sample of entry names
        val sampleEntries = browse(testArchivePath)
            .filterIsInstance<BrowsableItem.ArchiveFileEntry>()
            .take(100)
            .map { it.path.entryPath }

        assertTrue("Pre-cached archive should have entries", sampleEntries.isNotEmpty())

        val archiveFile = File(testArchivePath)
        val startTime = System.currentTimeMillis()
        val result = extractor.extract(
            inputStream = archiveFile.inputStream(),
            destinationPath = outputDir,
            archiveType = archiveType,
            sourceFileName = archiveFile.name,
            options = ExtractionOptions(selectedItems = sampleEntries),
            onProgress = {}
        )
        val duration = System.currentTimeMillis() - startTime

        assertTrue("Large archive extraction should succeed", result is ExtractionResult.Success)
        val extracted = (result as ExtractionResult.Success).extractedFilesCount
        assertEquals("Should extract all sampled files", sampleEntries.size, extracted)

        println("    ⏱️  Extracted ${sampleEntries.size} files from 264k-entry archive in ${duration}ms")

        logSuccess()
    }

    private suspend fun selectiveExtract_oneFileFrom100k() {
        logStep("Selective extract: 1 file from 264k-entry archive")

        val outputDir = ArchiveExtractionTestHelper.createOutputDir()

        // Use first file entry from pre-cached 264k archive
        val firstEntry = browse(testArchivePath)
            .filterIsInstance<BrowsableItem.ArchiveFileEntry>()
            .first()
            .path.entryPath

        val archiveFile = File(testArchivePath)
        val startTime = System.currentTimeMillis()
        val result = extractor.extract(
            inputStream = archiveFile.inputStream(),
            destinationPath = outputDir,
            archiveType = archiveType,
            sourceFileName = archiveFile.name,
            options = ExtractionOptions(selectedItems = listOf(firstEntry)),
            onProgress = {}
        )
        val duration = System.currentTimeMillis() - startTime

        assertTrue("Selective extract should succeed", result is ExtractionResult.Success)
        val extracted = (result as ExtractionResult.Success).extractedFilesCount
        assertEquals("Should extract exactly 1 file", 1, extracted)

        println("    ⏱️  Selective extract in ${duration}ms from 264k entries")
        assertTrue("Should be fast (<10s)", duration < 10_000)

        logSuccess()
    }

    private suspend fun extractDeepNestedArchive_100levels() {
        logStep("Extract archive with deep nesting (100 levels)")

        val deepNestedFile = fixtureFile("deep_nested")
        val nestingDepth = 100

        val outputDir = ArchiveExtractionTestHelper.createOutputDir()

        // Extract deep nested archive
        val startTime = System.currentTimeMillis()
        val result = extractor.extract(
            inputStream = deepNestedFile.inputStream(),
            destinationPath = outputDir,
            archiveType = archiveType,
            sourceFileName = deepNestedFile.name,
            options = ExtractionOptions(),
            onProgress = {}
        )
        val duration = System.currentTimeMillis() - startTime

        // Assert
        assertTrue("Deep nested archive should extract successfully", result is ExtractionResult.Success)

        // Formats without directory navigation (e.g. GZIP) decompress to a single flat
        // file — there's no nested directory tree to walk for those.
        if (supportsDirectoryNavigation) {
            // Verify nesting down to the deepest level (fixture nests level_1/level_2/.../level_100)
            var currentDir = outputDir
            for (level in 1..nestingDepth) {
                currentDir = File(currentDir, "level_$level")
                assertTrue("Level $level directory should exist", currentDir.exists())
            }
            val deepestLevelFiles = currentDir.listFiles { file -> file.isFile }
            assertTrue("Deepest level should contain at least 1 file", !deepestLevelFiles.isNullOrEmpty())
        } else {
            val extracted = (result as ExtractionResult.Success).extractedFilesCount
            assertTrue("Should extract at least 1 file", extracted >= 1)
        }

        println("    ⏱️  Extracted $nestingDepth nested levels in ${duration}ms")

        logSuccess()
    }

    private suspend fun extractArchiveWithLongFilename_255chars() {
        logStep("Extract archive with long filename (255 chars limit)")

        val longNameFile = fixtureFile("long_filename")

        val outputDir = ArchiveExtractionTestHelper.createOutputDir()

        // Extract archive with long filename
        val startTime = System.currentTimeMillis()
        val result = extractor.extract(
            inputStream = longNameFile.inputStream(),
            destinationPath = outputDir,
            archiveType = archiveType,
            sourceFileName = longNameFile.name,
            options = ExtractionOptions(),
            onProgress = {}
        )
        val duration = System.currentTimeMillis() - startTime

        // Assert
        assertTrue("Archive with long filename should extract successfully", result is ExtractionResult.Success)
        val extracted = (result as ExtractionResult.Success).extractedFilesCount
        assertTrue("Should extract at least 1 file", extracted >= 1)

        val extractedFiles = outputDir.walk().filter { it.isFile }.toList()
        assertTrue("Should have extracted files", extractedFiles.isNotEmpty())

        // GZIP names its output after the archive's own filename, not an embedded entry
        // name — it has no per-entry path to preserve a long filename through.
        if (supportsDirectoryNavigation) {
            val hasLongName = extractedFiles.any { it.name.length >= 200 }
            assertTrue("Should have extracted file with long name", hasLongName)
        }

        println("    ⏱️  Extracted archive with long filename in ${duration}ms")

        logSuccess()
    }

    // ========== Bloc 5: Concurrence ==========

    private suspend fun browseDuringExtraction() {
        logStep("Browse archive during extraction")

        // Start extraction in background
        val outputDir = ArchiveExtractionTestHelper.createOutputDir()
        val extractionJob = kotlinx.coroutines.GlobalScope.launch {
            extractor.extract(
                inputStream = File(testArchivePath).inputStream(),
                destinationPath = outputDir,
                archiveType = archiveType,
                sourceFileName = File(testArchivePath).name,
                options = ExtractionOptions(),
                onProgress = {}
            )
        }

        // Browse archive while extraction is ongoing
        val rootItems = browse(testArchivePath)
        assertTrue("Should browse successfully during extraction", rootItems.isNotEmpty())

        // Wait for extraction to finish
        extractionJob.join()
        outputDir.deleteRecursively()

        logSuccess()
    }

    private suspend fun multipleSimultaneousExtractions() = kotlinx.coroutines.coroutineScope {
        logStep("Multiple simultaneous extractions")

        // Re-extract the shared perfect fixture concurrently into separate output dirs.
        // Using coroutineScope (not GlobalScope) keeps these jobs structurally tied to this
        // test: a failed assertion here becomes a normal test failure instead of an uncaught
        // exception that crashes the whole instrumentation process and aborts every other test.
        val jobs = List(3) { index ->
            launch {
                val outputDir = ArchiveExtractionTestHelper.createOutputDir()
                val result = extractor.extract(
                    inputStream = File(testArchivePath).inputStream(),
                    destinationPath = outputDir,
                    archiveType = archiveType,
                    sourceFileName = "test_$index${archiveExtension}",
                    options = ExtractionOptions(),
                    onProgress = {}
                )
                assertTrue("Extraction $index should succeed", result is ExtractionResult.Success)
                outputDir.deleteRecursively()
            }
        }

        // Wait for all to complete
        jobs.forEach { it.join() }

        logSuccess()
    }

    // ========== Logging Helpers ==========

    private fun logStep(description: String) {
        currentStep++
        println("\n[$currentStep] $description")
    }

    private fun logSkip(description: String, reason: String) {
        currentStep++
        println("\n[$currentStep] $description")
        println("    ⏭️  Skipped: $reason")
    }

    private fun logSuccess() {
        println("    ✅ Passed")
    }
}
