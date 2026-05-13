package app.otter.service

import java.util.LinkedList

/**
 * Circular buffer to keep track of the most recent extracted files.
 * Used to display file list in extraction notification (Samsung My Files style).
 *
 * @param maxSize Maximum number of files to keep (default: 3)
 */
class RecentFilesBuffer(private val maxSize: Int = 3) {

    private val buffer = LinkedList<String>()

    /**
     * Add a new file to the buffer.
     * If buffer is full, oldest file is removed.
     */
    fun add(fileName: String) {
        if (buffer.size >= maxSize) {
            buffer.removeFirst()
        }
        buffer.addLast(fileName)
    }

    /**
     * Get all files in the buffer.
     * Returns list from oldest to newest.
     */
    fun getFiles(): List<String> = buffer.toList()

    /**
     * Get the most recent file (currently extracting).
     * Returns null if buffer is empty.
     */
    fun getCurrentFile(): String? = buffer.lastOrNull()

    /**
     * Get previously extracted files (excluding current).
     * Returns list from oldest to newest.
     */
    fun getCompletedFiles(): List<String> {
        return if (buffer.size > 1) {
            buffer.subList(0, buffer.size - 1)
        } else {
            emptyList()
        }
    }

    /**
     * Clear all files from the buffer.
     */
    fun clear() {
        buffer.clear()
    }

    /**
     * Get buffer size.
     */
    fun size(): Int = buffer.size

    /**
     * Check if buffer is empty.
     */
    fun isEmpty(): Boolean = buffer.isEmpty()
}
