package app.otter.data.extractor

import timber.log.Timber
import app.otter.data.inspector.RpaInspector
import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import app.otter.util.PathValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject

/**
 * RPA (Ren'Py Archive) extractor for RPA-3.0 format.
 *
 * Format specification:
 * - Header: "RPA-3.0 " (8 bytes)
 * - Index offset: 16 hex digits (16 bytes)
 * - Space: 1 byte
 * - Obfuscation key: 8 hex digits (8 bytes)
 * - Newline: 1 byte
 * - Padding: optional text (e.g., "Made with Ren'Py.")
 * - At offset: Zlib-compressed Python pickle index
 * - File data: stored at offsets specified in index (XOR with key)
 *
 * Security note: RpaInspector parses the pickle format manually (binary protocol) without
 * executing Python code, so there's no arbitrary code execution risk.
 * This is safe for untrusted RPA archives.
 */
class RpaExtractor @Inject constructor(
    private val pathValidator: PathValidator,
    tempFileManager: ITempFileManager,
    sevenZipHelper: SevenZipExtractorHelper
) : BaseArchiveExtractor(tempFileManager, sevenZipHelper) {

    override fun supports(type: ArchiveType): Boolean = type == ArchiveType.RPA

    override fun getTag(): String = "RPA"

    override suspend fun extractInternal(
        inputStream: InputStream,
        destinationPath: File,
        archiveType: ArchiveType,
        sourceFileName: String,
        selectedItems: List<String>?,
        onProgress: (ExtractionProgress) -> Unit
    ): ExtractionResult = withContext(Dispatchers.IO) {
        var extractedCount = 0
        var tempFile: File? = null

        try {
            // RPA requires temp file for random access
            tempFile = tempFileManager.createTempFile(inputStream, archiveType, getTag())
            Timber.tag(getTag()).d("Created temp file: ${tempFile.absolutePath}")

            // Use RpaInspector to get file index (avoids duplicating pickle parsing)
            val inspector = RpaInspector(tempFile)
            val index = inspector.getRawFileEntries()
            inspector.close()
            Timber.tag(getTag()).d("Total files in archive: ${index.size}")

            // Pre-allocate large buffer for optimal I/O
            val buffer = ByteArray(BUFFER_SIZE_BYTES)

            // Progress throttler from base class
            val throttler = ProgressThrottler()

            // Extract each file
            tempFile.inputStream().use { archiveStream ->
                index.forEach { entry ->
                    if (!isActive) return@forEach

                    // Entry contains de-obfuscated offset/size from RpaInspector
                    val realOffset = entry.offset
                    val realSize = entry.size

                    Timber.tag(getTag()).d("Extracting: ${entry.name} (offset=$realOffset, size=$realSize)")

                    // Path traversal protection + directory creation
                    val outputFile = pathValidator.createSafeOutputFile(destinationPath, entry.name)

                    // Seek to file position (mark not supported, reopen stream)
                    tempFile.inputStream().use { fileStream ->
                        fileStream.skip(realOffset)

                        // Extract file data with progress updates
                        outputFile.outputStream().buffered(BUFFER_SIZE_BYTES).use { output ->
                            var remaining = realSize
                            var bytesExtracted = 0L

                            while (remaining > 0 && isActive) {
                                val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                                val bytesRead = fileStream.read(buffer, 0, toRead)
                                if (bytesRead == -1) break
                                output.write(buffer, 0, bytesRead)
                                remaining -= bytesRead
                                bytesExtracted += bytesRead

                                // Notify progress during file extraction (for large files)
                                if (throttler.shouldNotify()) {
                                    val fileProgress = bytesExtracted.toFloat() / realSize.toFloat()
                                    val overallProgress = (extractedCount.toFloat() + fileProgress) / index.size.toFloat()
                                    onProgress(
                                        ExtractionProgress.Extracting(
                                            currentFile = entry.name,
                                            extractedCount = extractedCount,
                                            totalCount = index.size,
                                            progress = overallProgress
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Check if cancelled during file extraction
                    if (!isActive) return@forEach

                    extractedCount++

                    // Use base class helper for throttled progress notifications
                    notifyProgress(extractedCount, index.size, entry.name, throttler, onProgress)
                }
            }

            // Check for cancellation
            if (!isActive) {
                Timber.tag(getTag()).w("Extraction cancelled")
                return@withContext ExtractionResult.Failure(
                    errorMessage = "Extraction cancelled",
                    cause = null
                )
            }

            logger.logComplete(extractedCount)

            ExtractionResult.Success(
                outputPath = destinationPath.absolutePath,
                extractedFilesCount = extractedCount
            )
        } catch (e: Exception) {
            Timber.tag(getTag()).e(e, "Failed to extract RPA archive")
            ExtractionResult.Failure(
                errorMessage = e.message ?: "Unknown error",
                cause = e
            )
        } finally {
            // Clean up temp file
            tempFile?.let {
                val deleted = it.delete()
                Timber.tag(getTag()).d("Temp file deleted: $deleted")
            }
        }
    }

    companion object {
        private const val BUFFER_SIZE_BYTES = 256 * 1024 // 256 KB
    }
}
