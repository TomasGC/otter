package app.otter.data.extractor

import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import app.otter.util.PathValidator
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
    private val pathValidator: PathValidator,
    tempFileManager: ITempFileManager,
    sevenZipHelper: SevenZipExtractorHelper
) : BaseArchiveExtractor(tempFileManager, sevenZipHelper, IndeterminateProgressCalculator()) {

    override fun supports(type: ArchiveType): Boolean {
        return type == ArchiveType.TAR || type == ArchiveType.TAR_GZ
    }

    override fun getTag(): String = "TAR"

    override suspend fun extractInternal(
        inputStream: InputStream,
        destinationPath: File,
        archiveType: ArchiveType,
        sourceFileName: String,
        onProgress: (ExtractionProgress) -> Unit
    ): ExtractionResult = withContext(Dispatchers.IO) {
        var extractedCount = 0

        // Wrap with GZIP decompressor if needed
        val decompressedStream = if (archiveType == ArchiveType.TAR_GZ) {
            GzipCompressorInputStream(BufferedInputStream(inputStream))
        } else {
            BufferedInputStream(inputStream)
        }

        // Count total entries first (requires re-opening stream for actual extraction)
        // For now, we'll use -1 as totalCount since we can't count without consuming the stream
        val totalCount = -1

        // Progress throttler from base class
        val throttler = ProgressThrottler()

        // Extract all entries
        TarArchiveInputStream(decompressedStream).use { tarInput ->
            var entry = tarInput.nextTarEntry

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

                    // Use base class helper for throttled progress notifications
                    notifyProgress(extractedCount, totalCount, entry.name, throttler, onProgress)
                }

                entry = tarInput.nextTarEntry
            }
        }

        logger.logComplete(extractedCount)

        ExtractionResult.Success(
            outputPath = destinationPath.absolutePath,
            extractedFilesCount = extractedCount
        )
    }
}
