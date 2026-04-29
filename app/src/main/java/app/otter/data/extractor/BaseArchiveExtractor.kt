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

    override suspend fun extract(
        inputStream: InputStream,
        destinationPath: File,
        archiveType: ArchiveType,
        sourceFileName: String,
        onProgress: (ExtractionProgress) -> Unit
    ): ExtractionResult = withContext(Dispatchers.IO) {
        var tempFile: File? = null

        try {
            // Create temporary file with proper extension for 7-Zip detection
            tempFile = createTempFile(inputStream, archiveType)

            // Delegate extraction to subclass
            extractFromTempFile(tempFile, destinationPath, sourceFileName, onProgress)
        } catch (e: CancellationException) {
            Timber.tag(getTag()).d("${getTag()} extraction cancelled")
            throw e // Re-throw to propagate cancellation
        } catch (e: Exception) {
            Timber.tag(getTag()).e(e, "${getTag()} extraction failed: ${e.message}")
            ExtractionResult.Failure(
                errorMessage = "${getTag()} extraction failed: ${e.message}",
                cause = e
            )
        } finally {
            tempFile?.delete()
            Timber.tag(getTag()).d("Temp file deleted")
        }
    }

    protected abstract suspend fun extractFromTempFile(
        tempFile: File,
        destinationPath: File,
        sourceFileName: String,
        onProgress: (ExtractionProgress) -> Unit
    ): ExtractionResult

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

    companion object {
        private const val LOG_INTERVAL_FILES = 100

        // Shared temp file constants for extractors requiring RandomAccessFile
        const val TEMP_FILE_PREFIX = "otter_archive_"
        const val TEMP_FILE_SUFFIX = ".tmp"
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
