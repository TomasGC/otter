package app.otter.service

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
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
    fun `emitProgress should emit event to progressState flow`() = runTest {
        // Given
        val fileName = "test.zip"
        val currentFile = "file.txt"
        val extractedCount = 5
        val totalCount = 10
        val progress = 0.5f

        // When
        eventBus.emitProgress(
            fileName = fileName,
            currentFile = currentFile,
            extractedCount = extractedCount,
            totalCount = totalCount,
            progress = progress
        )

        // Then
        val capturedEvent = eventBus.progressState.first { it != null }

        assertNotNull(capturedEvent)
        assertEquals(fileName, capturedEvent?.fileName)
        assertEquals(currentFile, capturedEvent?.currentFile)
        assertEquals(extractedCount, capturedEvent?.extractedCount)
        assertEquals(totalCount, capturedEvent?.totalCount)
        assertEquals(progress, capturedEvent?.progress ?: 0f, 0.001f)
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
        eventBus.emitProgress(
            fileName = fileName,
            currentFile = currentFile,
            extractedCount = extractedCount,
            totalCount = totalCount,
            progress = progress
        )

        // Then
        val capturedEvent = eventBus.progressState.first { it != null }

        assertNotNull(capturedEvent)
        assertEquals("archive.zip", capturedEvent?.fileName)
        assertEquals("readme.txt", capturedEvent?.currentFile)
        assertEquals(42, capturedEvent?.extractedCount)
        assertEquals(100, capturedEvent?.totalCount)
        assertEquals(0.42f, capturedEvent?.progress ?: 0f, 0.001f)
    }

    @Test
    fun `progressState should retain last value`() = runTest {
        // Given
        eventBus.emitProgress(
            fileName = "test.zip",
            currentFile = "file.txt",
            extractedCount = 1,
            totalCount = 10,
            progress = 0.1f
        )

        // When - Collect after emission
        val event = eventBus.progressState.first { it != null }

        // Then - Should receive current state value
        assertEquals("test.zip", event?.fileName)
        assertEquals(1, event?.extractedCount)
    }

    @Test
    fun `emitComplete should emit event to completeEvents flow`() = runTest {
        // Given - Start collecting in background
        var eventReceived = false
        val eventJob = launch {
            eventBus.completeEvents.first()
            eventReceived = true
        }

        // Ensure collector is ready
        advanceUntilIdle()

        // When
        eventBus.emitComplete()

        // Then - Advance to process emission
        advanceUntilIdle()

        // Verify event was received
        assertEquals(true, eventReceived)

        eventJob.cancel()
    }

    @Test
    fun `multiple collectors should receive same progress state`() = runTest {
        // Given
        val collector1 = mutableListOf<ExtractionEventBus.ProgressEvent?>()
        val collector2 = mutableListOf<ExtractionEventBus.ProgressEvent?>()

        val job1 = launch {
            eventBus.progressState.collect { collector1.add(it) }
        }
        val job2 = launch {
            eventBus.progressState.collect { collector2.add(it) }
        }

        // Ensure collectors are ready
        advanceUntilIdle()

        // When
        eventBus.emitProgress("test.zip", "file.txt", 1, 10, 0.1f)

        // Then - Advance coroutines to ensure collection completes
        advanceUntilIdle()

        assertEquals(2, collector1.size) // null + event
        assertEquals(2, collector2.size) // null + event
        assertEquals("test.zip", collector1[1]?.fileName)
        assertEquals("test.zip", collector2[1]?.fileName)

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

        // Then - Advance time to trigger timeout
        advanceUntilIdle()

        // Wait and verify timeout
        try {
            job.join()
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            // Expected - no replay for complete events
        }

        // Should not have received event (replay = 0)
        assertFalse(eventReceived)
    }

    @Test
    fun `cancelling collection should stop receiving state updates`() = runTest {
        // Given
        val receivedEvents = mutableListOf<ExtractionEventBus.ProgressEvent?>()
        val job = launch {
            eventBus.progressState.collect { receivedEvents.add(it) }
        }

        // Ensure collector is ready
        advanceUntilIdle()

        // When - Emit first event
        eventBus.emitProgress("test1.zip", "file1.txt", 1, 10, 0.1f)
        advanceUntilIdle()

        // Cancel collection
        job.cancel()

        // Emit second event after cancellation
        eventBus.emitProgress("test2.zip", "file2.txt", 2, 10, 0.2f)
        advanceUntilIdle()

        // Then - Should only have received null + first event
        assertEquals(2, receivedEvents.size) // null + first event
        assertEquals("test1.zip", receivedEvents[1]?.fileName)
    }
}
