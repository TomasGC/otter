package app.otter.service

import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExtractionQueueTest {

    private lateinit var mockContext: Context

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        ExtractionQueue.clear()
    }

    @After
    fun tearDown() {
        ExtractionQueue.clear()
    }

    @Test
    fun `enqueueAll adds tasks to queue`() {
        // Given
        val tasks = listOf(
            ExtractionQueue.ExtractionTask(Uri.parse("file:///archive1.zip"), "archive1.zip"),
            ExtractionQueue.ExtractionTask(Uri.parse("file:///archive2.zip"), "archive2.zip")
        )

        // When
        ExtractionQueue.enqueueAll(tasks)

        // Then
        assertEquals(2, ExtractionQueue.size())
    }

    @Test
    fun `processNext returns false when already extracting`() {
        // Given
        val tasks = listOf(
            ExtractionQueue.ExtractionTask(Uri.parse("file:///archive1.zip"), "archive1.zip")
        )
        ExtractionQueue.enqueueAll(tasks)

        // When - First call starts extraction
        val firstResult = ExtractionQueue.processNext(mockContext)

        // Then - Second call should return false (already extracting)
        val secondResult = ExtractionQueue.processNext(mockContext)

        assertTrue(firstResult)
        assertFalse(secondResult)
    }

    @Test
    fun `processNext returns false when queue is empty`() {
        // When
        val result = ExtractionQueue.processNext(mockContext)

        // Then
        assertFalse(result)
        assertEquals(0, ExtractionQueue.size())
    }

    @Test
    fun `processNext starts service with correct intent`() {
        // Given
        val task = ExtractionQueue.ExtractionTask(Uri.parse("file:///test.zip"), "test.zip")
        ExtractionQueue.enqueueAll(listOf(task))

        every { mockContext.startService(any()) } returns mockk()

        // When
        val result = ExtractionQueue.processNext(mockContext)

        // Then
        assertTrue(result)
        verify { mockContext.startService(any()) }
        assertEquals(0, ExtractionQueue.size()) // Task removed from queue
    }

    @Test
    fun `onExtractionComplete processes next task in queue`() {
        // Given
        val tasks = listOf(
            ExtractionQueue.ExtractionTask(Uri.parse("file:///archive1.zip"), "archive1.zip"),
            ExtractionQueue.ExtractionTask(Uri.parse("file:///archive2.zip"), "archive2.zip")
        )
        ExtractionQueue.enqueueAll(tasks)

        // When - Poll tasks
        val first = ExtractionQueue.pollNext()
        assertNotNull(first)
        assertEquals("archive1.zip", first?.fileName)
        assertEquals(1, ExtractionQueue.size())

        // Then - Mark complete and poll second
        ExtractionQueue.markComplete()
        val second = ExtractionQueue.pollNext()
        assertNotNull(second)
        assertEquals("archive2.zip", second?.fileName)
        assertEquals(0, ExtractionQueue.size())
    }

    @Test
    fun `clear removes all tasks and resets state`() {
        // Given
        val tasks = listOf(
            ExtractionQueue.ExtractionTask(Uri.parse("file:///archive1.zip"), "archive1.zip"),
            ExtractionQueue.ExtractionTask(Uri.parse("file:///archive2.zip"), "archive2.zip")
        )
        ExtractionQueue.enqueueAll(tasks)
        ExtractionQueue.processNext(mockContext)

        // When
        ExtractionQueue.clear()

        // Then
        assertEquals(0, ExtractionQueue.size())

        // Should be able to process again (extraction flag reset)
        ExtractionQueue.enqueueAll(listOf(tasks.first()))
        val result = ExtractionQueue.processNext(mockContext)
        assertTrue(result)
    }

    @Test
    fun `size returns correct number of remaining tasks`() {
        // Given
        assertEquals(0, ExtractionQueue.size())

        val tasks = listOf(
            ExtractionQueue.ExtractionTask(Uri.parse("file:///archive1.zip"), "archive1.zip"),
            ExtractionQueue.ExtractionTask(Uri.parse("file:///archive2.zip"), "archive2.zip"),
            ExtractionQueue.ExtractionTask(Uri.parse("file:///archive3.zip"), "archive3.zip")
        )

        // When
        ExtractionQueue.enqueueAll(tasks)

        // Then
        assertEquals(3, ExtractionQueue.size())

        ExtractionQueue.processNext(mockContext)
        assertEquals(2, ExtractionQueue.size())
    }
}
