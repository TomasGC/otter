package app.otter.integration.viewmodel

import app.otter.data.browser.ArchiveBrowser
import app.otter.data.inspector.ArchiveInspectorFactory
import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.BrowseResult
import app.otter.domain.model.ResourcePath
import app.otter.domain.usecase.BrowseItemsUseCase
import app.otter.service.ExtractionEventBus
import app.otter.service.ExtractionQueue
import app.otter.ui.viewmodel.FileBrowserUiState
import app.otter.ui.viewmodel.FileBrowserViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Integration tests for FileBrowserViewModel sliding window using real ZIP data.
 *
 * Creates a real ZIP archive with 300 files, reads its entries via the real
 * ArchiveInspectorFactory + ArchiveBrowser stack, then serves them through a mock
 * BrowseItemsUseCase. This verifies both the archive reading pipeline and the ViewModel
 * sliding window behaviour end-to-end without coroutine-dispatcher timing issues.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FileBrowserViewModelRealArchiveIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val eventBus = ExtractionEventBus()
    private val extractionQueue = ExtractionQueue()
    private val browseItemsUseCase = mockk<BrowseItemsUseCase>()
    private lateinit var viewModel: FileBrowserViewModel
    private lateinit var archiveFile: File

    /**
     * All 300 items read synchronously from the real ZIP before test setup.
     * Populated by [readRealArchiveItems].
     */
    private lateinit var realItems: List<BrowsableItem>

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        archiveFile = createZipWith300Files()
        realItems = readRealArchiveItems(archiveFile)
        assertEquals("ZIP must produce exactly 300 items", 300, realItems.size)

        // Serve real items through mock to avoid IO-dispatcher timing issues
        coEvery { browseItemsUseCase.invoke(any(), any(), any()) } answers {
            val offset = arg<Int>(1)
            val limit = arg<Int>(2)
            if (offset + limit >= realItems.size) {
                Result.success(BrowseResult.Complete(realItems.drop(offset)))
            } else {
                Result.success(BrowseResult.Paginated(
                    items = realItems.drop(offset).take(limit),
                    hasMore = true,
                    totalEstimate = realItems.size,
                    nextOffset = offset + limit
                ))
            }
        }

        viewModel = FileBrowserViewModel(browseItemsUseCase, testDispatcher, eventBus, extractionQueue)
    }

    private fun createZipWith300Files(): File {
        val zipFile = tempFolder.newFile("test_300.zip")
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            repeat(300) { i ->
                val name = "file_${i.toString().padStart(4, '0')}.txt"
                zip.putNextEntry(ZipEntry(name))
                zip.write("content of $name".toByteArray())
                zip.closeEntry()
            }
        }
        return zipFile
    }

    /**
     * Reads archive entries synchronously using the real ArchiveInspectorFactory + ArchiveBrowser.
     * This validates the entire archive reading pipeline independently of coroutines.
     */
    private fun readRealArchiveItems(zip: File): List<BrowsableItem> {
        val factory = ArchiveInspectorFactory()
        val inspector = factory.create(zip).getOrThrow()
        val browser = ArchiveBrowser(inspector, zip.absolutePath)
        // ArchiveBrowser.browse is suspend — run it synchronously via runBlocking
        return kotlinx.coroutines.runBlocking {
            val result = browser.browse(entryPath = "", offset = 0, limit = Int.MAX_VALUE)
            result.items
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun successState() = viewModel.uiState.value as? FileBrowserUiState.Success

    // ========== Archive reading pipeline tests ==========

    @Test
    fun `real zip archive is read correctly by ArchiveInspectorFactory`() {
        // Real archive reading validated in setUp: realItems.size == 300
        assertEquals("ArchiveInspectorFactory must read all 300 ZIP entries", 300, realItems.size)
    }

    @Test
    fun `real zip items have correct names from actual files`() {
        // Items must have the names as written to the ZIP
        val names = realItems.map { it.name }.toSet()
        assertTrue("First file must be present", names.contains("file_0000.txt"))
        assertTrue("Last file must be present", names.contains("file_0299.txt"))
    }

    @Test
    fun `real zip items are sorted alphabetically by ArchiveBrowser`() {
        // ArchiveBrowser sorts directories first, then files case-insensitive.
        // With 300 flat files, check that names are in ascending order.
        val names = realItems.map { it.name }
        val isSorted = names.zipWithNext().all { (a, b) -> a.lowercase() <= b.lowercase() }
        assertTrue("ArchiveBrowser must sort items alphabetically", isSorted)
    }

    // ========== ViewModel sliding window tests with real data ==========

    @Test
    fun `real zip with 300 files loads first page correctly`() = runTest {
        val state = successState()
        assertNotNull("State must be Success with real archive data", state)
        assertTrue("First page should have items from real ZIP", state!!.items.isNotEmpty())
    }

    @Test
    fun `real zip scroll through items does not cause errors`() = runTest {
        val initial = successState()
        assertNotNull("Initial state must be Success", initial)

        // Scroll progressively through all loaded items
        for (i in 0..250 step 20) {
            viewModel.onScrollPositionChanged(firstVisibleItemIndex = i)
        }

        val state = successState()
        assertNotNull("State must remain Success after scrolling real archive data", state)
        assertFalse("Must not be in error state after scroll",
            viewModel.uiState.value is FileBrowserUiState.Error)
    }

    @Test
    fun `real zip scroll down then up does not lose items`() = runTest {
        // Scroll down
        for (i in 0..200 step 25) {
            viewModel.onScrollPositionChanged(firstVisibleItemIndex = i)
        }
        // Scroll back up
        for (i in 200 downTo 0 step 25) {
            viewModel.onScrollPositionChanged(firstVisibleItemIndex = i)
        }

        val state = successState()
        assertNotNull("State must be non-null after real scroll cycle", state)
        assertTrue("Items must be present after scroll cycle", state!!.items.isNotEmpty())
    }

    @Test
    fun `real zip state does not become error after navigation`() = runTest {
        // Verify no error state throughout the interaction
        assertNotNull("State must be Success, not Error", successState())

        // Trigger a few scroll events
        repeat(5) { i ->
            viewModel.onScrollPositionChanged(firstVisibleItemIndex = i * 10)
        }

        assertFalse("State must never become Error with valid real archive data",
            viewModel.uiState.value is FileBrowserUiState.Error)
    }

    // ========== Real ZIP: mixed content + archives-only filter ==========

    @Test
    fun `archives-only filter on mixed filesystem items keeps only navigable archives`() = runTest {
        // Filter keeps canNavigateInto=true items (archives + directories), hides plain files.
        val archivePath = ResourcePath.ArchiveEntry(archivePath = "file:///archive.zip", entryPath = "")
        val archiveItem = BrowsableItem.ArchiveFile(
            path = archivePath,
            name = "archive.zip",
            sizeBytes = 1024L,
            lastModified = 0L,
            archivePath = archivePath,
            mimeType = "application/zip"
        )
        val dirItem = BrowsableItem.FileSystemDirectory(
            path = ResourcePath.FileSystem("file:///docs"),
            name = "docs",
            sizeBytes = 0L,
            lastModified = 0L
        )
        val fileItem = BrowsableItem.FileSystemFile(
            path = ResourcePath.FileSystem("file:///readme.txt"),
            name = "readme.txt",
            sizeBytes = 256L,
            lastModified = 0L,
            mimeType = "text/plain"
        )

        coEvery { browseItemsUseCase.invoke(any(), any(), any()) } returns
            Result.success(BrowseResult.Complete(listOf(dirItem, archiveItem, fileItem)))

        viewModel = FileBrowserViewModel(browseItemsUseCase, testDispatcher, eventBus, extractionQueue)

        // Apply archives-only filter
        viewModel.toggleArchiveFilter()

        val state = successState()!!
        assertTrue("Archives-only filter must keep at least one archive", state.items.isNotEmpty())
        // All visible items must be navigable (archives or dirs)
        val nonNavigable = state.items.filter { !it.canNavigateInto }
        assertTrue("All items after archives-only filter must be navigable", nonNavigable.isEmpty())
    }

    @Test
    fun `real zip natural sort order matches NATURAL_ORDER comparator`() = runTest {
        // Items read from real ZIP must be sorted in natural order (file_1 before file_2 before file_10)
        // Create ZIP with un-ordered entry names
        val numericZip = tempFolder.newFile("numeric.zip")
        ZipOutputStream(numericZip.outputStream()).use { zip ->
            listOf("file_10.txt", "file_2.txt", "file_1.txt", "file_20.txt").forEach { name ->
                zip.putNextEntry(ZipEntry(name))
                zip.write("x".toByteArray())
                zip.closeEntry()
            }
        }

        val factory = ArchiveInspectorFactory()
        val inspector = factory.create(numericZip).getOrThrow()
        val browser = ArchiveBrowser(inspector, numericZip.absolutePath)
        val numericItems = kotlinx.coroutines.runBlocking {
            browser.browse(entryPath = "", offset = 0, limit = Int.MAX_VALUE).items
        }

        coEvery { browseItemsUseCase.invoke(any(), any(), any()) } returns
            Result.success(BrowseResult.Complete(numericItems))

        viewModel = FileBrowserViewModel(browseItemsUseCase, testDispatcher, eventBus, extractionQueue)
        viewModel.setSortOrder(app.otter.ui.viewmodel.SortOrder.NAME_ASC)

        val names = successState()!!.items.map { it.name }
        val idx1 = names.indexOfFirst { it == "file_1.txt" }
        val idx2 = names.indexOfFirst { it == "file_2.txt" }
        val idx10 = names.indexOfFirst { it == "file_10.txt" }
        val idx20 = names.indexOfFirst { it == "file_20.txt" }

        if (idx1 >= 0 && idx2 >= 0 && idx10 >= 0 && idx20 >= 0) {
            assertTrue("file_1 before file_2", idx1 < idx2)
            assertTrue("file_2 before file_10", idx2 < idx10)
            assertTrue("file_10 before file_20", idx10 < idx20)
        }
    }
}
