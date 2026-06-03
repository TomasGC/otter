package app.otter.domain.usecase.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otter.data.extractor.ArchiveExtractor
import app.otter.data.extractor.TestArchiveHelper
import app.otter.domain.model.ArchiveType
import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.ExtractionResult
import app.otter.domain.usecase.helpers.ArchiveExtractionTestHelper
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
import org.junit.rules.TemporaryFolder
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

    @get:Rule(order = 1)
    val tempFolder = TemporaryFolder()

    // Inject BrowseItemsUseCase
    @Inject
    lateinit var browseItemsUseCase: app.otter.domain.usecase.BrowseItemsUseCase

    // Subclasses must provide archive type, path, extractor, and extension
    protected abstract val archiveType: ArchiveType
    protected abstract val testArchivePath: String
    protected abstract val extractor: ArchiveExtractor
    protected abstract val archiveExtension: String // e.g., ".zip"

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
    private val archiveExtensions = setOf(".zip", ".rar", ".7z", ".tar", ".gz", ".tgz", ".rpa")

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

        val corruptedFile = tempFolder.newFile("corrupted${archiveExtension}")
        createCorruptedArchive(corruptedFile)

        val outputDir = ArchiveExtractionTestHelper.createOutputDir()

        // Try to extract corrupted archive
        val result = extractor.extract(
            inputStream = corruptedFile.inputStream(),
            destinationPath = outputDir,
            archiveType = archiveType,
            sourceFileName = corruptedFile.name,
            selectedItems = null,
            onProgress = {}
        )

        // Should return Failure, not crash
        assertTrue("Corrupted archive should return Failure", result is ExtractionResult.Failure)

        logSuccess()
    }

    private suspend fun handleMaliciousPathTraversal() {
        logStep("Handle malicious path traversal attack")

        val maliciousFile = tempFolder.newFile("malicious${archiveExtension}")
        createMaliciousArchive(maliciousFile)

        val outputDir = ArchiveExtractionTestHelper.createOutputDir()

        // Try to extract malicious archive
        extractor.extract(
            inputStream = maliciousFile.inputStream(),
            destinationPath = outputDir,
            archiveType = archiveType,
            sourceFileName = maliciousFile.name,
            selectedItems = null,
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
        logStep("Handle empty archive")

        val emptyFile = tempFolder.newFile("empty${archiveExtension}")
        createEmptyArchive(emptyFile)

        val outputDir = ArchiveExtractionTestHelper.createOutputDir()

        // Extract empty archive
        val result = extractor.extract(
            inputStream = emptyFile.inputStream(),
            destinationPath = outputDir,
            archiveType = archiveType,
            sourceFileName = emptyFile.name,
            selectedItems = null,
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
                selectedItems = null,
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
        logStep("Extract large archive with 10k+ files")

        // Create large archive programmatically
        val largeFile = tempFolder.newFile("large_10k${archiveExtension}")
        val fileCount = 10_000
        createLargeArchive(largeFile, fileCount)

        val outputDir = ArchiveExtractionTestHelper.createOutputDir()

        // Extract large archive
        val startTime = System.currentTimeMillis()
        val result = extractor.extract(
            inputStream = largeFile.inputStream(),
            destinationPath = outputDir,
            archiveType = archiveType,
            sourceFileName = largeFile.name,
            selectedItems = null,
            onProgress = {}
        )
        val duration = System.currentTimeMillis() - startTime

        // Assert
        assertTrue("Large archive should extract successfully", result is ExtractionResult.Success)
        val extracted = (result as ExtractionResult.Success).extractedFilesCount
        assertEquals("Should extract all $fileCount files", fileCount, extracted)

        println("    ⏱️  Extracted $fileCount files in ${duration}ms (${duration / maxOf(1, fileCount)}ms per file)")

        logSuccess()
    }

    private suspend fun selectiveExtract_oneFileFrom100k() {
        logStep("Selective extract: 1 file from 100k archive")

        // Create archive with 100k files
        val massiveFile = tempFolder.newFile("massive_100k${archiveExtension}")
        val totalFiles = 100_000
        createLargeArchive(massiveFile, totalFiles)

        val outputDir = ArchiveExtractionTestHelper.createOutputDir()

        // Extract only first file using resource path
        val startTime = System.currentTimeMillis()
        val selectedPath = app.otter.domain.model.ResourcePath.ArchiveEntry(
            archivePath = massiveFile.absolutePath,
            entryPath = "file_0.txt"
        )

        val result = extractor.extract(
            inputStream = massiveFile.inputStream(),
            destinationPath = outputDir,
            archiveType = archiveType,
            sourceFileName = massiveFile.name,
            selectedItems = listOf(selectedPath.entryPath), // Extract only first file
            onProgress = {}
        )
        val duration = System.currentTimeMillis() - startTime

        // Assert - Should be fast (no need to scan all files)
        assertTrue("Selective extract should succeed", result is ExtractionResult.Success)
        val extracted = (result as ExtractionResult.Success).extractedFilesCount
        assertEquals("Should extract exactly 1 file", 1, extracted)

        println("    ⏱️  Selective extract in ${duration}ms from $totalFiles files")
        assertTrue("Should be fast (<5s)", duration < 5000)

        logSuccess()
    }

    private suspend fun extractDeepNestedArchive_100levels() {
        logStep("Extract archive with deep nesting (100 levels)")

        // Create archive with deep nesting structure
        val deepNestedFile = tempFolder.newFile("deep_nested${archiveExtension}")
        val nestingDepth = 100
        createDeepNestedArchive(deepNestedFile, nestingDepth)

        val outputDir = ArchiveExtractionTestHelper.createOutputDir()

        // Extract deep nested archive
        val startTime = System.currentTimeMillis()
        val result = extractor.extract(
            inputStream = deepNestedFile.inputStream(),
            destinationPath = outputDir,
            archiveType = archiveType,
            sourceFileName = deepNestedFile.name,
            selectedItems = null,
            onProgress = {}
        )
        val duration = System.currentTimeMillis() - startTime

        // Assert
        assertTrue("Deep nested archive should extract successfully", result is ExtractionResult.Success)

        // Verify deepest file exists
        var currentDir = outputDir
        repeat(nestingDepth) { level ->
            currentDir = File(currentDir, "level_$level")
            assertTrue("Level $level directory should exist", currentDir.exists())
        }
        val deepestFile = File(currentDir, "deep_file.txt")
        assertTrue("Deepest file should exist at level $nestingDepth", deepestFile.exists())

        println("    ⏱️  Extracted $nestingDepth nested levels in ${duration}ms")

        logSuccess()
    }

    private suspend fun extractArchiveWithLongFilename_255chars() {
        logStep("Extract archive with long filename (255 chars limit)")

        // Create archive with very long filename
        val longNameFile = tempFolder.newFile("long_name${archiveExtension}")
        val maxFilenameLength = 255
        createArchiveWithLongFilename(longNameFile, maxFilenameLength)

        val outputDir = ArchiveExtractionTestHelper.createOutputDir()

        // Extract archive with long filename
        val startTime = System.currentTimeMillis()
        val result = extractor.extract(
            inputStream = longNameFile.inputStream(),
            destinationPath = outputDir,
            archiveType = archiveType,
            sourceFileName = longNameFile.name,
            selectedItems = null,
            onProgress = {}
        )
        val duration = System.currentTimeMillis() - startTime

        // Assert
        assertTrue("Archive with long filename should extract successfully", result is ExtractionResult.Success)
        val extracted = (result as ExtractionResult.Success).extractedFilesCount
        assertTrue("Should extract at least 1 file", extracted >= 1)

        // Verify long filename file exists (truncated if necessary)
        val extractedFiles = outputDir.walk().filter { it.isFile }.toList()
        assertTrue("Should have extracted files", extractedFiles.isNotEmpty())

        val hasLongName = extractedFiles.any { it.name.length >= 200 }
        assertTrue("Should have extracted file with long name", hasLongName)

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
                selectedItems = null,
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

    private suspend fun multipleSimultaneousExtractions() {
        logStep("Multiple simultaneous extractions")

        // Use a small programmatic archive to avoid disk pressure during concurrent extractions
        val smallArchive = tempFolder.newFile("concurrent_test${archiveExtension}")
        createLargeArchive(smallArchive, 100)

        // Start 3 extractions simultaneously
        val jobs = List(3) { index ->
            kotlinx.coroutines.GlobalScope.launch {
                val outputDir = ArchiveExtractionTestHelper.createOutputDir()
                val result = extractor.extract(
                    inputStream = smallArchive.inputStream(),
                    destinationPath = outputDir,
                    archiveType = archiveType,
                    sourceFileName = "test_$index${archiveExtension}",
                    selectedItems = null,
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

    // ========== Archive-Specific Helpers (to be implemented by subclasses) ==========

    protected abstract fun createCorruptedArchive(file: File)
    protected abstract fun createMaliciousArchive(file: File)
    protected abstract fun createEmptyArchive(file: File)
    protected abstract fun createLargeArchive(file: File, fileCount: Int)
    protected abstract fun createDeepNestedArchive(file: File, depth: Int)
    protected abstract fun createArchiveWithLongFilename(file: File, maxLength: Int)

    // ========== Logging Helpers ==========

    private fun logStep(description: String) {
        currentStep++
        println("\n[$currentStep] $description")
    }

    private fun logSuccess() {
        println("    ✅ Passed")
    }
}
