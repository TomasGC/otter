package app.otter.data.extractor

import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import app.otter.util.PathValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import javax.inject.Inject

/**
 * Extractor for TAR archives.
 * Supports: .tar, .tar.gz, .tgz, .tar.bz2, .tbz2
 *
 * Uses Apache Commons Compress library.
 * Uses InputStream directly (no temp file needed), avoiding asset packaging issues.
 */
class TarExtractor @Inject constructor(
    private val pathValidator: PathValidator,
    tempFileManager: ITempFileManager,
    sevenZipHelper: SevenZipExtractorHelper,
    private val sizeGuardFactory: () -> ArchiveSizeGuard = { ArchiveSizeGuard() }
) : BaseArchiveExtractor(tempFileManager, sevenZipHelper, IndeterminateProgressCalculator()) {

    override fun supports(type: ArchiveType): Boolean {
        return type == ArchiveType.TAR || type == ArchiveType.TAR_GZ || type == ArchiveType.TAR_BZ2
    }

    override fun getTag(): String = "TAR"

    override suspend fun extractInternal(
        inputStream: InputStream,
        destinationPath: File,
        archiveType: ArchiveType,
        sourceFileName: String,
        selectedItems: List<String>?,
        onProgress: (ExtractionProgress) -> Unit
    ): ExtractionResult = withContext(Dispatchers.IO) {
        var extractedCount = 0

        val decompressedStream = decompressStream(inputStream, archiveType)

        // Count total entries first (requires re-opening stream for actual extraction)
        // For now, we'll use -1 as totalCount since we can't count without consuming the stream
        val totalCount = -1
        val selectedPaths = selectedItems?.toSet()

        // Progress throttler from base class
        val throttler = ProgressThrottler()

        // Guards against zip-bomb entries (decompressed size far exceeding declared/expected size)
        val sizeGuard = sizeGuardFactory()
        val buffer = ByteArray(BUFFER_SIZE_BYTES)

        // Extract all entries
        TarArchiveInputStream(decompressedStream).use { tarInput ->
            var entry = tarInput.nextTarEntry
            while (entry != null && isActive) {
                val currentEntry = entry
                if (!currentEntry.isDirectory && isEntrySelected(currentEntry.name, selectedPaths)) {
                    val outputFile = pathValidator.createSafeOutputFile(destinationPath, currentEntry.name)
                    sizeGuard.startEntry()
                    extractTarEntry(tarInput, outputFile, sizeGuard, buffer)

                    if (isActive) {
                        extractedCount++
                        notifyProgress(extractedCount, totalCount, currentEntry.name, throttler, onProgress)
                    }
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

    private fun decompressStream(inputStream: InputStream, archiveType: ArchiveType): InputStream =
        when (archiveType) {
            ArchiveType.TAR_GZ -> GzipCompressorInputStream(BufferedInputStream(inputStream))
            ArchiveType.TAR_BZ2 -> BZip2CompressorInputStream(BufferedInputStream(inputStream))
            else -> BufferedInputStream(inputStream)
        }

    private fun extractTarEntry(
        tarInput: TarArchiveInputStream,
        outputFile: File,
        sizeGuard: ArchiveSizeGuard,
        buffer: ByteArray
    ) {
        outputFile.outputStream().buffered(BUFFER_SIZE_BYTES).use { output ->
            while (true) {
                val bytesRead = tarInput.read(buffer)
                if (bytesRead == -1) break
                sizeGuard.track(bytesRead)
                output.write(buffer, 0, bytesRead)
            }
        }
    }
}
