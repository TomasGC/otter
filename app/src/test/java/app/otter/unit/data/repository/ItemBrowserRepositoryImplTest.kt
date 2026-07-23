package app.otter.data.repository

import app.otter.data.browser.FileSystemBrowser
import app.otter.data.inspector.ArchiveInspectorFactory
import app.otter.domain.inspector.ArchiveInspector
import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.BrowseResult
import app.otter.domain.model.ResourcePath
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class ItemBrowserRepositoryImplTest {

    private lateinit var fileSystemBrowser: FileSystemBrowser
    private lateinit var inspectorFactory: ArchiveInspectorFactory
    private lateinit var repository: ItemBrowserRepositoryImpl

    @Before
    fun setup() {
        fileSystemBrowser = mockk()
        inspectorFactory = mockk()
        repository = ItemBrowserRepositoryImpl(fileSystemBrowser, inspectorFactory)
    }

    // ============================
    // browse() - FileSystem tests
    // ============================

    @Test
    fun `browse delegates to FileSystemBrowser for FileSystem path`() = runTest {
        // Given
        val path = ResourcePath.FileSystem("/storage/emulated/0/Documents")
        val expectedItems = listOf(
            BrowsableItem.FileSystemDirectory(
                path = ResourcePath.FileSystem("/storage/emulated/0/Documents/folder"),
                name = "folder",
                sizeBytes = 0L,
                lastModified = 1234567890L
            )
        )
        val expectedResult = BrowseResult.Complete(expectedItems)

        coEvery { fileSystemBrowser.browse(path) } returns Result.success(expectedResult)

        // When
        val result = repository.browse(path, offset = 0, limit = 100)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedResult, result.getOrNull())
        coVerify(exactly = 1) { fileSystemBrowser.browse(path) }
    }

    @Test
    fun `browse returns failure when FileSystemBrowser fails`() = runTest {
        // Given
        val path = ResourcePath.FileSystem("/storage/invalid")
        val expectedException = IllegalArgumentException("Directory does not exist")

        coEvery { fileSystemBrowser.browse(path) } returns Result.failure(expectedException)

        // When
        val result = repository.browse(path, offset = 0, limit = 100)

        // Then
        assertTrue(result.isFailure)
        assertEquals(expectedException, result.exceptionOrNull())
    }

    // ============================
    // browse() - ArchiveEntry tests
    // ============================

    @Test
    fun `browse creates ArchiveBrowser and delegates for ArchiveEntry path`() = runTest {
        // Given
        val path = ResourcePath.ArchiveEntry(
            archivePath = "/storage/emulated/0/archive.zip",
            entryPath = "folder/"
        )
        val mockInspector = mockk<ArchiveInspector>()

        every { inspectorFactory.create(any<File>()) } returns Result.success(mockInspector)
        coEvery { mockInspector.countEntries() } returns 15000
        every { mockInspector.entries() } returns listOf(
            app.otter.domain.inspector.ArchiveEntry(
                path = "folder/file.txt",
                isDirectory = false,
                sizeBytes = 1024L,
                compressedSize = 512L,
                lastModified = 1234567890L
            )
        ).asSequence()

        // When
        val result = repository.browse(path, offset = 0, limit = 100)

        // Then
        assertTrue(result.isSuccess)
        val browseResult = result.getOrNull()
        // Only 1 item in folder/ after filtering, so Complete (last page) not Paginated
        assertTrue(browseResult is BrowseResult.Complete)
        assertEquals(1, browseResult!!.items.size)
        verify(exactly = 1) { inspectorFactory.create(File("/storage/emulated/0/archive.zip")) }
    }

    @Test
    fun `browse passes offset and limit to ArchiveBrowser`() = runTest {
        // Given
        val path = ResourcePath.ArchiveEntry(
            archivePath = "/storage/emulated/0/large.zip",
            entryPath = ""
        )
        val mockInspector = mockk<ArchiveInspector>()

        every { inspectorFactory.create(any<File>()) } returns Result.success(mockInspector)
        coEvery { mockInspector.countEntries() } returns 20000
        every { mockInspector.entries() } returns emptyList<app.otter.domain.inspector.ArchiveEntry>().asSequence()

        // When
        val result = repository.browse(path, offset = 400, limit = 100)

        // Then
        assertTrue(result.isSuccess)
        verify(exactly = 1) { inspectorFactory.create(File("/storage/emulated/0/large.zip")) }
    }

    @Test
    fun `browse reuses cached ArchiveBrowser across repeated calls to the same archive`() = runTest {
        // Given - two browse() calls into the same archive, different entryPath/offset
        // (simulates a scroll session: repository must not recreate the inspector every call)
        val archivePath = "/storage/emulated/0/large.zip"
        val mockInspector = mockk<ArchiveInspector>()

        every { inspectorFactory.create(File(archivePath)) } returns Result.success(mockInspector)
        coEvery { mockInspector.countEntries() } returns 20000
        every { mockInspector.entries() } returns (0 until 100).asSequence().map {
            app.otter.domain.inspector.ArchiveEntry(
                path = "folder/file$it.txt",
                isDirectory = false,
                sizeBytes = 1024L,
                compressedSize = 512L,
                lastModified = 1234567890L
            )
        }

        // When
        val first = repository.browse(ResourcePath.ArchiveEntry(archivePath, "folder/"), offset = 0, limit = 50)
        val second = repository.browse(ResourcePath.ArchiveEntry(archivePath, "folder/"), offset = 50, limit = 50)

        // Then - inspector created once; entries() streamed once (ArchiveBrowser's own cache)
        assertTrue(first.isSuccess)
        assertTrue(second.isSuccess)
        verify(exactly = 1) { inspectorFactory.create(File(archivePath)) }
        verify(exactly = 1) { mockInspector.entries() }
    }

    @Test
    fun `browse from many concurrent coroutines for a brand-new archive creates the inspector exactly once`() = runTest {
        // ConcurrentHashMap.computeIfAbsent guarantees this, but nothing proved it before —
        // many coroutines racing to browse the SAME never-seen archivePath must share one
        // ArchiveBrowser/inspector instead of each creating their own.
        val archivePath = "/storage/emulated/0/concurrent.zip"
        val mockInspector = mockk<ArchiveInspector>()

        every { inspectorFactory.create(File(archivePath)) } answers {
            Thread.sleep(20) // widen the race window
            Result.success(mockInspector)
        }
        coEvery { mockInspector.countEntries() } returns 5
        every { mockInspector.entries() } returns emptyList<app.otter.domain.inspector.ArchiveEntry>().asSequence()

        val path = ResourcePath.ArchiveEntry(archivePath, "")

        coroutineScope {
            repeat(20) {
                launch(Dispatchers.Default) {
                    repository.browse(path, offset = 0, limit = 100)
                }
            }
        }

        verify(exactly = 1) { inspectorFactory.create(File(archivePath)) }
    }

    @Test
    fun `browserCache evicts the oldest archive once the bound is exceeded`() = runTest {
        // browserCache is a Singleton-scoped, app-lifetime cache. Without a bound, browsing
        // many distinct archives across a long session accumulates ArchiveBrowser instances
        // (each holding a full raw-entries snapshot) forever.
        val inspectors = (0..ItemBrowserRepositoryImpl.MAX_CACHED_ARCHIVES).map { i ->
            val archivePath = "/storage/emulated/0/archive_$i.zip"
            val mockInspector = mockk<ArchiveInspector>()
            every { inspectorFactory.create(File(archivePath)) } returns Result.success(mockInspector)
            coEvery { mockInspector.countEntries() } returns 5
            every { mockInspector.entries() } returns emptyList<app.otter.domain.inspector.ArchiveEntry>().asSequence()
            archivePath to mockInspector
        }

        // Browse one more archive than the bound allows — the first one must be evicted.
        inspectors.forEach { (archivePath, _) ->
            repository.browse(ResourcePath.ArchiveEntry(archivePath, ""), offset = 0, limit = 100)
        }

        // Re-browsing the first (now-evicted) archive must recreate its inspector.
        val (firstPath, _) = inspectors.first()
        repository.browse(ResourcePath.ArchiveEntry(firstPath, ""), offset = 0, limit = 100)

        verify(exactly = 2) { inspectorFactory.create(File(firstPath)) }
    }

    @Test
    fun `browse returns cached results for the same archivePath even if the underlying file changes`() = runTest {
        // Documents the accepted tradeoff (see ArchiveBrowser and ItemBrowserRepositoryImpl
        // KDoc): archives are assumed read-only for this repository's lifetime. If the file is
        // replaced on disk mid-session, callers get the STALE cached listing, not the new
        // content, until the process restarts. This locks that contract in as an explicit,
        // intentional test rather than an undocumented gap.
        val archivePath = "/storage/emulated/0/mutable.zip"
        val originalInspector = mockk<ArchiveInspector>()
        every { inspectorFactory.create(File(archivePath)) } returns Result.success(originalInspector)
        coEvery { originalInspector.countEntries() } returns 1
        every { originalInspector.entries() } returns listOf(
            app.otter.domain.inspector.ArchiveEntry("original.txt", false, 10, 5, 0L)
        ).asSequence()

        val firstResult = repository.browse(ResourcePath.ArchiveEntry(archivePath, ""), offset = 0, limit = 100)
        assertEquals("original.txt", firstResult.getOrNull()!!.items.first().name)

        // Simulate the file being replaced: a fresh factory call would return different content.
        val replacementInspector = mockk<ArchiveInspector>()
        every { inspectorFactory.create(File(archivePath)) } returns Result.success(replacementInspector)
        coEvery { replacementInspector.countEntries() } returns 1
        every { replacementInspector.entries() } returns listOf(
            app.otter.domain.inspector.ArchiveEntry("replaced.txt", false, 20, 10, 0L)
        ).asSequence()

        // ...but browsing the same archivePath again still returns the cached (stale) listing.
        val secondResult = repository.browse(ResourcePath.ArchiveEntry(archivePath, ""), offset = 0, limit = 100)
        assertEquals("original.txt", secondResult.getOrNull()!!.items.first().name)
        verify(exactly = 1) { inspectorFactory.create(File(archivePath)) }
    }

    @Test
    fun `browse returns failure when inspector creation fails`() = runTest {
        // Given
        val path = ResourcePath.ArchiveEntry(
            archivePath = "/storage/emulated/0/invalid.zip",
            entryPath = ""
        )
        val expectedException = IllegalArgumentException("File does not exist")

        every { inspectorFactory.create(any<File>()) } returns Result.failure(expectedException)

        // When
        val result = repository.browse(path, offset = 0, limit = 100)

        // Then
        assertTrue(result.isFailure)
        assertEquals(expectedException, result.exceptionOrNull())
    }

    // ============================
    // getParent() - FileSystem tests
    // ============================

    @Test
    fun `getParent returns parent directory for FileSystem path`() {
        // Given
        val path = ResourcePath.FileSystem("/storage/emulated/0/Documents")
        val expectedParent = ResourcePath.FileSystem("/storage/emulated/0")

        every { fileSystemBrowser.getParent(path) } returns expectedParent

        // When
        val parent = repository.getParent(path)

        // Then
        assertEquals(expectedParent, parent)
        verify(exactly = 1) { fileSystemBrowser.getParent(path) }
    }

    @Test
    fun `getParent returns null for FileSystem root`() {
        // Given
        val rootPath = ResourcePath.FileSystem("/")

        every { fileSystemBrowser.getParent(rootPath) } returns null

        // When
        val parent = repository.getParent(rootPath)

        // Then
        assertNull(parent)
    }

    // ============================
    // getParent() - ArchiveEntry tests
    // ============================

    @Test
    fun `getParent returns parent directory for ArchiveEntry in subdirectory`() {
        // Given
        val path = ResourcePath.ArchiveEntry(
            archivePath = "/storage/emulated/0/archive.zip",
            entryPath = "folder/subfolder"
        )
        val expectedParent = ResourcePath.ArchiveEntry(
            archivePath = "/storage/emulated/0/archive.zip",
            entryPath = "folder"
        )

        // When
        val parent = repository.getParent(path)

        // Then
        assertEquals(expectedParent, parent)
    }

    @Test
    fun `getParent returns file system path for ArchiveEntry at root`() {
        // Given
        val path = ResourcePath.ArchiveEntry(
            archivePath = "/storage/emulated/0/archive.zip",
            entryPath = ""
        )
        val expectedParent = ResourcePath.FileSystem("/storage/emulated/0/archive.zip")

        // When
        val parent = repository.getParent(path)

        // Then
        assertEquals(expectedParent, parent)
    }

    @Test
    fun `getParent handles ArchiveEntry with nested path correctly`() {
        // Given
        val path = ResourcePath.ArchiveEntry(
            archivePath = "/storage/emulated/0/archive.zip",
            entryPath = "a/b/c/d"
        )
        val expectedParent = ResourcePath.ArchiveEntry(
            archivePath = "/storage/emulated/0/archive.zip",
            entryPath = "a/b/c"
        )

        // When
        val parent = repository.getParent(path)

        // Then
        assertEquals(expectedParent, parent)
    }

    // ============================
    // isRoot() - FileSystem tests
    // ============================

    @Test
    fun `isRoot returns true for FileSystem root path`() {
        // Given
        val rootPath = ResourcePath.FileSystem("/")

        every { fileSystemBrowser.isRoot(rootPath) } returns true

        // When
        val result = repository.isRoot(rootPath)

        // Then
        assertTrue(result)
        verify(exactly = 1) { fileSystemBrowser.isRoot(rootPath) }
    }

    @Test
    fun `isRoot returns false for FileSystem non-root path`() {
        // Given
        val nonRootPath = ResourcePath.FileSystem("/storage/emulated/0")

        every { fileSystemBrowser.isRoot(nonRootPath) } returns false

        // When
        val result = repository.isRoot(nonRootPath)

        // Then
        assertFalse(result)
    }

    // ============================
    // isRoot() - ArchiveEntry tests
    // ============================

    @Test
    fun `isRoot returns false for ArchiveEntry at root`() {
        // Given
        val archiveRootPath = ResourcePath.ArchiveEntry(
            archivePath = "/storage/emulated/0/archive.zip",
            entryPath = ""
        )

        // When
        val result = repository.isRoot(archiveRootPath)

        // Then
        assertFalse(result)
    }

    @Test
    fun `isRoot returns false for ArchiveEntry in subdirectory`() {
        // Given
        val archivePath = ResourcePath.ArchiveEntry(
            archivePath = "/storage/emulated/0/archive.zip",
            entryPath = "folder/subfolder"
        )

        // When
        val result = repository.isRoot(archivePath)

        // Then
        assertFalse(result)
    }

    // ========================================
    // Phase 5: Error Handling Tests (Task #75)
    // ========================================

    @Test
    fun `browse propagates IOException from inspector creation`() = runTest {
        // Given
        val path = ResourcePath.ArchiveEntry(
            archivePath = "/storage/emulated/0/locked.zip",
            entryPath = ""
        )
        val ioException = java.io.IOException("Permission denied")

        every { inspectorFactory.create(any<File>()) } returns Result.failure(ioException)

        // When
        val result = repository.browse(path, offset = 0, limit = 100)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is java.io.IOException)
        assertEquals("Permission denied", result.exceptionOrNull()?.message)
    }

    @Test
    fun `browse propagates IOException from FileSystemBrowser`() = runTest {
        // Given
        val path = ResourcePath.FileSystem("/storage/restricted")
        val ioException = java.io.IOException("Access denied")

        coEvery { fileSystemBrowser.browse(path) } returns Result.failure(ioException)

        // When
        val result = repository.browse(path, offset = 0, limit = 100)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is java.io.IOException)
        assertEquals("Access denied", result.exceptionOrNull()?.message)
    }

    @Test
    fun `browse handles corrupted archive gracefully`() = runTest {
        // Given
        val path = ResourcePath.ArchiveEntry(
            archivePath = "/storage/emulated/0/corrupted.zip",
            entryPath = ""
        )
        val corruptedException = java.util.zip.ZipException("invalid CEN header (bad signature)")

        every { inspectorFactory.create(any<File>()) } returns Result.failure(corruptedException)

        // When
        val result = repository.browse(path, offset = 0, limit = 100)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is java.util.zip.ZipException)
        assertEquals("invalid CEN header (bad signature)", result.exceptionOrNull()?.message)
    }

    @Test
    fun `browse handles FileNotFoundException for non-existent archive`() = runTest {
        // Given
        val path = ResourcePath.ArchiveEntry(
            archivePath = "/storage/emulated/0/nonexistent.zip",
            entryPath = ""
        )
        val fileNotFoundException = java.io.FileNotFoundException("/storage/emulated/0/nonexistent.zip")

        every { inspectorFactory.create(any<File>()) } returns Result.failure(fileNotFoundException)

        // When
        val result = repository.browse(path, offset = 0, limit = 100)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is java.io.FileNotFoundException)
    }

    @Test
    fun `browse handles OutOfMemoryError gracefully`() = runTest {
        // Given
        val path = ResourcePath.ArchiveEntry(
            archivePath = "/storage/emulated/0/huge.zip",
            entryPath = ""
        )
        val oomError = OutOfMemoryError("Failed to allocate buffer")

        every { inspectorFactory.create(any<File>()) } returns Result.failure(oomError)

        // When
        val result = repository.browse(path, offset = 0, limit = 100)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is OutOfMemoryError)
        assertEquals("Failed to allocate buffer", result.exceptionOrNull()?.message)
    }

    @Test
    fun `browse handles inspector throwing exception during entries iteration`() = runTest {
        // Given
        val path = ResourcePath.ArchiveEntry(
            archivePath = "/storage/emulated/0/partial.zip",
            entryPath = ""
        )
        val mockInspector = mockk<ArchiveInspector>()

        every { inspectorFactory.create(any<File>()) } returns Result.success(mockInspector)
        coEvery { mockInspector.countEntries() } returns 100
        every { mockInspector.entries() } throws java.io.IOException("Unexpected end of ZLIB input stream")

        // When
        val result = repository.browse(path, offset = 0, limit = 100)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is java.io.IOException)
        assertEquals("Unexpected end of ZLIB input stream", result.exceptionOrNull()?.message)
    }

    @Test
    fun `browse handles SecurityException for restricted file access`() = runTest {
        // Given
        val path = ResourcePath.ArchiveEntry(
            archivePath = "/system/protected/archive.zip",
            entryPath = ""
        )
        val securityException = SecurityException("Permission denied: /system/protected/archive.zip")

        every { inspectorFactory.create(any<File>()) } returns Result.failure(securityException)

        // When
        val result = repository.browse(path, offset = 0, limit = 100)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun `browse handles null or empty archive path gracefully`() = runTest {
        // Given - Invalid path with empty archive path
        val path = ResourcePath.ArchiveEntry(
            archivePath = "",
            entryPath = "folder"
        )
        val illegalArgumentException = IllegalArgumentException("Archive path cannot be empty")

        every { inspectorFactory.create(any<File>()) } returns Result.failure(illegalArgumentException)

        // When
        val result = repository.browse(path, offset = 0, limit = 100)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `browse returns empty result for archive with no entries matching path`() = runTest {
        // Given
        val path = ResourcePath.ArchiveEntry(
            archivePath = "/storage/emulated/0/archive.zip",
            entryPath = "nonexistent-folder/"
        )
        val mockInspector = mockk<ArchiveInspector>()

        every { inspectorFactory.create(any<File>()) } returns Result.success(mockInspector)
        coEvery { mockInspector.countEntries() } returns 100
        every { mockInspector.entries() } returns listOf(
            app.otter.domain.inspector.ArchiveEntry(
                path = "other-folder/file.txt",
                isDirectory = false,
                sizeBytes = 1024L,
                compressedSize = 512L,
                lastModified = 1234567890L
            )
        ).asSequence()

        // When
        val result = repository.browse(path, offset = 0, limit = 100)

        // Then
        assertTrue(result.isSuccess)
        val browseResult = result.getOrNull()
        assertTrue(browseResult is BrowseResult.Complete)
        assertEquals(0, browseResult!!.items.size)
    }

    @Test
    fun `browse handles concurrent modification during iteration`() = runTest {
        // Given
        val path = ResourcePath.ArchiveEntry(
            archivePath = "/storage/emulated/0/archive.zip",
            entryPath = ""
        )
        val mockInspector = mockk<ArchiveInspector>()

        every { inspectorFactory.create(any<File>()) } returns Result.success(mockInspector)
        coEvery { mockInspector.countEntries() } returns 50
        every { mockInspector.entries() } throws ConcurrentModificationException("Collection was modified during iteration")

        // When
        val result = repository.browse(path, offset = 0, limit = 100)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ConcurrentModificationException)
    }
}
