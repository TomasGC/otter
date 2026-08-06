package app.otter.unit.domain.usecase

import app.otter.domain.model.FolderCounts
import app.otter.domain.repository.ItemBrowserRepository
import app.otter.domain.usecase.GetFolderCountsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetFolderCountsUseCaseTest {

    private lateinit var repository: ItemBrowserRepository
    private lateinit var useCase: GetFolderCountsUseCase

    @Before
    fun setup() {
        repository = mockk()
        useCase = GetFolderCountsUseCase(repository)
    }

    @Test
    fun `empty path list emits nothing`() = runTest {
        val results = useCase(emptyList()).toList()
        assertTrue(results.isEmpty())
    }

    @Test
    fun `single path emits one pair`() = runTest {
        val path = "/storage/emulated/0/Downloads"
        val counts = FolderCounts(folderCount = 3, fileCount = 7)
        coEvery { repository.getFolderCounts(path) } returns counts

        val results = useCase(listOf(path)).toList()

        assertEquals(1, results.size)
        assertEquals(path to counts, results.first())
    }

    @Test
    fun `multiple paths each emit a pair`() = runTest {
        val paths = listOf("/a", "/b", "/c")
        paths.forEach { path ->
            coEvery { repository.getFolderCounts(path) } returns FolderCounts(1, 2)
        }

        val results = useCase(paths).toList()

        assertEquals(3, results.size)
        val emittedPaths = results.map { it.first }.toSet()
        assertEquals(paths.toSet(), emittedPaths)
    }

    @Test
    fun `repository error for one path is swallowed, others still emit`() = runTest {
        val goodPath = "/good"
        val badPath = "/bad"
        val counts = FolderCounts(2, 5)
        coEvery { repository.getFolderCounts(goodPath) } returns counts
        coEvery { repository.getFolderCounts(badPath) } throws RuntimeException("IO error")

        val results = useCase(listOf(goodPath, badPath)).toList()

        // Only goodPath emits; badPath error is silently swallowed via runCatching
        assertEquals(1, results.size)
        assertEquals(goodPath to counts, results.first())
    }

    @Test
    fun `each path invokes repository exactly once`() = runTest {
        val paths = listOf("/x", "/y")
        paths.forEach { coEvery { repository.getFolderCounts(it) } returns FolderCounts(0, 0) }

        useCase(paths).toList()

        paths.forEach { path -> coVerify(exactly = 1) { repository.getFolderCounts(path) } }
    }

    @Test
    fun `all paths failing emits nothing`() = runTest {
        val paths = listOf("/bad1", "/bad2")
        paths.forEach { coEvery { repository.getFolderCounts(it) } throws RuntimeException("IO error") }

        val results = useCase(paths).toList()

        assertTrue(results.isEmpty())
    }

    @Test
    fun `duplicate paths each invoke repository separately`() = runTest {
        val path = "/storage/emulated/0/Documents"
        val counts = FolderCounts(1, 1)
        coEvery { repository.getFolderCounts(path) } returns counts

        val results = useCase(listOf(path, path)).toList()

        assertEquals(2, results.size)
        results.forEach { assertEquals(path to counts, it) }
        coVerify(exactly = 2) { repository.getFolderCounts(path) }
    }
}
