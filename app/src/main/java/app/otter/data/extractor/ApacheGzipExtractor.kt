package app.otter.data.extractor

import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import app.otter.util.PathValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import javax.inject.Inject

/**
 * Extractor for GZIP compressed files using Apache Commons Compress.
 * Supports: .gz, .gzip
 *
 * GZIP is a compression format (not an archive format like ZIP or TAR).
 * It compresses a single file without storing directory structure or multiple files.
 *
 * Uses InputStream directly (no temp file needed), avoiding asset packaging issues.
 *
 * Example: photo.jpg.gz → photo.jpg
 */
class ApacheGzipExtractor @Inject constructor(
    private val pathValidator: PathValidator
) : ArchiveExtractor {

    override fun supports(type: ArchiveType): Boolean = type == ArchiveType.GZIP

    override suspend fun extract(
        inputStream: InputStream,
        destinationPath: File,
        archiveType: ArchiveType,
        sourceFileName: String,
        onProgress: (ExtractionProgress) -> Unit
    ): ExtractionResult = withContext(Dispatchers.IO) {
        try {
            // GZIP decompresses to a single file
            // Derive output filename by removing .gz/.gzip extension from source name
            val outputFileName = deriveOutputFileName(sourceFileName)
            val outputFile = File(destinationPath, outputFileName)

            Timber.tag(TAG).d("Decompressing $sourceFileName → $outputFileName")

            // Ensure destination directory exists
            destinationPath.mkdirs()

            // Decompress using Apache Commons Compress
            GzipCompressorInputStream(BufferedInputStream(inputStream)).use { gzipInput ->
                outputFile.outputStream().buffered(BUFFER_SIZE_BYTES).use { output ->
                    gzipInput.copyTo(output, BUFFER_SIZE_BYTES)
                }
            }

            // Verify output
            if (!outputFile.exists() || outputFile.length() == 0L) {
                return@withContext ExtractionResult.Failure(
                    errorMessage = "GZIP decompression produced empty file",
                    cause = IllegalStateException("Empty output")
                )
            }

            Timber.tag(TAG).d("Decompressed to ${outputFile.name} (${outputFile.length()} bytes)")

            // Report progress (GZIP has exactly 1 file)
            onProgress(
                ExtractionProgress.Extracting(
                    currentFile = outputFileName,
                    extractedCount = 1,
                    totalCount = 1,
                    progress = 1.0f
                )
            )

            ExtractionResult.Success(
                outputPath = destinationPath.absolutePath,
                extractedFilesCount = 1
            )
        } catch (e: CancellationException) {
            Timber.tag(TAG).d("GZIP decompression cancelled")
            throw e // Re-throw to propagate cancellation
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "GZIP decompression error: ${e.message}")
            ExtractionResult.Failure(
                errorMessage = "GZIP decompression failed: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Derives the output filename by removing .gz or .gzip extension.
     *
     * Examples:
     * - photo.jpg.gz → photo.jpg
     * - document.txt.gzip → document.txt
     * - otter_archive_12345.gz → otter_archive_12345
     */
    private fun deriveOutputFileName(sourceFileName: String): String {
        return when {
            sourceFileName.endsWith(".gzip", ignoreCase = true) -> {
                sourceFileName.substring(0, sourceFileName.length - 5)
            }
            sourceFileName.endsWith(".gz", ignoreCase = true) -> {
                sourceFileName.substring(0, sourceFileName.length - 3)
            }
            else -> sourceFileName // Fallback (should not happen)
        }
    }

    companion object {
        private const val TAG = "ApacheGzipExtractor"
        private const val BUFFER_SIZE_BYTES = 256 * 1024 // 256 KB
    }
}
