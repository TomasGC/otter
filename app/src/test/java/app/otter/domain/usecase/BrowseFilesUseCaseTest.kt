package app.otter.domain.usecase

import app.otter.domain.model.FileItem
import app.otter.domain.model.ResourcePath
import app.otter.domain.repository.FileBrowserRepository
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

class BrowseFilesUseCaseTest {

    private lateinit var repository: FileBrowserRepository
    private lateinit var useCase: BrowseFilesUseCase

    @Before
    fun setup() {
        repository = mockk()
        useCase = BrowseFilesUseCase(repository)
    }

    @Test
    fun `invoke should delegate to repository`() = runTest {
        // Given
        val path = ResourcePath.from("file:///storage")
        val files = listOf(
            createFileItem("file.txt", isArchive = false),
            createFileItem("folder", isDirectory = true)
        )
        coEvery { repository.listFiles(path) } returns Result.success(files)

        // When
        val result = useCase(path)

        // Then
        assertTrue(result.isSuccess)
        coVerify { repository.listFiles(path) }
    }

    @Test
    fun `invoke should sort archives first then directories then files`() = runTest {
        // Given
        val path = ResourcePath.from("file:///storage")
        val files = listOf(
            createFileItem("file.txt", isArchive = false, isDirectory = false),
            createFileItem("folder", isDirectory = true),
            createFileItem("archive.zip", isArchive = true),
            createFileItem("document.pdf", isArchive = false, isDirectory = false)
        )
        coEvery { repository.listFiles(path) } returns Result.success(files)

        // When
        val result = useCase(path)

        // Then
        val sorted = result.getOrNull()!!
        assertEquals("archive.zip", sorted[0].name) // Archive first
        assertEquals("folder", sorted[1].name)       // Directory second
        assertEquals("document.pdf", sorted[2].name) // Files after
        assertEquals("file.txt", sorted[3].name)
    }

    @Test
    fun `invoke should sort alphabetically within same category`() = runTest {
        // Given
        val path = ResourcePath.from("file:///storage")
        val files = listOf(
            createFileItem("zebra.zip", isArchive = true),
            createFileItem("apple.zip", isArchive = true),
            createFileItem("zoo", isDirectory = true),
            createFileItem("archive", isDirectory = true),
            createFileItem("zebra.txt", isArchive = false),
            createFileItem("apple.txt", isArchive = false)
        )
        coEvery { repository.listFiles(path) } returns Result.success(files)

        // When
        val result = useCase(path)

        // Then
        val sorted = result.getOrNull()!!
        // Archives: apple.zip, zebra.zip
        assertEquals("apple.zip", sorted[0].name)
        assertEquals("zebra.zip", sorted[1].name)
        // Directories: archive, zoo
        assertEquals("archive", sorted[2].name)
        assertEquals("zoo", sorted[3].name)
        // Files: apple.txt, zebra.txt
        assertEquals("apple.txt", sorted[4].name)
        assertEquals("zebra.txt", sorted[5].name)
    }

    @Test
    fun `invoke should be case insensitive when sorting`() = runTest {
        // Given
        val path = ResourcePath.from("file:///storage")
        val files = listOf(
            createFileItem("Zebra.txt"),
            createFileItem("apple.txt"),
            createFileItem("BANANA.txt")
        )
        coEvery { repository.listFiles(path) } returns Result.success(files)

        // When
        val result = useCase(path)

        // Then
        val sorted = result.getOrNull()!!
        assertEquals("apple.txt", sorted[0].name)
        assertEquals("BANANA.txt", sorted[1].name)
        assertEquals("Zebra.txt", sorted[2].name)
    }

    @Test
    fun `invoke should return empty list when repository returns empty`() = runTest {
        // Given
        val path = ResourcePath.from("file:///storage")
        coEvery { repository.listFiles(path) } returns Result.success(emptyList())

        // When
        val result = useCase(path)

        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
    }

    @Test
    fun `invoke should propagate repository failure`() = runTest {
        // Given
        val path = ResourcePath.from("file:///storage")
        val exception = SecurityException("Permission denied")
        coEvery { repository.listFiles(path) } returns Result.failure(exception)

        // When
        val result = useCase(path)

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `getParent should delegate to repository`() {
        // Given
        val currentPath = ResourcePath.from("file:///storage/downloads")
        val parentPath = ResourcePath.from("file:///storage")
        every { repository.getParent(currentPath) } returns parentPath

        // When
        val result = useCase.getParent(currentPath)

        // Then
        assertEquals(parentPath, result)
        verify { repository.getParent(currentPath) }
    }

    @Test
    fun `getParent should return null when at root`() {
        // Given
        val rootPath = ResourcePath.from("file:///")
        every { repository.getParent(rootPath) } returns null

        // When
        val result = useCase.getParent(rootPath)

        // Then
        assertNull(result)
    }

    @Test
    fun `isRoot should delegate to repository`() {
        // Given
        val path = ResourcePath.from("file:///storage")
        every { repository.isRoot(path) } returns false

        // When
        val result = useCase.isRoot(path)

        // Then
        assertFalse(result)
        verify { repository.isRoot(path) }
    }

    @Test
    fun `isRoot should return true for root directory`() {
        // Given
        val rootPath = ResourcePath.from("file:///")
        every { repository.isRoot(rootPath) } returns true

        // When
        val result = useCase.isRoot(rootPath)

        // Then
        assertTrue(result)
    }

    // Helper to create test FileItem
    private fun createFileItem(
        name: String,
        isDirectory: Boolean = false,
        isArchive: Boolean = false
    ): FileItem {
        return FileItem(
            path = ResourcePath.from("file:///$name"),
            name = name,
            isDirectory = isDirectory,
            sizeBytes = if (isDirectory) null else 1024L,
            lastModified = System.currentTimeMillis(),
            mimeType = when {
                isArchive -> "application/zip"
                isDirectory -> null
                else -> "text/plain"
            }
        )
    }
}
