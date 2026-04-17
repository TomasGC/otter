package app.otter.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import app.otter.data.extractor.ArchiveExtractor
import app.otter.domain.model.ArchiveFile
import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ArchiveRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var zipExtractor: ArchiveExtractor
    private lateinit var repository: ArchiveRepositoryImpl

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        contentResolver = mockk(relaxed = true)
        zipExtractor = mockk(relaxed = true)

        every { context.contentResolver } returns contentResolver
        every { zipExtractor.supports(ArchiveType.ZIP) } returns true

        repository = ArchiveRepositoryImpl(context, listOf(zipExtractor))
    }

    @Test
    fun `should select correct extractor for archive type`() = runTest {
        val archive = createTestArchive()
        val destinationUri = Uri.parse("file:///downloads")
        val inputStream = ByteArrayInputStream(byteArrayOf())

        every { contentResolver.openInputStream(archive.uri) } returns inputStream
        coEvery {
            zipExtractor.extract(any(), any(), any())
        } returns ExtractionResult.Success("/downloads/test", 5)

        repository.extractArchive(archive, destinationUri).toList()

        verify { zipExtractor.supports(ArchiveType.ZIP) }
    }

    @Test
    fun `should return error when no extractor supports archive type`() = runTest {
        val unsupportedExtractor: ArchiveExtractor = mockk {
            every { supports(any()) } returns false
        }
        val repo = ArchiveRepositoryImpl(context, listOf(unsupportedExtractor))

        val archive = createTestArchive()
        val destinationUri = Uri.parse("file:///downloads")

        val results = repo.extractArchive(archive, destinationUri).toList()

        assertTrue(results.any { it is ExtractionProgress.Error })
        val error = results.first { it is ExtractionProgress.Error } as ExtractionProgress.Error
        assertTrue(error.message.contains("No extractor"))
    }

    @Test
    fun `should return error when cannot open archive`() = runTest {
        val archive = createTestArchive()
        val destinationUri = Uri.parse("file:///downloads")

        every { contentResolver.openInputStream(archive.uri) } returns null

        val results = repository.extractArchive(archive, destinationUri).toList()

        assertTrue(results.any { it is ExtractionProgress.Error })
        val error = results.first { it is ExtractionProgress.Error } as ExtractionProgress.Error
        assertTrue(error.message.contains("Cannot open archive"))
    }

    @Test
    fun `should propagate extraction success`() = runTest {
        val archive = createTestArchive()
        val destinationUri = Uri.parse("file:///downloads")
        val inputStream = ByteArrayInputStream(byteArrayOf())

        every { contentResolver.openInputStream(archive.uri) } returns inputStream
        coEvery {
            zipExtractor.extract(any(), any(), any())
        } returns ExtractionResult.Success("/downloads/test", 5)

        val results = repository.extractArchive(archive, destinationUri).toList()

        assertTrue(results.any { it is ExtractionProgress.Success })
        val success = results.first { it is ExtractionProgress.Success } as ExtractionProgress.Success
        assertEquals("/downloads/test", success.outputPath)
        assertEquals(5, success.extractedCount)
    }

    @Test
    fun `should propagate extraction failure`() = runTest {
        val archive = createTestArchive()
        val destinationUri = Uri.parse("file:///downloads")
        val inputStream = ByteArrayInputStream(byteArrayOf())

        every { contentResolver.openInputStream(archive.uri) } returns inputStream
        coEvery {
            zipExtractor.extract(any(), any(), any())
        } returns ExtractionResult.Failure("Corrupted archive", null)

        val results = repository.extractArchive(archive, destinationUri).toList()

        assertTrue(results.any { it is ExtractionProgress.Error })
        val error = results.first { it is ExtractionProgress.Error } as ExtractionProgress.Error
        assertTrue(error.message.contains("Corrupted archive"))
    }

    @Test
    fun `should use destination path directly`() = runTest {
        val archive = createTestArchive()
        val destinationUri = Uri.parse("file:///downloads/test")
        val inputStream = ByteArrayInputStream(byteArrayOf())

        every { contentResolver.openInputStream(archive.uri) } returns inputStream
        coEvery {
            zipExtractor.extract(any(), any(), any())
        } answers {
            val destinationPath = secondArg<File>()
            assertEquals("/downloads/test", destinationPath.path)
            ExtractionResult.Success(destinationPath.absolutePath, 3)
        }

        repository.extractArchive(archive, destinationUri).toList()
    }

    @Test
    fun `should emit progress events during extraction`() = runTest {
        val archive = createTestArchive()
        val destinationUri = Uri.parse("file:///downloads")
        val inputStream = ByteArrayInputStream(byteArrayOf())

        every { contentResolver.openInputStream(archive.uri) } returns inputStream
        coEvery {
            zipExtractor.extract(any(), any(), any())
        } answers {
            val onProgress = thirdArg<(ExtractionProgress) -> Unit>()
            // Simulate progress callbacks
            onProgress(ExtractionProgress.Extracting("file1.txt", 1, 0, 0f))
            onProgress(ExtractionProgress.Extracting("file2.txt", 2, 0, 0f))
            ExtractionResult.Success("/downloads/test", 2)
        }

        val results = repository.extractArchive(archive, destinationUri).toList()

        // Should emit: Idle, Extracting(1), Extracting(2), Success
        assertTrue("Should have at least 4 events", results.size >= 4)
        assertTrue("First event should be Idle", results[0] is ExtractionProgress.Idle)
        assertTrue("Should contain Extracting events", results.any { it is ExtractionProgress.Extracting })
        assertTrue("Last event should be Success", results.last() is ExtractionProgress.Success)
    }

    @Test
    fun `should handle cancellation gracefully`() = runTest {
        val archive = createTestArchive()
        val destinationUri = Uri.parse("file:///downloads")
        val inputStream = ByteArrayInputStream(byteArrayOf())

        every { contentResolver.openInputStream(archive.uri) } returns inputStream
        coEvery {
            zipExtractor.extract(any(), any(), any())
        } throws kotlinx.coroutines.CancellationException("Extraction cancelled")

        val results = repository.extractArchive(archive, destinationUri).toList()

        // Flow should close cleanly on cancellation (just Idle event)
        assertTrue("Should emit Idle before cancellation", results.isNotEmpty())
        assertTrue("First event should be Idle", results[0] is ExtractionProgress.Idle)
    }

    private fun createTestArchive() = ArchiveFile(
        uri = Uri.parse("file:///test.zip"),
        name = "test.zip",
        sizeBytes = 1024L,
        mimeType = "application/zip",
        type = ArchiveType.ZIP
    )
}
