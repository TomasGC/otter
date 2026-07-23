package app.otter.data.browser

import app.otter.domain.inspector.ArchiveEntry
import app.otter.domain.inspector.ArchiveInspector
import app.otter.domain.model.BrowseResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveBrowserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `browse small archive returns Complete result`() = runTest {
        // Arrange - Archive with <10k entries
        val inspector = mockk<ArchiveInspector>()
        coEvery { inspector.countEntries() } returns 5
        every { inspector.entries() } returns sequenceOf(
            ArchiveEntry("file1.txt", false, 100, 50, 1000L),
            ArchiveEntry("file2.txt", false, 200, 100, 2000L),
            ArchiveEntry("dir/", true, 0, 0, 3000L),
            ArchiveEntry("dir/file3.txt", false, 300, 150, 4000L),
            ArchiveEntry("file4.txt", false, 400, 200, 5000L)
        )

        val browser = ArchiveBrowser(inspector, "/path/to/archive.zip")

        // Act - Browse root (entryPath = "")
        val result = browser.browse(entryPath = "", offset = 0, limit = 100)

        // Assert - Returns Complete result with root-level items only
        assertTrue(result is BrowseResult.Complete)
        assertEquals(4, result.items.size) // file1.txt, file2.txt, dir/, file4.txt (excludes dir/file3.txt)
    }

    @Test
    fun `browse large archive returns Paginated result`() = runTest {
        // Arrange - Archive with ≥10k entries
        val inspector = mockk<ArchiveInspector>()
        coEvery { inspector.countEntries() } returns 10_000
        every { inspector.entries() } returns (0 until 10_000).asSequence().map {
            ArchiveEntry("file$it.txt", false, 100, 50, 1000L)
        }

        val browser = ArchiveBrowser(inspector, "/path/to/archive.zip")

        // Act - Request first page (100 items)
        val result = browser.browse(entryPath = "", offset = 0, limit = 100)

        // Assert
        assertTrue(result is BrowseResult.Paginated)
        val paginated = result as BrowseResult.Paginated
        assertEquals(100, paginated.items.size)
        assertTrue(paginated.hasMore)
        assertEquals(10_000, paginated.totalEstimate)
        assertEquals(100, paginated.nextOffset)
    }

    @Test
    fun `browse root directory filters root-level entries only`() = runTest {
        // Arrange
        val inspector = mockk<ArchiveInspector>()
        coEvery { inspector.countEntries() } returns 6
        every { inspector.entries() } returns sequenceOf(
            ArchiveEntry("file1.txt", false, 100, 50, 1000L),
            ArchiveEntry("dir/", true, 0, 0, 2000L),
            ArchiveEntry("dir/file2.txt", false, 200, 100, 3000L),
            ArchiveEntry("dir/subdir/", true, 0, 0, 4000L),
            ArchiveEntry("dir/subdir/file3.txt", false, 300, 150, 5000L),
            ArchiveEntry("file4.txt", false, 400, 200, 6000L)
        )

        val browser = ArchiveBrowser(inspector, "/path/to/archive.zip")

        // Act - Browse root (entryPath = "")
        val result = browser.browse(entryPath = "", offset = 0, limit = 100)

        // Assert - Only root-level items
        assertEquals(3, result.items.size)
        assertTrue(result.items.any { it.name == "file1.txt" })
        assertTrue(result.items.any { it.name == "dir" })
        assertTrue(result.items.any { it.name == "file4.txt" })
    }

    @Test
    fun `browse subdirectory filters entries in that directory only`() = runTest {
        // Arrange
        val inspector = mockk<ArchiveInspector>()
        coEvery { inspector.countEntries() } returns 6
        every { inspector.entries() } returns sequenceOf(
            ArchiveEntry("file1.txt", false, 100, 50, 1000L),
            ArchiveEntry("dir/", true, 0, 0, 2000L),
            ArchiveEntry("dir/file2.txt", false, 200, 100, 3000L),
            ArchiveEntry("dir/subdir/", true, 0, 0, 4000L),
            ArchiveEntry("dir/subdir/file3.txt", false, 300, 150, 5000L),
            ArchiveEntry("file4.txt", false, 400, 200, 6000L)
        )

        val browser = ArchiveBrowser(inspector, "/path/to/archive.zip")

        // Act - Browse "dir/"
        val result = browser.browse(entryPath = "dir/", offset = 0, limit = 100)

        // Assert - Only items in "dir/"
        assertEquals(2, result.items.size)
        assertTrue(result.items.any { it.name == "file2.txt" })
        assertTrue(result.items.any { it.name == "subdir" })
    }

    @Test
    fun `browse returns directories first then files sorted case-insensitively`() = runTest {
        // Arrange
        val inspector = mockk<ArchiveInspector>()
        coEvery { inspector.countEntries() } returns 6
        every { inspector.entries() } returns sequenceOf(
            ArchiveEntry("zebra.txt", false, 100, 50, 1000L),
            ArchiveEntry("apple.txt", false, 200, 100, 2000L),
            ArchiveEntry("Dir2/", true, 0, 0, 3000L),
            ArchiveEntry("dir1/", true, 0, 0, 4000L),
            ArchiveEntry("banana.txt", false, 300, 150, 5000L),
            ArchiveEntry("Dir3/", true, 0, 0, 6000L)
        )

        val browser = ArchiveBrowser(inspector, "/path/to/archive.zip")

        // Act
        val result = browser.browse(entryPath = "", offset = 0, limit = 100)

        // Assert - Directories first (case-insensitive sort), then files
        assertEquals(6, result.items.size)
        assertEquals("dir1", result.items[0].name)
        assertEquals("Dir2", result.items[1].name)
        assertEquals("Dir3", result.items[2].name)
        assertEquals("apple.txt", result.items[3].name)
        assertEquals("banana.txt", result.items[4].name)
        assertEquals("zebra.txt", result.items[5].name)
    }

    @Test
    fun `browse with pagination offset skips items correctly`() = runTest {
        // Arrange - Large archive
        val inspector = mockk<ArchiveInspector>()
        coEvery { inspector.countEntries() } returns 10_000
        every { inspector.entries() } returns (0 until 10_000).asSequence().map {
            ArchiveEntry("file${it.toString().padStart(5, '0')}.txt", false, 100, 50, 1000L)
        }

        val browser = ArchiveBrowser(inspector, "/path/to/archive.zip")

        // Act - Request page at offset 100
        val result = browser.browse(entryPath = "", offset = 100, limit = 50)

        // Assert
        assertTrue(result is BrowseResult.Paginated)
        val paginated = result as BrowseResult.Paginated
        assertEquals(50, paginated.items.size)
        assertEquals("file00100.txt", paginated.items.first().name)
        assertEquals("file00149.txt", paginated.items.last().name)
        assertEquals(150, paginated.nextOffset)
    }

    @Test
    fun `browse last page sets hasMore to false`() = runTest {
        // Arrange - Large archive
        val inspector = mockk<ArchiveInspector>()
        coEvery { inspector.countEntries() } returns 10_000
        every { inspector.entries() } returns (0 until 10_000).asSequence().map {
            ArchiveEntry("file$it.txt", false, 100, 50, 1000L)
        }

        val browser = ArchiveBrowser(inspector, "/path/to/archive.zip")

        // Act - Request last page (offset 9900, limit 100)
        val result = browser.browse(entryPath = "", offset = 9900, limit = 100)

        // Assert - Last page returns Complete (not Paginated)
        assertTrue(result is BrowseResult.Complete)
        val complete = result as BrowseResult.Complete
        assertEquals(100, complete.items.size)
    }

    @Test
    fun `browse handles trailing slash in entryPath`() = runTest {
        // Arrange
        val inspector = mockk<ArchiveInspector>()
        coEvery { inspector.countEntries() } returns 2
        every { inspector.entries() } returns sequenceOf(
            ArchiveEntry("dir/file1.txt", false, 100, 50, 1000L),
            ArchiveEntry("dir/file2.txt", false, 200, 100, 2000L)
        )

        val browser = ArchiveBrowser(inspector, "/path/to/archive.zip")

        // Act - Both "dir" and "dir/" should work
        val resultWithSlash = browser.browse(entryPath = "dir/", offset = 0, limit = 100)
        val resultWithoutSlash = browser.browse(entryPath = "dir", offset = 0, limit = 100)

        // Assert - Both should return same results
        assertEquals(2, resultWithSlash.items.size)
        assertEquals(2, resultWithoutSlash.items.size)
    }

    @Test
    fun `browse handles directories detected by trailing slash`() = runTest {
        // Arrange - Some entries don't have explicit directory flag but end with /
        val inspector = mockk<ArchiveInspector>()
        coEvery { inspector.countEntries() } returns 3
        every { inspector.entries() } returns sequenceOf(
            ArchiveEntry("dir1/", false, 0, 0, 1000L), // isDirectory = false but ends with /
            ArchiveEntry("dir2/", true, 0, 0, 2000L),  // isDirectory = true
            ArchiveEntry("file.txt", false, 100, 50, 3000L)
        )

        val browser = ArchiveBrowser(inspector, "/path/to/archive.zip")

        // Act
        val result = browser.browse(entryPath = "", offset = 0, limit = 100)

        // Assert - Both dir1 and dir2 should be treated as directories
        assertEquals(3, result.items.size)
        assertEquals("dir1", result.items[0].name)
        assertEquals("dir2", result.items[1].name)
        assertEquals("file.txt", result.items[2].name)
    }

    @Test
    fun `browse empty archive returns Complete with empty list`() = runTest {
        // Arrange
        val inspector = mockk<ArchiveInspector>()
        coEvery { inspector.countEntries() } returns 0
        every { inspector.entries() } returns emptySequence()

        val browser = ArchiveBrowser(inspector, "/path/to/archive.zip")

        // Act
        val result = browser.browse(entryPath = "", offset = 0, limit = 100)

        // Assert
        assertTrue(result is BrowseResult.Complete)
        assertEquals(0, result.items.size)
    }

    @Test
    fun `browse non-existent directory returns Complete with empty list`() = runTest {
        // Arrange
        val inspector = mockk<ArchiveInspector>()
        coEvery { inspector.countEntries() } returns 2
        every { inspector.entries() } returns sequenceOf(
            ArchiveEntry("dir/file1.txt", false, 100, 50, 1000L),
            ArchiveEntry("dir/file2.txt", false, 200, 100, 2000L)
        )

        val browser = ArchiveBrowser(inspector, "/path/to/archive.zip")

        // Act - Browse non-existent "other/" directory
        val result = browser.browse(entryPath = "other/", offset = 0, limit = 100)

        // Assert
        assertTrue(result is BrowseResult.Complete)
        assertEquals(0, result.items.size)
    }

    @Test
    fun `browse with real ZIP file`() = runTest {
        // Arrange - Create real ZIP file
        val zipFile = createTestZip(mapOf(
            "file1.txt" to "content1",
            "dir/" to "",
            "dir/file2.txt" to "content2",
            "dir/subdir/" to "",
            "dir/subdir/file3.txt" to "content3",
            "file4.txt" to "content4"
        ))

        val inspector = ZipInspectorForTest(zipFile)
        val browser = ArchiveBrowser(inspector, "/path/to/archive.zip")

        // Act - Browse root
        val result = browser.browse(entryPath = "", offset = 0, limit = 100)

        // Assert
        assertTrue(result is BrowseResult.Complete)
        assertEquals(3, result.items.size) // file1.txt, dir, file4.txt
        assertTrue(result.items.any { it.name == "file1.txt" })
        assertTrue(result.items.any { it.name == "dir" })
        assertTrue(result.items.any { it.name == "file4.txt" })

        inspector.close()
    }

    @Test
    fun `browse deduplicates entries with identical paths`() = runTest {
        // Two inspector entries with the same path must result in one BrowsableItem.
        // ArchiveBrowser uses distinctBy { it.path } to prevent duplicates.
        val inspector = mockk<ArchiveInspector>()
        coEvery { inspector.countEntries() } returns 2
        every { inspector.entries() } returns sequenceOf(
            ArchiveEntry("duplicate.txt", false, 100, 50, 1000L),
            ArchiveEntry("duplicate.txt", false, 200, 100, 2000L) // same path
        )
        val browser = ArchiveBrowser(inspector, "/path/to/archive.zip")

        val result = browser.browse(entryPath = "", offset = 0, limit = 100)

        assertTrue(result is BrowseResult.Complete)
        val items = (result as BrowseResult.Complete).items
        assertEquals("Duplicate entries must be collapsed to 1 item", 1, items.size)
        assertEquals("duplicate.txt", items.first().name)
    }

    @Test
    fun `browse deduplicates entries across explicit and implicit directories`() = runTest {
        // An explicit dir entry + an implicit dir (inferred from file path) → one dir in result.
        val inspector = mockk<ArchiveInspector>()
        coEvery { inspector.countEntries() } returns 2
        every { inspector.entries() } returns sequenceOf(
            ArchiveEntry("subdir/", true, 0, 0, 0L),          // explicit dir
            ArchiveEntry("subdir/file.txt", false, 100, 50, 1000L) // implicit parent is "subdir/"
        )
        val browser = ArchiveBrowser(inspector, "/path/to/archive.zip")

        val result = browser.browse(entryPath = "", offset = 0, limit = 100)

        assertTrue(result is BrowseResult.Complete)
        val items = (result as BrowseResult.Complete).items
        // Root contains: "subdir/" directory only (file.txt is inside subdir/)
        val dirs = items.filterIsInstance<app.otter.domain.model.BrowsableItem.ArchiveDirectory>()
        assertEquals("Explicit + implicit 'subdir/' must collapse to 1 directory", 1, dirs.size)
    }

    @Test
    fun `browse same directory twice reuses cached entries instead of re-streaming the inspector`() = runTest {
        // Arrange - Large archive; each browse() call would otherwise re-stream all entries
        val inspector = mockk<ArchiveInspector>()
        coEvery { inspector.countEntries() } returns 10_000
        every { inspector.entries() } returns (0 until 10_000).asSequence().map {
            ArchiveEntry("file${it.toString().padStart(5, '0')}.txt", false, 100, 50, 1000L)
        }

        val browser = ArchiveBrowser(inspector, "/path/to/archive.zip")

        // Act - Two paginated calls into the SAME directory (simulates scrolling through it)
        browser.browse(entryPath = "", offset = 0, limit = 100)
        browser.browse(entryPath = "", offset = 100, limit = 100)

        // Assert - The expensive entries() stream must only run once, not once per page
        verify(exactly = 1) { inspector.entries() }
    }

    @Test
    fun `browse different directories on the same browser each cache independently`() = runTest {
        // Arrange
        val inspector = mockk<ArchiveInspector>()
        coEvery { inspector.countEntries() } returns 4
        every { inspector.entries() } returns sequenceOf(
            ArchiveEntry("dirA/file1.txt", false, 100, 50, 1000L),
            ArchiveEntry("dirB/file2.txt", false, 200, 100, 2000L)
        )

        val browser = ArchiveBrowser(inspector, "/path/to/archive.zip")

        // Act - Browse two different directories, then re-browse the first again
        val firstA = browser.browse(entryPath = "dirA/", offset = 0, limit = 100)
        browser.browse(entryPath = "dirB/", offset = 0, limit = 100)
        val secondA = browser.browse(entryPath = "dirA/", offset = 0, limit = 100)

        // Assert - Underlying stream still only read once across all three calls
        verify(exactly = 1) { inspector.entries() }
        assertEquals(1, firstA.items.size)
        assertEquals("file1.txt", firstA.items.first().name)
        assertEquals(secondA.items.map { it.name }, firstA.items.map { it.name })
    }

    @Test
    fun `browse from many concurrent coroutines on the same instance does not duplicate the entries stream`() = runTest {
        // ItemBrowserRepositoryImpl keeps one ArchiveBrowser alive per archive and can dispatch
        // concurrent scroll-prefetch calls into it. directoryCache/cachedRawEntries must survive
        // that without corrupting state or re-streaming the inspector per racing caller.
        val inspector = mockk<ArchiveInspector>()
        coEvery { inspector.countEntries() } returns 10_000
        every { inspector.entries() } answers {
            Thread.sleep(20) // widen the race window
            (0 until 10_000).asSequence().map { ArchiveEntry("file$it.txt", false, 100, 50, 1000L) }
        }

        val browser = ArchiveBrowser(inspector, "/path/to/archive.zip")

        coroutineScope {
            repeat(20) { i ->
                launch(Dispatchers.Default) {
                    browser.browse(entryPath = "", offset = i * 10, limit = 10)
                }
            }
        }

        verify(exactly = 1) { inspector.entries() }
    }

    private fun createTestZip(files: Map<String, String>): File {
        val zipFile = tempFolder.newFile("test.zip")
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return zipFile
    }

    /**
     * Minimal ZipInspector implementation for testing.
     * Uses the real ZipInspector from production code.
     */
    private class ZipInspectorForTest(private val file: File) : ArchiveInspector by app.otter.data.inspector.ZipInspector(file) {
        // Delegate all methods to real ZipInspector
    }
}
