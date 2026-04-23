package app.otter.service

import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `emitComplete should emit event to completeEvents flow`() = runTest {
        // Given
        val eventJob = launch {
            val event = eventBus.completeEvents.first()

            // Then
            assertEquals(Unit, event)
        }

        // When
        eventBus.emitComplete()

        eventJob.join()
    }

    @Test
    fun `multiple collectors should receive same progress event`() = runTest {
        // Given
        val collector1 = mutableListOf<ExtractionEventBus.ProgressEvent>()
        val collector2 = mutableListOf<ExtractionEventBus.ProgressEvent>()

        val job1 = launch {
            eventBus.progressEvents.collect { collector1.add(it) }
        }
        val job2 = launch {
            eventBus.progressEvents.collect { collector2.add(it) }
        }

        // When
        eventBus.emitProgress("test.zip", "file.txt", 1, 10, 0.1f)

        // Wait a bit for collection
        kotlinx.coroutines.delay(100)

        // Then
        assertEquals(1, collector1.size)
        assertEquals(1, collector2.size)
        assertEquals("test.zip", collector1[0].fileName)
        assertEquals("test.zip", collector2[0].fileName)

        job1.cancel()
        job2.cancel()
    }

    @Test
    fun `completeEvents should not have replay`() = runTest {
        // Given - Emit before collecting
        eventBus.emitComplete()

        // When - Try to collect after emission
        var eventReceived = false
        val job = launch {
            kotlinx.coroutines.withTimeout(500) {
                eventBus.completeEvents.first()
                eventReceived = true
            }
        }

        // Wait and verify timeout
        try {
            job.join()
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            // Expected - no replay for complete events
        }

        // Then - Should not have received event (replay = 0)
        assertFalse(eventReceived)
    }

    @Test
    fun `cancelling collection should stop receiving events`() = runTest {
        // Given
        val receivedEvents = mutableListOf<ExtractionEventBus.ProgressEvent>()
        val job = launch {
            eventBus.progressEvents.collect { receivedEvents.add(it) }
        }

        // When - Emit first event
        eventBus.emitProgress("test1.zip", "file1.txt", 1, 10, 0.1f)
        kotlinx.coroutines.delay(100)

        // Cancel collection
        job.cancel()

        // Emit second event after cancellation
        eventBus.emitProgress("test2.zip", "file2.txt", 2, 10, 0.2f)
        kotlinx.coroutines.delay(100)

        // Then - Should only have received first event
        assertEquals(1, receivedEvents.size)
        assertEquals("test1.zip", receivedEvents[0].fileName)
    }
}
