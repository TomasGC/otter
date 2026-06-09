package app.otter.data.browser

import app.otter.domain.inspector.ArchiveEntry
import app.otter.domain.inspector.ArchiveInspector
import app.otter.domain.inspector.ArchiveType
import app.otter.domain.model.BrowseResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for ArchiveBrowser pagination logic.
 *
 * TDD - Phase 3: Pagination
 * Covers:
 * - Pagination threshold (10k items)
 * - Boundary conditions (exactly 10k, 10k+1, 9999)
 * - Interleaved page requests
 */
class ArchiveBrowserPaginationTest {

    // ========== RED Phase: Task #70 - Pagination Threshold Tests ==========

    @Test
    fun `browse with less than 10k items returns Complete result`() = runTest {
        // Arrange - Create inspector with 9999 items
        val entries = (0 until 9999).map { index ->
            ArchiveEntry(
                path = "file_$index.txt",
                isDirectory = false,
                sizeBytes = 1024L,
                compressedSize = 512L,
                lastModified = System.currentTimeMillis()
            )
        }

        val inspector = mockk<ArchiveInspector>()
        every { inspector.entries() } returns entries.asSequence()
        coEvery { inspector.countEntries() } returns entries.size
        every { inspector.getArchiveType() } returns ArchiveType.ZIP
        every { inspector.close() } returns Unit

        val archivePath = "/test/archive.zip"
        val browser = ArchiveBrowser(inspector, archivePath)

        // Act - Browse without pagination (offset = 0, limit = Int.MAX_VALUE to get all)
        val result = browser.browse(entryPath = "", offset = 0, limit = Int.MAX_VALUE)

        // Assert - Result should be Complete (< 10k threshold)
        assertTrue("Result should be Complete for < 10k items", result is BrowseResult.Complete)
        val complete = result as BrowseResult.Complete
        assertEquals("Should have all 9999 items", 9999, complete.items.size)
    }

    @Test
    fun `browse with exactly 10k items returns Paginated result`() = runTest {
        // Arrange - Create inspector with exactly 10k items
        val entries = (0 until 10_000).map { index ->
            ArchiveEntry(
                path = "file_$index.txt",
                isDirectory = false,
                sizeBytes = 1024L,
                compressedSize = 512L,
                lastModified = System.currentTimeMillis()
            )
        }

        val inspector = mockk<ArchiveInspector>()
        every { inspector.entries() } returns entries.asSequence()
        coEvery { inspector.countEntries() } returns entries.size
        every { inspector.getArchiveType() } returns ArchiveType.ZIP
        every { inspector.close() } returns Unit

        val archivePath = "/test/archive.zip"
        val browser = ArchiveBrowser(inspector, archivePath)

        // Act - Browse first page
        val result = browser.browse(entryPath = "", offset = 0, limit = 100)

        // Assert - Result should be Paginated (>= 10k threshold)
        assertTrue("Result should be Paginated for >= 10k items", result is BrowseResult.Paginated)
        val paginated = result as BrowseResult.Paginated
        assertEquals("Should return 100 items (limit)", 100, paginated.items.size)
        assertEquals("Total estimate should be 10k", 10_000, paginated.totalEstimate)
        assertTrue("Should have more pages", paginated.hasMore)
        assertEquals("Next offset should be 100", 100, paginated.nextOffset)
    }

    @Test
    fun `browse with more than 10k items returns Paginated result`() = runTest {
        // Arrange - Create inspector with 15k items
        val entries = (0 until 15_000).map { index ->
            ArchiveEntry(
                path = "file_$index.txt",
                isDirectory = false,
                sizeBytes = 1024L,
                compressedSize = 512L,
                lastModified = System.currentTimeMillis()
            )
        }

        val inspector = mockk<ArchiveInspector>()
        every { inspector.entries() } returns entries.asSequence()
        coEvery { inspector.countEntries() } returns entries.size
        every { inspector.getArchiveType() } returns ArchiveType.ZIP
        every { inspector.close() } returns Unit

        val archivePath = "/test/archive.zip"
        val browser = ArchiveBrowser(inspector, archivePath)

        // Act - Browse first page
        val result = browser.browse(entryPath = "", offset = 0, limit = 100)

        // Assert - Result should be Paginated
        assertTrue("Result should be Paginated for > 10k items", result is BrowseResult.Paginated)
        val paginated = result as BrowseResult.Paginated
        assertEquals("Should return 100 items", 100, paginated.items.size)
        assertEquals("Total estimate should be 15k", 15_000, paginated.totalEstimate)
        assertTrue("Should have more pages", paginated.hasMore)
    }

    // ========== RED Phase: Task #71 - Pagination Boundary Tests ==========

    @Test
    fun `browse last page returns hasMore false`() = runTest {
        // Arrange - Create inspector with 250 items (2.5 pages with limit 100)
        val entries = (0 until 250).map { index ->
            ArchiveEntry(
                path = "file_$index.txt",
                isDirectory = false,
                sizeBytes = 1024L,
                compressedSize = 512L,
                lastModified = System.currentTimeMillis()
            )
        }

        val inspector = mockk<ArchiveInspector>()
        every { inspector.entries() } returns entries.asSequence()
        coEvery { inspector.countEntries() } returns entries.size
        every { inspector.getArchiveType() } returns ArchiveType.ZIP
        every { inspector.close() } returns Unit

        val archivePath = "/test/archive.zip"
        val browser = ArchiveBrowser(inspector, archivePath)

        // Calculate expected lexicographic order
        val sortedNames = entries.map { "file_${it.path.removePrefix("file_").removeSuffix(".txt")}.txt" }
            .sortedBy { it.lowercase() }

        // Act - Browse last page (offset 200, should return 50 items)
        val result = browser.browse(entryPath = "", offset = 200, limit = 100)

        // Assert - Last page should have hasMore = false
        assertTrue("Result should be Complete for last page", result is BrowseResult.Complete)
        val complete = result as BrowseResult.Complete
        assertEquals("Should return remaining 50 items", 50, complete.items.size)
        assertEquals("First item should match lexicographic order", sortedNames[200], complete.items.first().name)
        assertEquals("Last item should match lexicographic order", sortedNames[249], complete.items.last().name)
    }

    @Test
    fun `browse with offset beyond total returns empty Complete result`() = runTest {
        // Arrange - Create inspector with 100 items
        val entries = (0 until 100).map { index ->
            ArchiveEntry(
                path = "file_$index.txt",
                isDirectory = false,
                sizeBytes = 1024L,
                compressedSize = 512L,
                lastModified = System.currentTimeMillis()
            )
        }

        val inspector = mockk<ArchiveInspector>()
        every { inspector.entries() } returns entries.asSequence()
        coEvery { inspector.countEntries() } returns entries.size
        every { inspector.getArchiveType() } returns ArchiveType.ZIP
        every { inspector.close() } returns Unit

        val archivePath = "/test/archive.zip"
        val browser = ArchiveBrowser(inspector, archivePath)

        // Act - Browse with offset beyond total (offset 200 > 100 items)
        val result = browser.browse(entryPath = "", offset = 200, limit = 100)

        // Assert - Should return empty Complete result
        assertTrue("Result should be Complete when offset > total", result is BrowseResult.Complete)
        val complete = result as BrowseResult.Complete
        assertTrue("Should return empty list", complete.items.isEmpty())
    }

    @Test
    fun `browse with offset at exact boundary returns correct page`() = runTest {
        // Arrange - Create inspector with 300 items (exactly 3 pages with limit 100)
        val entries = (0 until 300).map { index ->
            ArchiveEntry(
                path = "file_$index.txt",
                isDirectory = false,
                sizeBytes = 1024L,
                compressedSize = 512L,
                lastModified = System.currentTimeMillis()
            )
        }

        val inspector = mockk<ArchiveInspector>()
        every { inspector.entries() } returns entries.asSequence()
        coEvery { inspector.countEntries() } returns entries.size
        every { inspector.getArchiveType() } returns ArchiveType.ZIP
        every { inspector.close() } returns Unit

        val archivePath = "/test/archive.zip"
        val browser = ArchiveBrowser(inspector, archivePath)

        // Calculate expected lexicographic order
        val sortedNames = entries.map { "file_${it.path.removePrefix("file_").removeSuffix(".txt")}.txt" }
            .sortedBy { it.lowercase() }

        // Act - Browse page 2 (offset 100)
        val result = browser.browse(entryPath = "", offset = 100, limit = 100)

        // Assert - Should return exactly 100 items from index 100-199
        assertTrue("Result should be Complete for page 2", result is BrowseResult.Complete)
        val complete = result as BrowseResult.Complete
        assertEquals("Should return 100 items", 100, complete.items.size)
        assertEquals("First item should match lexicographic order", sortedNames[100], complete.items.first().name)
        assertEquals("Last item should match lexicographic order", sortedNames[199], complete.items.last().name)
    }

    // ========== RED Phase: Task #72 - Interleaved Pagination Tests ==========

    @Test
    fun `multiple sequential page requests return consistent results`() = runTest {
        // Arrange - Create inspector with 500 items
        val entries = (0 until 500).map { index ->
            ArchiveEntry(
                path = "file_$index.txt",
                isDirectory = false,
                sizeBytes = 1024L,
                compressedSize = 512L,
                lastModified = System.currentTimeMillis()
            )
        }

        val inspector = mockk<ArchiveInspector>()
        every { inspector.entries() } returns entries.asSequence()
        coEvery { inspector.countEntries() } returns entries.size
        every { inspector.getArchiveType() } returns ArchiveType.ZIP
        every { inspector.close() } returns Unit

        val archivePath = "/test/archive.zip"
        val browser = ArchiveBrowser(inspector, archivePath)

        // Calculate expected lexicographic order
        val sortedNames = entries.map { "file_${it.path.removePrefix("file_").removeSuffix(".txt")}.txt" }
            .sortedBy { it.lowercase() }

        // Act - Browse pages sequentially (0, 100, 200, 300, 400)
        val page1 = browser.browse(entryPath = "", offset = 0, limit = 100)
        val page2 = browser.browse(entryPath = "", offset = 100, limit = 100)
        val page3 = browser.browse(entryPath = "", offset = 200, limit = 100)
        val page4 = browser.browse(entryPath = "", offset = 300, limit = 100)
        val page5 = browser.browse(entryPath = "", offset = 400, limit = 100)

        // Assert - All pages should have correct items
        assertTrue("Page 1 should be Complete", page1 is BrowseResult.Complete)
        assertTrue("Page 2 should be Complete", page2 is BrowseResult.Complete)
        assertTrue("Page 3 should be Complete", page3 is BrowseResult.Complete)
        assertTrue("Page 4 should be Complete", page4 is BrowseResult.Complete)
        assertTrue("Page 5 should be Complete", page5 is BrowseResult.Complete)

        assertEquals("Page 1 should have 100 items", 100, (page1 as BrowseResult.Complete).items.size)
        assertEquals("Page 2 should have 100 items", 100, (page2 as BrowseResult.Complete).items.size)
        assertEquals("Page 3 should have 100 items", 100, (page3 as BrowseResult.Complete).items.size)
        assertEquals("Page 4 should have 100 items", 100, (page4 as BrowseResult.Complete).items.size)
        assertEquals("Page 5 should have 100 items", 100, (page5 as BrowseResult.Complete).items.size)

        // Verify no overlap between pages (lexicographic order)
        assertEquals("Page 1 first item", sortedNames[0], page1.items.first().name)
        assertEquals("Page 2 first item", sortedNames[100], page2.items.first().name)
        assertEquals("Page 3 first item", sortedNames[200], page3.items.first().name)
        assertEquals("Page 4 first item", sortedNames[300], page4.items.first().name)
        assertEquals("Page 5 first item", sortedNames[400], page5.items.first().name)
    }

    @Test
    fun `non-sequential page requests return correct results`() = runTest {
        // Arrange - Create inspector with 500 items
        val entries = (0 until 500).map { index ->
            ArchiveEntry(
                path = "file_$index.txt",
                isDirectory = false,
                sizeBytes = 1024L,
                compressedSize = 512L,
                lastModified = System.currentTimeMillis()
            )
        }

        val inspector = mockk<ArchiveInspector>()
        every { inspector.entries() } returns entries.asSequence()
        coEvery { inspector.countEntries() } returns entries.size
        every { inspector.getArchiveType() } returns ArchiveType.ZIP
        every { inspector.close() } returns Unit

        val archivePath = "/test/archive.zip"
        val browser = ArchiveBrowser(inspector, archivePath)

        // Calculate expected lexicographic order
        val sortedNames = entries.map { "file_${it.path.removePrefix("file_").removeSuffix(".txt")}.txt" }
            .sortedBy { it.lowercase() }

        // Act - Browse pages in non-sequential order (400, 0, 200, 100, 300)
        val page5 = browser.browse(entryPath = "", offset = 400, limit = 100)
        val page1 = browser.browse(entryPath = "", offset = 0, limit = 100)
        val page3 = browser.browse(entryPath = "", offset = 200, limit = 100)
        val page2 = browser.browse(entryPath = "", offset = 100, limit = 100)
        val page4 = browser.browse(entryPath = "", offset = 300, limit = 100)

        // Assert - All pages should have correct items regardless of order (lexicographic)
        assertEquals("Page 5 first item", sortedNames[400], (page5 as BrowseResult.Complete).items.first().name)
        assertEquals("Page 1 first item", sortedNames[0], (page1 as BrowseResult.Complete).items.first().name)
        assertEquals("Page 3 first item", sortedNames[200], (page3 as BrowseResult.Complete).items.first().name)
        assertEquals("Page 2 first item", sortedNames[100], (page2 as BrowseResult.Complete).items.first().name)
        assertEquals("Page 4 first item", sortedNames[300], (page4 as BrowseResult.Complete).items.first().name)

        // Verify all pages have correct size
        assertEquals("Page 5 size", 100, page5.items.size)
        assertEquals("Page 1 size", 100, page1.items.size)
        assertEquals("Page 3 size", 100, page3.items.size)
        assertEquals("Page 2 size", 100, page2.items.size)
        assertEquals("Page 4 size", 100, page4.items.size)
    }

    @Test
    fun `revisiting same page returns same results`() = runTest {
        // Arrange - Create inspector with 300 items
        val entries = (0 until 300).map { index ->
            ArchiveEntry(
                path = "file_$index.txt",
                isDirectory = false,
                sizeBytes = 1024L,
                compressedSize = 512L,
                lastModified = System.currentTimeMillis()
            )
        }

        val inspector = mockk<ArchiveInspector>()
        every { inspector.entries() } returns entries.asSequence()
        coEvery { inspector.countEntries() } returns entries.size
        every { inspector.getArchiveType() } returns ArchiveType.ZIP
        every { inspector.close() } returns Unit

        val archivePath = "/test/archive.zip"
        val browser = ArchiveBrowser(inspector, archivePath)

        // Act - Browse page 2 multiple times
        val firstVisit = browser.browse(entryPath = "", offset = 100, limit = 100)
        val secondVisit = browser.browse(entryPath = "", offset = 100, limit = 100)
        val thirdVisit = browser.browse(entryPath = "", offset = 100, limit = 100)

        // Assert - All visits should return same results
        val firstItems = (firstVisit as BrowseResult.Complete).items
        val secondItems = (secondVisit as BrowseResult.Complete).items
        val thirdItems = (thirdVisit as BrowseResult.Complete).items

        assertEquals("All visits should return same count", firstItems.size, secondItems.size)
        assertEquals("All visits should return same count", firstItems.size, thirdItems.size)

        assertEquals("First item should be consistent", firstItems.first().name, secondItems.first().name)
        assertEquals("First item should be consistent", firstItems.first().name, thirdItems.first().name)

        assertEquals("Last item should be consistent", firstItems.last().name, secondItems.last().name)
        assertEquals("Last item should be consistent", firstItems.last().name, thirdItems.last().name)
    }
}
