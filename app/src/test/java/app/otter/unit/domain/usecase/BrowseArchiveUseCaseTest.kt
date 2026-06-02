package app.otter.domain.usecase

import app.otter.domain.model.ArchiveEntry
import app.otter.domain.model.ResourcePath
import app.otter.domain.repository.ArchiveBrowserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BrowseArchiveUseCaseTest {

    private lateinit var repository: ArchiveBrowserRepository
    private lateinit var useCase: BrowseArchiveUseCase

    @Before
    fun setup() {
        repository = mockk()
        useCase = BrowseArchiveUseCase(repository)
    }

    @Test
    fun `invoke should delegate to repository`() = runTest {
        // Given
        val archivePath = ResourcePath.FileSystem("file:///test.zip")
        val path = "folder"
        val timestamp = System.currentTimeMillis()
        val entries = listOf(
            ArchiveEntry("folder/file.txt", false, 100, 80, timestamp),
            ArchiveEntry("folder/sub", true, 0, 0, timestamp)
        )
        coEvery { repository.listEntries(archivePath, path) } returns Result.success(entries)

        // When
        val result = useCase(archivePath, path)

        // Then
        assertTrue(result.isSuccess)
        coVerify { repository.listEntries(archivePath, path) }
    }

    @Test
    fun `invoke should sort directories before files`() = runTest {
        // Given
        val archivePath = ResourcePath.FileSystem("file:///test.zip")
        val timestamp = System.currentTimeMillis()
        val entries = listOf(
            ArchiveEntry("file1.txt", false, 100, 80, timestamp),
            ArchiveEntry("dir1", true, 0, 0, timestamp),
            ArchiveEntry("file2.txt", false, 200, 150, timestamp),
            ArchiveEntry("dir2", true, 0, 0, timestamp)
        )
        coEvery { repository.listEntries(archivePath, "") } returns Result.success(entries)

        // When
        val result = useCase(archivePath, "")

        // Then
        assertTrue(result.isSuccess)
        val sorted = result.getOrNull()!!
        assertEquals(4, sorted.size)
        // Directories first
        assertTrue(sorted[0].isDirectory)
        assertTrue(sorted[1].isDirectory)
        // Files after
        assertTrue(!sorted[2].isDirectory)
        assertTrue(!sorted[3].isDirectory)
    }

    @Test
    fun `invoke should sort alphabetically within same type`() = runTest {
        // Given
        val archivePath = ResourcePath.FileSystem("file:///test.zip")
        val timestamp = System.currentTimeMillis()
        val entries = listOf(
            ArchiveEntry("zebra.txt", false, 100, 80, timestamp),
            ArchiveEntry("apple.txt", false, 100, 80, timestamp),
            ArchiveEntry("zoo", true, 0, 0, timestamp),
            ArchiveEntry("archive", true, 0, 0, timestamp)
        )
        coEvery { repository.listEntries(archivePath, "") } returns Result.success(entries)

        // When
        val result = useCase(archivePath, "")

        // Then
        val sorted = result.getOrNull()!!
        // Directories: archive, zoo
        assertEquals("archive", sorted[0].path)
        assertEquals("zoo", sorted[1].path)
        // Files: apple.txt, zebra.txt
        assertEquals("apple.txt", sorted[2].path)
        assertEquals("zebra.txt", sorted[3].path)
    }

    @Test
    fun `invoke should be case insensitive when sorting`() = runTest {
        // Given
        val archivePath = ResourcePath.FileSystem("file:///test.zip")
        val timestamp = System.currentTimeMillis()
        val entries = listOf(
            ArchiveEntry("Zebra.txt", false, 100, 80, timestamp),
            ArchiveEntry("apple.txt", false, 100, 80, timestamp),
            ArchiveEntry("BANANA.txt", false, 100, 80, timestamp)
        )
        coEvery { repository.listEntries(archivePath, "") } returns Result.success(entries)

        // When
        val result = useCase(archivePath, "")

        // Then
        val sorted = result.getOrNull()!!
        assertEquals("apple.txt", sorted[0].path)
        assertEquals("BANANA.txt", sorted[1].path)
        assertEquals("Zebra.txt", sorted[2].path)
    }

    @Test
    fun `invoke should return empty list when repository returns empty`() = runTest {
        // Given
        val archivePath = ResourcePath.FileSystem("file:///test.zip")
        coEvery { repository.listEntries(archivePath, "") } returns Result.success(emptyList())

        // When
        val result = useCase(archivePath, "")

        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
    }

    @Test
    fun `invoke should propagate repository failure`() = runTest {
        // Given
        val archivePath = ResourcePath.FileSystem("file:///test.zip")
        val exception = IllegalArgumentException("Archive not found")
        coEvery { repository.listEntries(archivePath, "") } returns Result.failure(exception)

        // When
        val result = useCase(archivePath, "")

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `invoke should use empty string as default path`() = runTest {
        // Given
        val archivePath = ResourcePath.FileSystem("file:///test.zip")
        coEvery { repository.listEntries(archivePath, "") } returns Result.success(emptyList())

        // When
        useCase(archivePath)

        // Then
        coVerify { repository.listEntries(archivePath, "") }
    }
}
