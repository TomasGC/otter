package app.otter.data.extractor

import android.util.Log
import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import app.otter.util.FileLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject

class ZipExtractor @Inject constructor() : ArchiveExtractor {

    override fun supports(type: ArchiveType): Boolean = type == ArchiveType.ZIP

    override suspend fun extract(
        inputStream: InputStream,
        destinationPath: File,
        onProgress: (ExtractionProgress) -> Unit
    ): ExtractionResult = withContext(Dispatchers.IO) {
        var extractedCount = 0

        try {
            FileLogger.log("Starting ZIP extraction (optimized: direct stream, 256KB buffer)", TAG)
            Log.d(TAG, "Starting ZIP extraction")

            // Pre-allocate large buffer (256 KB for optimal I/O)
            val buffer = ByteArray(256 * 1024)

            // Extract directly from input stream - no temp file needed!
            ZipInputStream(inputStream).use { zipStream ->
                var entry: ZipEntry? = zipStream.nextEntry
                var lastNotificationTime = 0L

                while (entry != null && isActive) { // Check if coroutine is still active
                    if (!entry.isDirectory) {
                        val outputFile = File(destinationPath, entry.name)

                        // Path traversal protection
                        if (!outputFile.canonicalPath.startsWith(destinationPath.canonicalPath)) {
                            val error = "Entry outside destination: ${entry.name}"
                            FileLogger.logError(error, null, TAG)
                            throw SecurityException(error)
                        }

                        // Create parent directories
                        outputFile.parentFile?.mkdirs()

                        // Simple buffered write with large buffer
                        outputFile.outputStream().buffered(256 * 1024).use { output ->
                            var bytesRead: Int
                            while (zipStream.read(buffer).also { bytesRead = it } != -1 && isActive) {
                                output.write(buffer, 0, bytesRead)
                            }
                        }

                        // Check if cancelled during file extraction
                        if (!isActive) {
                            FileLogger.log("Extraction cancelled by user", TAG)
                            break
                        }

                        extractedCount++

                        // Throttle notifications: update max every 1000ms (reduced notification overhead)
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastNotificationTime > 1000) {
                            lastNotificationTime = currentTime
                            onProgress(
                                ExtractionProgress.Extracting(
                                    currentFile = entry.name,
                                    extractedCount = extractedCount,
                                    totalCount = 0, // Unknown during extraction
                                    progress = 0f // Indeterminate progress
                                )
                            )
                        }

                        // Minimal logging - every 500 files
                        if (extractedCount % 500 == 0) {
                            FileLogger.log("Extracted $extractedCount files", TAG)
                        }
                    }

                    entry = zipStream.nextEntry
                }
            }

            FileLogger.log("ZIP extraction completed: $extractedCount files", TAG)
            Log.d(TAG, "Extraction completed: $extractedCount files")

            ExtractionResult.Success(
                outputPath = destinationPath.absolutePath,
                extractedFilesCount = extractedCount
            )
        } catch (e: CancellationException) {
            FileLogger.log("ZIP extraction cancelled: $extractedCount files extracted before cancellation", TAG)
            Log.d(TAG, "Extraction cancelled: $extractedCount files extracted")
            throw e // Re-throw to propagate cancellation
        } catch (e: Exception) {
            FileLogger.logError("ZIP extraction failed: ${e.message}", e, TAG)
            Log.e(TAG, "ZIP extraction failed", e)
            ExtractionResult.Failure(
                errorMessage = "Extraction failed: ${e.message}",
                cause = e
            )
        }
    }

    companion object {
        private const val TAG = "ZipExtractor"
    }
}
