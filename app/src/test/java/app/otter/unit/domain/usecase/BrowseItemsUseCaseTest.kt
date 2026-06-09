package app.otter.domain.usecase

import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.BrowseResult
import app.otter.domain.model.ResourcePath
import app.otter.domain.repository.ItemBrowserRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseItemsUseCaseTest {

    private val repository: ItemBrowserRepository = mockk(relaxed = true)
    private val useCase = BrowseItemsUseCase(repository)

    @Test
    fun `invoke returns Complete result for small list`() = runTest {
        // Arrange
        val path = ResourcePath.FileSystem("file:///storage/downloads")
        val items = listOf(
            createFileSystemFile("file1.txt"),
            createFileSystemFile("file2.txt")
        )
        val completeResult = BrowseResult.Complete(items)

        coEvery {
            repository.browse(path, 0, BrowseItemsUseCase.PAGINATION_THRESHOLD)
        } returns Result.success(completeResult)

        // Act
        val result = useCase(path, offset = 0, limit = BrowseItemsUseCase.PAGINATION_THRESHOLD)

        // Assert
        assertTrue(result.isSuccess)
        val browseResult = result.getOrNull()!!
        assertTrue(browseResult is BrowseResult.Complete)
        assertEquals(2, browseResult.items.size)
    }

    @Test
    fun `invoke returns Paginated result for large list`() = runTest {
        // Arrange
        val path = ResourcePath.FileSystem("file:///storage/downloads")
        val items = List(100) { index ->
            createFileSystemFile("file$index.txt")
        }
        val paginatedResult = BrowseResult.Paginated(
            items = items,
            hasMore = true,
            totalEstimate = 10500,
            nextOffset = 100
        )

        coEvery { repository.browse(path, 0, 100) } returns Result.success(paginatedResult)

        // Act
        val result = useCase(path, offset = 0, limit = 100)

        // Assert
        assertTrue(result.isSuccess)
        val browseResult = result.getOrNull()!!
        assertTrue(browseResult is BrowseResult.Paginated)

        val paginated = browseResult as BrowseResult.Paginated
        assertEquals(100, paginated.items.size)
        assertTrue(paginated.hasMore)
        assertEquals(10500, paginated.totalEstimate)
        assertEquals(100, paginated.nextOffset)
    }

    @Test
    fun `invoke propagates repository errors`() = runTest {
        // Arrange
        val path = ResourcePath.FileSystem("file:///storage/invalid")
        val exception = IllegalArgumentException("Invalid path")

        coEvery {
            repository.browse(path, 0, BrowseItemsUseCase.PAGINATION_THRESHOLD)
        } returns Result.failure(exception)

        // Act
        val result = useCase(path, offset = 0, limit = BrowseItemsUseCase.PAGINATION_THRESHOLD)

        // Assert
        assertTrue(result.isFailure)
        assertEquals("Invalid path", result.exceptionOrNull()?.message)
    }

    @Test
    fun `invoke handles pagination with custom offset and limit`() = runTest {
        // Arrange
        val path = ResourcePath.FileSystem("file:///storage/downloads")
        val items = List(50) { index ->
            createFileSystemFile("file${100 + index}.txt")
        }
        val paginatedResult = BrowseResult.Paginated(
            items = items,
            hasMore = true,
            totalEstimate = 500,
            nextOffset = 150
        )

        coEvery { repository.browse(path, 100, 50) } returns Result.success(paginatedResult)

        // Act
        val result = useCase(path, offset = 100, limit = 50)

        // Assert
        assertTrue(result.isSuccess)
        val browseResult = result.getOrNull()!!
        assertTrue(browseResult is BrowseResult.Paginated)

        val paginated = browseResult as BrowseResult.Paginated
        assertEquals(50, paginated.items.size)
        assertEquals(150, paginated.nextOffset)
    }

    @Test
    fun `invoke returns Paginated result when hasMore is false`() = runTest {
        // Arrange
        val path = ResourcePath.FileSystem("file:///storage/downloads")
        val items = List(50) { index ->
            createFileSystemFile("file$index.txt")
        }
        val paginatedResult = BrowseResult.Paginated(
            items = items,
            hasMore = false,
            totalEstimate = 50,
            nextOffset = 50
        )

        coEvery { repository.browse(path, 0, 100) } returns Result.success(paginatedResult)

        // Act
        val result = useCase(path, offset = 0, limit = 100)

        // Assert
        assertTrue(result.isSuccess)
        val browseResult = result.getOrNull()!!
        assertTrue(browseResult is BrowseResult.Paginated)

        val paginated = browseResult as BrowseResult.Paginated
        assertFalse(paginated.hasMore)
    }

    private fun createFileSystemFile(name: String) = BrowsableItem.FileSystemFile(
        path = ResourcePath.FileSystem("file:///storage/downloads/$name"),
        name = name,
        sizeBytes = 1024L,
        lastModified = 1234567890L,
        mimeType = "text/plain"
    )
}
