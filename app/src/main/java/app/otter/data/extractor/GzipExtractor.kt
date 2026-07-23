package app.otter.data.extractor

import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import javax.inject.Inject

/**
 * Extractor for GZIP compressed files.
 * Supports: .gz, .gzip
 *
 * GZIP is a compression format (not an archive format like ZIP or TAR).
 * It compresses a single file without storing directory structure or multiple files.
 *
 * Uses Apache Commons Compress library.
 * Uses InputStream directly (no temp file needed), avoiding asset packaging issues.
 *
 * Example: photo.jpg.gz → photo.jpg
 */
class GzipExtractor @Inject constructor(
    tempFileManager: ITempFileManager,
    sevenZipHelper: SevenZipExtractorHelper,
    private val sizeGuardFactory: () -> ArchiveSizeGuard = { ArchiveSizeGuard() }
) : BaseArchiveExtractor(tempFileManager, sevenZipHelper, SingleFileProgressCalculator()) {

    override fun supports(type: ArchiveType): Boolean = type == ArchiveType.GZIP

    override fun getTag(): String = "GZIP"

    override suspend fun extractInternal(
        inputStream: InputStream,
        destinationPath: File,
        archiveType: ArchiveType,
        sourceFileName: String,
        selectedItems: List<String>?,
        onProgress: (ExtractionProgress) -> Unit
    ): ExtractionResult = withContext(Dispatchers.IO) {
        // GZIP decompresses to a single file
        // Derive output filename by removing .gz/.gzip extension from source name
        val outputFileName = deriveOutputFileName(sourceFileName)
        val outputFile = File(destinationPath, outputFileName)

        Timber.tag(getTag()).d("Decompressing $sourceFileName → $outputFileName")

        // Ensure destination directory exists
        destinationPath.mkdirs()

        // Decompress using Apache Commons Compress
        val sizeGuard = sizeGuardFactory()
        sizeGuard.startEntry()
        val buffer = ByteArray(BaseArchiveExtractor.BUFFER_SIZE_BYTES)
        GzipCompressorInputStream(BufferedInputStream(inputStream)).use { gzipInput ->
            outputFile.outputStream().buffered(BaseArchiveExtractor.BUFFER_SIZE_BYTES).use { output ->
                while (true) {
                    if (!isActive) throw CancellationException("GZIP extraction cancelled")
                    val bytesRead = gzipInput.read(buffer)
                    if (bytesRead == -1) break
                    sizeGuard.track(bytesRead)
                    output.write(buffer, 0, bytesRead)
                }
            }
        }

        // Verify output
        if (!outputFile.exists() || outputFile.length() == 0L) {
            return@withContext ExtractionResult.Failure(
                errorMessage = "GZIP decompression produced empty file",
                cause = IllegalStateException("Empty output")
            )
        }

        Timber.tag(getTag()).d("Decompressed to ${outputFile.name} (${outputFile.length()} bytes)")

        ExtractionResult.Success(
            outputPath = destinationPath.absolutePath,
            extractedFilesCount = 1
        )
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
}
