package app.otter.service

import android.content.Context
import android.net.Uri
import app.otter.domain.model.ResourcePath
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    private lateinit var queue: ExtractionQueue

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        queue = ExtractionQueue()
    }

    @After
    fun tearDown() {
        queue.clear()
    }

    @Test
    fun `enqueueAll adds tasks to queue`() {
        // Given
        val tasks = listOf(
            ExtractionQueue.ExtractionTask(ResourcePath.FileSystem("file:///archive1.zip"), "archive1.zip"),
            ExtractionQueue.ExtractionTask(ResourcePath.FileSystem("file:///archive2.zip"), "archive2.zip")
        )

        // When
        queue.enqueueAll(tasks)

        // Then
        assertEquals(2, queue.size())
    }

    @Test
    fun `processNext returns false when already extracting`() {
        // Given
        val tasks = listOf(
            ExtractionQueue.ExtractionTask(ResourcePath.FileSystem("file:///archive1.zip"), "archive1.zip")
        )
        queue.enqueueAll(tasks)

        // When - First call starts extraction
        val firstResult = queue.processNext(mockContext)

        // Then - Second call should return false (already extracting)
        val secondResult = queue.processNext(mockContext)

        assertTrue(firstResult)
        assertFalse(secondResult)
    }

    @Test
    fun `processNext returns false when queue is empty`() {
        // When
        val result = queue.processNext(mockContext)

        // Then
        assertFalse(result)
        assertEquals(0, queue.size())
    }

    @Test
    fun `processNext starts service with correct intent`() {
        // Given
        val task = ExtractionQueue.ExtractionTask(ResourcePath.FileSystem("file:///test.zip"), "test.zip")
        queue.enqueueAll(listOf(task))

        every { mockContext.startService(any()) } returns mockk()

        // When
        val result = queue.processNext(mockContext)

        // Then
        assertTrue(result)
        verify { mockContext.startService(any()) }
        assertEquals(0, queue.size()) // Task removed from queue
    }

    @Test
    fun `onExtractionComplete processes next task in queue`() {
        // Given
        val tasks = listOf(
            ExtractionQueue.ExtractionTask(ResourcePath.FileSystem("file:///archive1.zip"), "archive1.zip"),
            ExtractionQueue.ExtractionTask(ResourcePath.FileSystem("file:///archive2.zip"), "archive2.zip")
        )
        queue.enqueueAll(tasks)

        // When - Poll tasks
        val first = queue.pollNext()
        assertNotNull(first)
        assertEquals("archive1.zip", first?.fileName)
        assertEquals(1, queue.size())

        // Then - Mark complete and poll second
        queue.markComplete()
        val second = queue.pollNext()
        assertNotNull(second)
        assertEquals("archive2.zip", second?.fileName)
        assertEquals(0, queue.size())
    }

    @Test
    fun `clear removes all tasks and resets state`() {
        // Given
        val tasks = listOf(
            ExtractionQueue.ExtractionTask(ResourcePath.FileSystem("file:///archive1.zip"), "archive1.zip"),
            ExtractionQueue.ExtractionTask(ResourcePath.FileSystem("file:///archive2.zip"), "archive2.zip")
        )
        queue.enqueueAll(tasks)
        queue.processNext(mockContext)

        // When
        queue.clear()

        // Then
        assertEquals(0, queue.size())

        // Should be able to process again (extraction flag reset)
        queue.enqueueAll(listOf(tasks.first()))
        val result = queue.processNext(mockContext)
        assertTrue(result)
    }

    @Test
    fun `size returns correct number of remaining tasks`() {
        // Given
        assertEquals(0, queue.size())

        val tasks = listOf(
            ExtractionQueue.ExtractionTask(ResourcePath.FileSystem("file:///archive1.zip"), "archive1.zip"),
            ExtractionQueue.ExtractionTask(ResourcePath.FileSystem("file:///archive2.zip"), "archive2.zip"),
            ExtractionQueue.ExtractionTask(ResourcePath.FileSystem("file:///archive3.zip"), "archive3.zip")
        )

        // When
        queue.enqueueAll(tasks)

        // Then
        assertEquals(3, queue.size())

        queue.processNext(mockContext)
        assertEquals(2, queue.size())
    }

    // ========== selectedItems preservation ==========

    @Test
    fun `pollNext preserves selectedItems from enqueued task`() {
        val selectedItems = listOf("folder/file1.txt", "folder/file2.jpg")
        val task = ExtractionQueue.ExtractionTask(
            archiveUri = ResourcePath.FileSystem("file:///archive.zip"),
            fileName = "archive.zip",
            selectedItems = selectedItems
        )
        queue.enqueueAll(listOf(task))

        val polled = queue.pollNext()

        assertNotNull(polled)
        assertEquals(selectedItems, polled?.selectedItems)
    }

    @Test
    fun `pollNext with null selectedItems preserves null`() {
        val task = ExtractionQueue.ExtractionTask(
            archiveUri = ResourcePath.FileSystem("file:///archive.zip"),
            fileName = "archive.zip",
            selectedItems = null
        )
        queue.enqueueAll(listOf(task))

        val polled = queue.pollNext()

        assertNotNull(polled)
        assertNull(polled?.selectedItems)
    }

    @Test
    fun `enqueueAll preserves selectedItems order across multiple tasks`() {
        val tasks = listOf(
            ExtractionQueue.ExtractionTask(ResourcePath.FileSystem("file:///a.zip"), "a.zip", listOf("a.txt")),
            ExtractionQueue.ExtractionTask(ResourcePath.FileSystem("file:///b.zip"), "b.zip", listOf("b1.txt", "b2.txt")),
            ExtractionQueue.ExtractionTask(ResourcePath.FileSystem("file:///c.zip"), "c.zip", null)
        )
        queue.enqueueAll(tasks)

        val first = queue.pollNext()
        queue.markComplete()
        val second = queue.pollNext()
        queue.markComplete()
        val third = queue.pollNext()

        assertEquals(listOf("a.txt"), first?.selectedItems)
        assertEquals(listOf("b1.txt", "b2.txt"), second?.selectedItems)
        assertNull(third?.selectedItems)
    }

    @Test
    fun `concurrent processNext calls do not both start extraction`() {
        // isExtracting was a plain var with a check-then-set race: two threads could both read
        // isExtracting=false before either set it true, both start extraction. Use a barrier to
        // force many real threads to hit processNext at the same instant, deterministically.
        val tasks = (1..20).map {
            ExtractionQueue.ExtractionTask(ResourcePath.FileSystem("file:///archive$it.zip"), "archive$it.zip")
        }
        queue.enqueueAll(tasks)

        val threadCount = 20
        val barrier = java.util.concurrent.CyclicBarrier(threadCount)
        val successCount = java.util.concurrent.atomic.AtomicInteger(0)
        every { mockContext.startService(any()) } returns mockk()

        val threads = (1..threadCount).map {
            Thread {
                barrier.await()
                if (queue.processNext(mockContext)) {
                    successCount.incrementAndGet()
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertEquals(
            "Only one concurrent processNext call must succeed in starting extraction",
            1,
            successCount.get()
        )
    }

    @Test
    fun `processNext passes selectedItems in service intent`() {
        val selectedItems = listOf("entry1.txt", "entry2.png")
        val task = ExtractionQueue.ExtractionTask(
            archiveUri = ResourcePath.FileSystem("file:///archive.zip"),
            fileName = "archive.zip",
            selectedItems = selectedItems
        )
        queue.enqueueAll(listOf(task))

        var capturedIntent: android.content.Intent? = null
        every { mockContext.startService(any()) } answers {
            capturedIntent = firstArg()
            mockk()
        }

        queue.processNext(mockContext)

        assertNotNull("Service must be started", capturedIntent)
        val extras = capturedIntent?.getStringArrayListExtra("extra_selected_items")
        assertEquals("selectedItems must be passed to intent", selectedItems, extras)
    }
}
