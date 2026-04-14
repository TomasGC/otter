package app.otter.domain.usecase

import android.net.Uri
import app.otter.domain.model.ArchiveFile
import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.repository.ArchiveRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExtractArchiveUseCaseTest {

    private val repository: ArchiveRepository = mockk(relaxed = true)
    private val useCase = ExtractArchiveUseCase(repository)

    @Test
    fun `should return error when archive is empty`() = runTest {
        val emptyArchive = ArchiveFile(
            uri = Uri.parse("file:///empty.zip"),
            name = "empty.zip",
            sizeBytes = 0L,
            mimeType = "application/zip",
            type = ArchiveType.ZIP
        )
        val destinationUri = Uri.parse("file:///downloads")

        val result = useCase(emptyArchive, destinationUri).first()

        assertTrue(result is ExtractionProgress.Error)
        assertTrue((result as ExtractionProgress.Error).message.contains("empty"))
    }

    @Test
    fun `should delegate extraction to repository for valid archive`() = runTest {
        val archive = ArchiveFile(
            uri = Uri.parse("file:///test.zip"),
            name = "test.zip",
            sizeBytes = 1024L,
            mimeType = "application/zip",
            type = ArchiveType.ZIP
        )
        val destinationUri = Uri.parse("file:///downloads")

        every { repository.extractArchive(archive, destinationUri) } returns flowOf(
            ExtractionProgress.Success("/downloads/test", 5)
        )

        val result = useCase(archive, destinationUri).first()

        assertTrue(result is ExtractionProgress.Success)
        assertEquals("/downloads/test", (result as ExtractionProgress.Success).outputPath)
        assertEquals(5, result.extractedCount)
    }

    @Test
    fun `should propagate progress events from repository`() = runTest {
        val archive = createValidArchive()
        val destinationUri = Uri.parse("file:///downloads")

        every { repository.extractArchive(archive, destinationUri) } returns flowOf(
            ExtractionProgress.Extracting("file1.txt", 1, 3, 0.33f),
            ExtractionProgress.Extracting("file2.txt", 2, 3, 0.66f),
            ExtractionProgress.Extracting("file3.txt", 3, 3, 1.0f),
            ExtractionProgress.Success("/downloads/test", 3)
        )

        val results = mutableListOf<ExtractionProgress>()
        useCase(archive, destinationUri).collect { results.add(it) }

        assertEquals(4, results.size)
        assertTrue(results[0] is ExtractionProgress.Extracting)
        assertTrue(results[3] is ExtractionProgress.Success)
    }

    @Test
    fun `should propagate errors from repository`() = runTest {
        val archive = createValidArchive()
        val destinationUri = Uri.parse("file:///downloads")

        every { repository.extractArchive(archive, destinationUri) } returns flowOf(
            ExtractionProgress.Error("Corrupted archive", null)
        )

        val result = useCase(archive, destinationUri).first()

        assertTrue(result is ExtractionProgress.Error)
        assertEquals("Corrupted archive", (result as ExtractionProgress.Error).message)
    }

    @Test
    fun `should allow extraction of large archives`() = runTest {
        val largeArchive = ArchiveFile(
            uri = Uri.parse("file:///large.zip"),
            name = "large.zip",
            sizeBytes = 100_000_000L, // 100 MB
            mimeType = "application/zip",
            type = ArchiveType.ZIP
        )
        val destinationUri = Uri.parse("file:///downloads")

        every { repository.extractArchive(largeArchive, destinationUri) } returns flowOf(
            ExtractionProgress.Success("/downloads/large", 1000)
        )

        val result = useCase(largeArchive, destinationUri).first()

        assertTrue(result is ExtractionProgress.Success)
    }

    private fun createValidArchive() = ArchiveFile(
        uri = Uri.parse("file:///test.zip"),
        name = "test.zip",
        sizeBytes = 1024L,
        mimeType = "application/zip",
        type = ArchiveType.ZIP
    )
}
