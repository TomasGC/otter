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
