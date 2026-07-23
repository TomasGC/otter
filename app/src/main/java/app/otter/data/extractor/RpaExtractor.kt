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
    sevenZipHelper: SevenZipExtractorHelper,
    private val sizeGuardFactory: () -> ArchiveSizeGuard = { ArchiveSizeGuard() }
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
            val inspector = RpaInspector.from(tempFile)
            val index = inspector.getRawFileEntries()
            inspector.close()
            Timber.tag(getTag()).d("Total files in archive: ${index.size}")

            // Pre-allocate large buffer for optimal I/O
            val buffer = ByteArray(BUFFER_SIZE_BYTES)

            // Progress throttler from base class
            val throttler = ProgressThrottler()

            // Guards against zip-bomb entries (decompressed size far exceeding declared/expected size)
            val sizeGuard = sizeGuardFactory()

            // Extract each file
            val selectedPaths = selectedItems?.toSet()

            tempFile.inputStream().use {
                index.forEach { entry ->
                    if (!isActive) return@forEach
                    if (!isEntrySelected(entry.name, selectedPaths)) return@forEach
                    Timber.tag(getTag()).d("Extracting: ${entry.name} (offset=${entry.offset}, size=${entry.size})")
                    val outputFile = pathValidator.createSafeOutputFile(destinationPath, entry.name)
                    sizeGuard.startEntry()
                    extractEntryData(
                        tempFile,
                        EntryExtractCtx(entry.name, entry.offset, entry.size, extractedCount, index.size, outputFile),
                        ExtractionSession(buffer, throttler, onProgress, sizeGuard) { isActive }
                    )
                    if (!isActive) return@forEach
                    extractedCount++
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

    private data class EntryExtractCtx(
        val name: String, val offset: Long, val size: Long,
        val extractedCount: Int, val totalCount: Int,
        val outputFile: File
    )

    // Bundles the per-extraction (not per-entry) collaborators shared across every entry's
    // read/write loop. Plain class, not data class: buffer/onProgress/isActiveCheck have no
    // meaningful value-equality, so auto-generated equals()/hashCode() would be misleading.
    private class ExtractionSession(
        val buffer: ByteArray,
        val throttler: ProgressThrottler,
        val onProgress: (ExtractionProgress) -> Unit,
        val sizeGuard: ArchiveSizeGuard,
        val isActiveCheck: () -> Boolean
    )

    private fun extractEntryData(tempFile: File, ctx: EntryExtractCtx, session: ExtractionSession) {
        tempFile.inputStream().use { fileStream ->
            fileStream.skip(ctx.offset)
            writeEntryToFile(fileStream, ctx, session)
        }
    }

    private fun writeEntryToFile(fileStream: java.io.InputStream, ctx: EntryExtractCtx, session: ExtractionSession) {
        ctx.outputFile.outputStream().buffered(BUFFER_SIZE_BYTES).use { output ->
            var remaining = ctx.size
            var bytesExtracted = 0L
            while (remaining > 0 && session.isActiveCheck()) {
                val toRead = minOf(remaining, session.buffer.size.toLong()).toInt()
                val bytesRead = fileStream.read(session.buffer, 0, toRead)
                if (bytesRead == -1) break
                session.sizeGuard.track(bytesRead)
                output.write(session.buffer, 0, bytesRead)
                remaining -= bytesRead
                bytesExtracted += bytesRead
                if (session.throttler.shouldNotify()) notifyChunkProgress(ctx, bytesExtracted, session.onProgress)
            }
        }
    }

    private fun notifyChunkProgress(ctx: EntryExtractCtx, bytesExtracted: Long, onProgress: (ExtractionProgress) -> Unit) {
        val fileProgress = bytesExtracted.toFloat() / ctx.size.toFloat()
        val overallProgress = (ctx.extractedCount.toFloat() + fileProgress) / ctx.totalCount.toFloat()
        onProgress(ExtractionProgress.Extracting(
            currentFile = ctx.name, extractedCount = ctx.extractedCount,
            totalCount = ctx.totalCount, progress = overallProgress
        ))
    }

    companion object {
        private const val BUFFER_SIZE_BYTES = 256 * 1024 // 256 KB
    }
}
