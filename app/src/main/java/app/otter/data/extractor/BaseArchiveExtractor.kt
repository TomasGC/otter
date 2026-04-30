package app.otter.data.extractor

import timber.log.Timber
import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import app.otter.util.PathValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sf.sevenzipjbinding.IInArchive
import java.io.File
import java.io.InputStream

abstract class BaseArchiveExtractor : ArchiveExtractor {

    protected abstract fun getTag(): String

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
     */
    protected suspend fun extractWithTempFile(
        inputStream: InputStream,
        destinationPath: File,
        archiveType: ArchiveType,
        sourceFileName: String,
        onProgress: (ExtractionProgress) -> Unit,
        extractFromTempFile: suspend (File) -> ExtractionResult
    ): ExtractionResult {
        var tempFile: File? = null
        return try {
            tempFile = createTempFile(inputStream, archiveType)
            extractFromTempFile(tempFile)
        } finally {
            tempFile?.delete()
            Timber.tag(getTag()).d("Temp file deleted")
        }
    }

    protected fun createTempFile(inputStream: InputStream, archiveType: ArchiveType): File {
        // Use proper extension for 7-Zip format detection
        // Critical for .tar.gz and .tgz which require multi-layer extraction
        val extension = archiveType.extensions.first()

        val tempFile = File.createTempFile(TEMP_FILE_PREFIX, extension)
        Timber.tag(getTag()).d("Created temp file: ${tempFile.absolutePath}")

        val bytesCopied = tempFile.outputStream().use { output ->
            inputStream.copyTo(output)
        }
        Timber.tag(getTag()).d("Copied $bytesCopied bytes to temp file. File size: ${tempFile.length()}")

        if (!tempFile.exists() || tempFile.length() == 0L) {
            val error = "Temp file is empty or doesn't exist"
            Timber.tag(getTag()).e(error)
            throw IllegalStateException(error)
        }

        return tempFile
    }

    protected fun validatePath(outputFile: File, destinationPath: File, entryName: String) {
        if (!outputFile.canonicalPath.startsWith(destinationPath.canonicalPath)) {
            val error = "Entry outside destination: $entryName"
            Timber.tag(getTag()).e(error)
            throw SecurityException(error)
        }
    }

    protected fun logExtractionProgress(extractedCount: Int, totalCount: Int, fileName: String) {
        // Only log every 100 files to avoid performance issues
        if (extractedCount % LOG_INTERVAL_FILES == 0 || extractedCount == totalCount) {
            Timber.tag(getTag()).d("Extracted $extractedCount/$totalCount files (current: $fileName)")
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
     * Helper to notify progress with throttling and automatic progress calculation.
     */
    protected fun notifyProgress(
        extractedCount: Int,
        totalCount: Int,
        currentFile: String,
        throttler: ProgressThrottler,
        onProgress: (ExtractionProgress) -> Unit
    ) {
        if (throttler.shouldNotify()) {
            val progress = if (totalCount > 0) extractedCount.toFloat() / totalCount else 0f
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
        private const val LOG_INTERVAL_FILES = 100

        // Shared temp file constants for extractors requiring RandomAccessFile
        const val TEMP_FILE_PREFIX = "otter_archive_"
        const val TEMP_FILE_SUFFIX = ".tmp"

        // Shared buffer and throttle constants
        @JvmStatic
        protected val BUFFER_SIZE_BYTES = 256 * 1024 // 256 KB

        @JvmStatic
        protected val PROGRESS_THROTTLE_MS = 1000L // 1 second
    }

    protected fun logExtractionComplete(extractedCount: Int) {
        Timber.tag(getTag()).d("Extraction completed: $extractedCount files")
    }

    /**
     * Common extraction logic for all 7-Zip-based extractors (RAR, 7z, TAR).
     * Eliminates code duplication across RarExtractor, SevenZipExtractor, and TarExtractor.
     */
    protected suspend fun extractWith7Zip(
        inArchive: IInArchive,
        destinationPath: File,
        pathValidator: PathValidator,
        onProgress: (ExtractionProgress) -> Unit
    ): ExtractionResult {
        return try {
            val callback = SevenZipCallbackExtractor(
                inArchive = inArchive,
                destinationPath = destinationPath,
                pathValidator = pathValidator
            ) { extractedCount, totalCount, fileName ->
                logExtractionProgress(extractedCount, totalCount, fileName)
                onProgress(
                    ExtractionProgress.Extracting(
                        currentFile = fileName,
                        extractedCount = extractedCount,
                        totalCount = totalCount,
                        progress = if (totalCount > 0) extractedCount.toFloat() / totalCount else 0f
                    )
                )
            }

            inArchive.extract(null, false, callback)

            val extractedCount = callback.getExtractedCount()
            logExtractionComplete(extractedCount)

            ExtractionResult.Success(
                outputPath = destinationPath.absolutePath,
                extractedFilesCount = extractedCount
            )
        } finally {
            inArchive.close()
        }
    }
}
