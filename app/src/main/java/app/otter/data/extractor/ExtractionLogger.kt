package app.otter.data.extractor

import timber.log.Timber

/**
 * Handles logging for archive extraction operations.
 *
 * Centralizes logging logic with configurable intervals to avoid
 * performance overhead from excessive logging.
 *
 * Responsibilities:
 * - Log extraction progress at configurable intervals
 * - Log extraction completion
 * - Provide consistent log formatting across all extractors
 */
class ExtractionLogger(private val tag: String) {

    /**
     * Logs extraction progress at configured intervals.
     *
     * Only logs every LOG_INTERVAL_FILES to avoid performance issues.
     *
     * @param extractedCount Number of files extracted so far
     * @param totalCount Total number of files to extract
     * @param fileName Current file being extracted
     */
    fun logProgress(extractedCount: Int, totalCount: Int, fileName: String) {
        // Only log every 100 files to avoid performance issues
        if (extractedCount % LOG_INTERVAL_FILES == 0 || extractedCount == totalCount) {
            Timber.tag(tag).d("Extracted $extractedCount/$totalCount files (current: $fileName)")
        }
    }

    /**
     * Logs extraction completion.
     *
     * @param extractedCount Total number of files extracted
     */
    fun logComplete(extractedCount: Int) {
        Timber.tag(tag).d("Extraction completed: $extractedCount files")
    }

    companion object {
        private const val LOG_INTERVAL_FILES = 100
    }
}
