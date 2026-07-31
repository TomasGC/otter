package app.otter.integration.service

import android.content.Context
import android.content.Intent
import app.otter.domain.model.ResourcePath
import app.otter.service.ExtractionQueue
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Integration tests for selectedItems propagation through ExtractionQueue → ExtractionService intent.
 *
 * Verifies the full chain: enqueue task with selectedItems → processNext → intent carries selectedItems.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExtractionSelectedItemsMockIntegrationTest {

    private lateinit var context: Context
    private lateinit var queue: ExtractionQueue
    private val intentSlot = slot<Intent>()

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        queue = ExtractionQueue()
        every { context.startService(capture(intentSlot)) } returns mockk()
    }

    @Test
    fun `selectedItems flow through queue to service intent`() {
        val selectedItems = listOf("folder/doc.pdf", "folder/image.png", "notes.txt")
        val task = ExtractionQueue.ExtractionTask(
            archiveUri = ResourcePath.FileSystem("file:///storage/archive.zip"),
            fileName = "archive.zip",
            selectedItems = selectedItems
        )
        queue.enqueueAll(listOf(task))

        queue.processNext(context)

        assertTrue("Service must be started", intentSlot.isCaptured)
        val extras = intentSlot.captured.getStringArrayListExtra("extra_selected_items")
        assertNotNull("selectedItems extra must be present in intent", extras)
        assertEquals("All selected items must arrive in intent", selectedItems, extras)
    }

    @Test
    fun `null selectedItems produces intent without extra`() {
        val task = ExtractionQueue.ExtractionTask(
            archiveUri = ResourcePath.FileSystem("file:///archive.zip"),
            fileName = "archive.zip",
            selectedItems = null
        )
        queue.enqueueAll(listOf(task))

        queue.processNext(context)

        assertTrue("Service must be started", intentSlot.isCaptured)
        assertNull("selectedItems extra must be absent when null",
            intentSlot.captured.getStringArrayListExtra("extra_selected_items"))
    }

    @Test
    fun `queue preserves selectedItems order for sequential tasks`() {
        val tasks = listOf(
            ExtractionQueue.ExtractionTask(
                ResourcePath.FileSystem("file:///a.zip"), "a.zip",
                selectedItems = listOf("z.txt", "a.txt") // reverse alpha order
            ),
            ExtractionQueue.ExtractionTask(
                ResourcePath.FileSystem("file:///b.zip"), "b.zip",
                selectedItems = listOf("1.txt", "2.txt", "3.txt")
            )
        )
        queue.enqueueAll(tasks)

        // First task
        queue.processNext(context)
        val firstExtras = intentSlot.captured.getStringArrayListExtra("extra_selected_items")
        assertEquals(listOf("z.txt", "a.txt"), firstExtras)

        // Reset and complete first task
        queue.markComplete()

        // Second task
        val intentSlot2 = slot<Intent>()
        every { context.startService(capture(intentSlot2)) } returns mockk()
        queue.processNext(context)
        val secondExtras = intentSlot2.captured.getStringArrayListExtra("extra_selected_items")
        assertEquals(listOf("1.txt", "2.txt", "3.txt"), secondExtras)
    }

    @Test
    fun `large selectedItems list (1000 entries) preserved through queue`() {
        val largeList = (1..1000).map { "folder/file_$it.txt" }
        val task = ExtractionQueue.ExtractionTask(
            archiveUri = ResourcePath.FileSystem("file:///large.zip"),
            fileName = "large.zip",
            selectedItems = largeList
        )
        queue.enqueueAll(listOf(task))

        queue.processNext(context)

        val extras = intentSlot.captured.getStringArrayListExtra("extra_selected_items")
        assertNotNull("Large selectedItems list must be preserved", extras)
        assertEquals("All 1000 items must arrive in intent", 1000, extras!!.size)
        assertEquals("Order must be preserved", largeList, extras)
    }

    @Test
    fun `mixed null and non-null selectedItems across queue tasks`() {
        val tasks = listOf(
            ExtractionQueue.ExtractionTask(
                ResourcePath.FileSystem("file:///selective.zip"), "selective.zip",
                selectedItems = listOf("important.txt")
            ),
            ExtractionQueue.ExtractionTask(
                ResourcePath.FileSystem("file:///full.zip"), "full.zip",
                selectedItems = null // extract all
            )
        )
        queue.enqueueAll(tasks)

        // Poll directly to check without startService side effects
        val first = queue.pollNext()
        assertEquals(listOf("important.txt"), first?.selectedItems)

        queue.markComplete()
        val second = queue.pollNext()
        assertNull("Second task must have null selectedItems (extract all)", second?.selectedItems)
    }
}
