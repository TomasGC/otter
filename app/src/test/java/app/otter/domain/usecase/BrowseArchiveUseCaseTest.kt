package app.otter.domain.usecase

import android.net.Uri
import app.otter.domain.model.ArchiveEntry
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
        val archiveUri = Uri.parse("file:///test.zip")
        val path = "folder"
        val entries = listOf(
            ArchiveEntry("folder/file.txt", "file.txt", false, 100, 80, System.currentTimeMillis()),
            ArchiveEntry("folder/sub", "sub", true, null, null, System.currentTimeMillis())
        )
        coEvery { repository.listEntries(archiveUri, path) } returns Result.success(entries)

        // When
        val result = useCase(archiveUri, path)

        // Then
        assertTrue(result.isSuccess)
        coVerify { repository.listEntries(archiveUri, path) }
    }

    @Test
    fun `invoke should sort directories before files`() = runTest {
        // Given
        val archiveUri = Uri.parse("file:///test.zip")
        val entries = listOf(
            ArchiveEntry("file1.txt", "file1.txt", false, 100, 80, System.currentTimeMillis()),
            ArchiveEntry("dir1", "dir1", true, null, null, System.currentTimeMillis()),
            ArchiveEntry("file2.txt", "file2.txt", false, 200, 150, System.currentTimeMillis()),
            ArchiveEntry("dir2", "dir2", true, null, null, System.currentTimeMillis())
        )
        coEvery { repository.listEntries(archiveUri, "") } returns Result.success(entries)

        // When
        val result = useCase(archiveUri, "")

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
        val archiveUri = Uri.parse("file:///test.zip")
        val entries = listOf(
            ArchiveEntry("zebra.txt", "zebra.txt", false, 100, 80, System.currentTimeMillis()),
            ArchiveEntry("apple.txt", "apple.txt", false, 100, 80, System.currentTimeMillis()),
            ArchiveEntry("zoo", "zoo", true, null, null, System.currentTimeMillis()),
            ArchiveEntry("archive", "archive", true, null, null, System.currentTimeMillis())
        )
        coEvery { repository.listEntries(archiveUri, "") } returns Result.success(entries)

        // When
        val result = useCase(archiveUri, "")

        // Then
        val sorted = result.getOrNull()!!
        // Directories: archive, zoo
        assertEquals("archive", sorted[0].name)
        assertEquals("zoo", sorted[1].name)
        // Files: apple.txt, zebra.txt
        assertEquals("apple.txt", sorted[2].name)
        assertEquals("zebra.txt", sorted[3].name)
    }

    @Test
    fun `invoke should be case insensitive when sorting`() = runTest {
        // Given
        val archiveUri = Uri.parse("file:///test.zip")
        val entries = listOf(
            ArchiveEntry("Zebra.txt", "Zebra.txt", false, 100, 80, System.currentTimeMillis()),
            ArchiveEntry("apple.txt", "apple.txt", false, 100, 80, System.currentTimeMillis()),
            ArchiveEntry("BANANA.txt", "BANANA.txt", false, 100, 80, System.currentTimeMillis())
        )
        coEvery { repository.listEntries(archiveUri, "") } returns Result.success(entries)

        // When
        val result = useCase(archiveUri, "")

        // Then
        val sorted = result.getOrNull()!!
        assertEquals("apple.txt", sorted[0].name)
        assertEquals("BANANA.txt", sorted[1].name)
        assertEquals("Zebra.txt", sorted[2].name)
    }

    @Test
    fun `invoke should return empty list when repository returns empty`() = runTest {
        // Given
        val archiveUri = Uri.parse("file:///test.zip")
        coEvery { repository.listEntries(archiveUri, "") } returns Result.success(emptyList())

        // When
        val result = useCase(archiveUri, "")

        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
    }

    @Test
    fun `invoke should propagate repository failure`() = runTest {
        // Given
        val archiveUri = Uri.parse("file:///test.zip")
        val exception = IllegalArgumentException("Archive not found")
        coEvery { repository.listEntries(archiveUri, "") } returns Result.failure(exception)

        // When
        val result = useCase(archiveUri, "")

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `invoke should use empty string as default path`() = runTest {
        // Given
        val archiveUri = Uri.parse("file:///test.zip")
        coEvery { repository.listEntries(archiveUri, "") } returns Result.success(emptyList())

        // When
        useCase(archiveUri)

        // Then
        coVerify { repository.listEntries(archiveUri, "") }
    }
}
