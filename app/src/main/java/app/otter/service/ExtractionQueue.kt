package app.otter.service

import android.content.Context
import android.net.Uri
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Singleton queue for managing sequential archive extraction.
 */
object ExtractionQueue {
    private const val TAG = "ExtractionQueue"
    private val queue = ConcurrentLinkedQueue<ExtractionTask>()
    private var isExtracting = false

    data class ExtractionTask(
        val archiveUri: Uri,
        val fileName: String
    )

    /**
     * Adds archives to extraction queue.
     */
    fun enqueueAll(tasks: List<ExtractionTask>) {
        queue.addAll(tasks)
        Log.d(TAG, "Enqueued ${tasks.size} tasks. Total in queue: ${queue.size}")
    }

    /**
     * Starts extraction of next archive in queue.
     */
    fun processNext(context: Context): Boolean {
        if (isExtracting) {
            Log.d(TAG, "Already extracting, skipping")
            return false
        }

        val task = queue.poll()
        if (task == null) {
            Log.d(TAG, "Queue empty")
            return false
        }

        Log.d(TAG, "Processing: ${task.fileName}, remaining: ${queue.size}")
        isExtracting = true

        val intent = ExtractionService.newIntent(
            context = context,
            archiveUri = task.archiveUri,
            fileName = task.fileName
        )
        context.startService(intent)

        return true
    }

    /**
     * Marks current extraction as complete.
     */
    fun markComplete() {
        Log.d(TAG, "Marking current extraction as complete")
        isExtracting = false
    }

    /**
     * Gets and removes the next task from queue without starting extraction.
     */
    fun pollNext(): ExtractionTask? {
        val task = queue.poll()
        if (task != null) {
            Log.d(TAG, "Polled next task: ${task.fileName}, remaining: ${queue.size}")
            isExtracting = true
        } else {
            Log.d(TAG, "Queue empty")
        }
        return task
    }

    /**
     * Clears the queue.
     */
    fun clear() {
        queue.clear()
        isExtracting = false
        Log.d(TAG, "Queue cleared")
    }

    /**
     * Returns number of remaining tasks.
     */
    fun size(): Int = queue.size
}
