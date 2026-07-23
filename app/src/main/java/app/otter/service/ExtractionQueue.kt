package app.otter.service

import android.content.Context
import android.net.Uri
import timber.log.Timber
import app.otter.data.util.ResourcePathConverter
import app.otter.domain.model.ResourcePath
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Queue for managing sequential archive extraction.
 * Injectable via Hilt for proper test isolation.
 */
@Singleton
class ExtractionQueue @Inject constructor() {
    private val queue = ConcurrentLinkedQueue<ExtractionTask>()
    private val isExtracting = AtomicBoolean(false)

    data class ExtractionTask(
        val archiveUri: ResourcePath,
        val fileName: String,
        val selectedItems: List<String>? = null // null = extract all; list = extract specific entries
    )

    companion object {
        private const val TAG = "ExtractionQueue"
    }

    /**
     * Adds archives to extraction queue.
     */
    fun enqueueAll(tasks: List<ExtractionTask>) {
        queue.addAll(tasks)
        Timber.tag(TAG).d("Enqueued ${tasks.size} tasks. Total in queue: ${queue.size}")
    }

    /**
     * Starts extraction of next archive in queue.
     */
    fun processNext(context: Context): Boolean {
        if (!isExtracting.compareAndSet(false, true)) {
            Timber.tag(TAG).d("Already extracting, skipping")
            return false
        }

        val task = queue.poll()
        if (task == null) {
            Timber.tag(TAG).d("Queue empty")
            isExtracting.set(false)
            return false
        }

        Timber.tag(TAG).d("Processing: ${task.fileName}, remaining: ${queue.size}")

        val intent = ExtractionService.newIntent(
            context = context,
            archiveUri = task.archiveUri,
            fileName = task.fileName,
            selectedItems = task.selectedItems
        )
        context.startService(intent)

        return true
    }

    /**
     * Marks current extraction as complete.
     */
    fun markComplete() {
        Timber.tag(TAG).d("Marking current extraction as complete")
        isExtracting.set(false)
    }

    /**
     * Gets and removes the next task from queue without starting extraction.
     */
    fun pollNext(): ExtractionTask? {
        val task = queue.poll()
        if (task != null) {
            Timber.tag(TAG).d("Polled next task: ${task.fileName}, remaining: ${queue.size}")
            isExtracting.set(true)
        } else {
            Timber.tag(TAG).d("Queue empty")
        }
        return task
    }

    /**
     * Clears the queue.
     */
    fun clear() {
        queue.clear()
        isExtracting.set(false)
        Timber.tag(TAG).d("Queue cleared")
    }

    /**
     * Returns number of remaining tasks.
     */
    fun size(): Int = queue.size
}
