package app.otter.service

import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ExtractionEventBus
 */
class ExtractionEventBusTest {

    private lateinit var eventBus: ExtractionEventBus

    @Before
    fun setup() {
        eventBus = ExtractionEventBus()
    }

    @Test
    fun `emitProgress should emit event to progressEvents flow`() = runTest {
        // Given
        val fileName = "test.zip"
        val currentFile = "file.txt"
        val extractedCount = 5
        val totalCount = 10
        val progress = 0.5f

        // When
        val eventJob = launch {
            val event = eventBus.progressEvents.first()

            // Then
            assertEquals(fileName, event.fileName)
            assertEquals(currentFile, event.currentFile)
            assertEquals(extractedCount, event.extractedCount)
            assertEquals(totalCount, event.totalCount)
            assertEquals(progress, event.progress, 0.001f)
        }

        eventBus.emitProgress(
            fileName = fileName,
            currentFile = currentFile,
            extractedCount = extractedCount,
            totalCount = totalCount,
            progress = progress
        )

        eventJob.join()
    }

    @Test
    fun `emitProgress should create ProgressEvent with correct data`() = runTest {
        // Given
        val fileName = "archive.zip"
        val currentFile = "readme.txt"
        val extractedCount = 42
        val totalCount = 100
        val progress = 0.42f

        // When
        val eventJob = launch {
            val event = eventBus.progressEvents.first()

            // Then
            assertNotNull(event)
            assertEquals("archive.zip", event.fileName)
            assertEquals("readme.txt", event.currentFile)
            assertEquals(42, event.extractedCount)
            assertEquals(100, event.totalCount)
            assertEquals(0.42f, event.progress, 0.001f)
        }

        eventBus.emitProgress(
            fileName = fileName,
            currentFile = currentFile,
            extractedCount = extractedCount,
            totalCount = totalCount,
            progress = progress
        )

        eventJob.join()
    }

    @Test
    fun `progressEvents should have replay of 1`() = runTest {
        // Given
        eventBus.emitProgress(
            fileName = "test.zip",
            currentFile = "file.txt",
            extractedCount = 1,
            totalCount = 10,
            progress = 0.1f
        )

        // When - Collect after emission
        val event = eventBus.progressEvents.first()

        // Then - Should receive replayed event
        assertEquals("test.zip", event.fileName)
        assertEquals(1, event.extractedCount)
    }
}
