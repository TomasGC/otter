package app.otter.data.extractor

import timber.log.Timber
import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

abstract class BaseArchiveExtractor(
    protected val tempFileManager: ITempFileManager,
    protected val sevenZipHelper: SevenZipExtractorHelper,
    protected val progressCalculator: ProgressCalculator = StandardProgressCalculator()
) : ArchiveExtractor {

    protected abstract fun getTag(): String

    protected val logger: ExtractionLogger by lazy { ExtractionLogger(getTag()) }

    /**
     * Template method that handles common extraction logic and ensures
     * final progress callback at 100% is always sent for all extractors.
     */
    override suspend fun extract(
        inputStream: InputStream,
        destinationPath: File,
        archiveType: ArchiveType,
        sourceFileName: String,
        onProgress: (ExtractionProgress) -> Unit
    ): ExtractionResult = withContext(Dispatchers.IO) {
        try {
            // Delegate extraction to subclass
            val result = extractInternal(inputStream, destinationPath, archiveType, sourceFileName, onProgress)

            // ✅ Automatic final progress callback at 100% for all extractors
            if (result is ExtractionResult.Success) {
                onProgress(
                    ExtractionProgress.Extracting(
                        currentFile = "",
                        extractedCount = result.extractedFilesCount,
                        totalCount = result.extractedFilesCount,
                        progress = 1.0f
                    )
                )
            }

            result
        } catch (e: CancellationException) {
            Timber.tag(getTag()).d("${getTag()} extraction cancelled")
            throw e // Re-throw to propagate cancellation
        } catch (e: Exception) {
            Timber.tag(getTag()).e(e, "${getTag()} extraction failed: ${e.message}")
            ExtractionResult.Failure(
                errorMessage = "${getTag()} extraction failed: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Internal extraction logic to be implemented by subclasses.
     * The base class will automatically send a final progress callback at 100% after this completes.
     */
    protected abstract suspend fun extractInternal(
        inputStream: InputStream,
        destinationPath: File,
        archiveType: ArchiveType,
        sourceFileName: String,
        onProgress: (ExtractionProgress) -> Unit
    ): ExtractionResult

    /**
     * Helper for extractors that need temporary files (RAR, 7z).
     * Creates temp file, extracts, then cleans up automatically.
     *
     * The callback lambda can capture needed values from the outer scope
     * (destinationPath, pathValidator, onProgress, etc.).
     */
    protected suspend fun extractWithTempFile(
        inputStream: InputStream,
        archiveType: ArchiveType,
        extractFromTempFile: suspend (File) -> ExtractionResult
    ): ExtractionResult {
        var tempFile: File? = null
        return try {
            tempFile = tempFileManager.createTempFile(inputStream, archiveType, getTag())
            extractFromTempFile(tempFile)
        } finally {
            tempFile?.delete()
            Timber.tag(getTag()).d("Temp file deleted")
        }
    }

    /**
     * Helper class to throttle progress notifications to avoid performance overhead.
     * Only notifies if sufficient time has passed since last notification.
     */
    protected class ProgressThrottler(
        private val throttleMs: Long = DEFAULT_THROTTLE_MS
    ) {
        private var lastNotificationTime = 0L

        fun shouldNotify(): Boolean {
            val currentTime = System.currentTimeMillis()
            return if (currentTime - lastNotificationTime > throttleMs) {
                lastNotificationTime = currentTime
                true
            } else {
                false
            }
        }

        companion object {
            private const val DEFAULT_THROTTLE_MS = 1000L
        }
    }

    /**
     * Helper to notify progress with throttling and strategy-based progress calculation.
     */
    protected fun notifyProgress(
        extractedCount: Int,
        totalCount: Int,
        currentFile: String,
        throttler: ProgressThrottler,
        onProgress: (ExtractionProgress) -> Unit
    ) {
        if (throttler.shouldNotify()) {
            val progress = progressCalculator.calculate(extractedCount, totalCount)
            onProgress(
                ExtractionProgress.Extracting(
                    currentFile = currentFile,
                    extractedCount = extractedCount,
                    totalCount = totalCount,
                    progress = progress
                )
            )
        }
    }

    companion object {
        // Shared buffer and throttle constants
        @JvmStatic
        protected val BUFFER_SIZE_BYTES = 256 * 1024 // 256 KB

        @JvmStatic
        protected val PROGRESS_THROTTLE_MS = 1000L // 1 second
    }
}
