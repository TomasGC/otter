package app.otter.data.extractor

/**
 * Enforces decompressed-size limits during extraction to guard against zip-bomb archives.
 *
 * Limits are checked against actual bytes written to disk, not against archive
 * entry metadata (which is attacker-controlled and can misreport size).
 */
class ArchiveSizeGuard(
    private val maxFileSizeBytes: Long = MAX_FILE_SIZE_BYTES,
    private val maxTotalSizeBytes: Long = MAX_TOTAL_SIZE_BYTES
) {
    private var currentFileBytes = 0L
    private var totalBytes = 0L

    /** Resets the per-entry counter. Call before extracting a new archive entry. */
    fun startEntry() {
        currentFileBytes = 0L
    }

    /**
     * Records bytes just written for the current entry.
     * @throws SecurityException if the per-file or cumulative limit is exceeded.
     */
    fun track(bytesWritten: Int) {
        currentFileBytes += bytesWritten
        totalBytes += bytesWritten

        if (currentFileBytes > maxFileSizeBytes) {
            throw SecurityException(
                "Entry exceeds max file size of $maxFileSizeBytes bytes (zip-bomb protection)"
            )
        }
        if (totalBytes > maxTotalSizeBytes) {
            throw SecurityException(
                "Archive exceeds max total size of $maxTotalSizeBytes bytes (zip-bomb protection)"
            )
        }
    }

    companion object {
        const val MAX_FILE_SIZE_BYTES = 100L * 1024 * 1024 // 100 MB
        const val MAX_TOTAL_SIZE_BYTES = 500L * 1024 * 1024 // 500 MB
    }
}
