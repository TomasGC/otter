package app.otter.data.extractor

import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import app.otter.util.PathValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import javax.inject.Inject

/**
 * Extractor for TAR archives using Apache Commons Compress.
 * Supports: .tar, .tar.gz, .tgz
 *
 * Uses InputStream directly (no temp file needed), avoiding asset packaging issues.
 */
class ApacheTarExtractor @Inject constructor(
    private val pathValidator: PathValidator
) : ArchiveExtractor {

    override fun supports(type: ArchiveType): Boolean {
        return type == ArchiveType.TAR || type == ArchiveType.TAR_GZ
    }

    override suspend fun extract(
        inputStream: InputStream,
        destinationPath: File,
        archiveType: ArchiveType,
        sourceFileName: String,
        onProgress: (ExtractionProgress) -> Unit
    ): ExtractionResult = withContext(Dispatchers.IO) {
        var extractedCount = 0

        try {
            // Wrap with GZIP decompressor if needed
            val decompressedStream = if (archiveType == ArchiveType.TAR_GZ) {
                GzipCompressorInputStream(BufferedInputStream(inputStream))
            } else {
                BufferedInputStream(inputStream)
            }

            // Count total entries first (requires re-opening stream for actual extraction)
            // For now, we'll use -1 as totalCount since we can't count without consuming the stream
            val totalCount = -1

            // Extract all entries
            TarArchiveInputStream(decompressedStream).use { tarInput ->
                var entry = tarInput.nextTarEntry
                var lastNotificationTime = 0L

                while (entry != null && isActive) {
                    if (!entry.isDirectory) {
                        // Path traversal protection
                        val outputFile = pathValidator.createSafeOutputFile(destinationPath, entry.name)

                        // Extract entry
                        outputFile.outputStream().buffered(BUFFER_SIZE_BYTES).use { output ->
                            tarInput.copyTo(output, BUFFER_SIZE_BYTES)
                        }

                        // Check if cancelled during file extraction
                        if (!isActive) break

                        extractedCount++

                        // Throttle progress notifications
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastNotificationTime > PROGRESS_THROTTLE_MS) {
                            lastNotificationTime = currentTime
                            val progress = if (totalCount > 0) extractedCount.toFloat() / totalCount else 0f
                            onProgress(
                                ExtractionProgress.Extracting(
                                    currentFile = entry.name,
                                    extractedCount = extractedCount,
                                    totalCount = totalCount,
                                    progress = progress
                                )
                            )
                        }
                    }

                    entry = tarInput.nextTarEntry
                }
            }

            Timber.tag(TAG).d("TAR extraction completed: $extractedCount files")

            ExtractionResult.Success(
                outputPath = destinationPath.absolutePath,
                extractedFilesCount = extractedCount
            )
        } catch (e: CancellationException) {
            Timber.tag(TAG).d("TAR extraction cancelled")
            throw e // Re-throw to propagate cancellation
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "TAR extraction failed: ${e.message}")
            ExtractionResult.Failure(
                errorMessage = "TAR extraction failed: ${e.message}",
                cause = e
            )
        }
    }

    companion object {
        private const val TAG = "ApacheTarExtractor"
        private const val BUFFER_SIZE_BYTES = 256 * 1024 // 256 KB
        private const val PROGRESS_THROTTLE_MS = 1000L // 1 second
    }
}
