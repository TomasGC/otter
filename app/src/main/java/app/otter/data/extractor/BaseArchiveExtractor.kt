package app.otter.data.extractor

import timber.log.Timber
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

abstract class BaseArchiveExtractor : ArchiveExtractor {

    protected abstract fun getTag(): String

    override suspend fun extract(
        inputStream: InputStream,
        destinationPath: File,
        onProgress: (ExtractionProgress) -> Unit
    ): ExtractionResult = withContext(Dispatchers.IO) {
        var tempFile: File? = null

        try {
            // Create temporary file
            tempFile = createTempFile(inputStream)

            // Delegate extraction to subclass
            extractFromTempFile(tempFile, destinationPath, onProgress)
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
        onProgress: (ExtractionProgress) -> Unit
    ): ExtractionResult

    protected fun createTempFile(inputStream: InputStream): File {
        val tempFile = File.createTempFile(getFilePrefix(), getFileExtension())
        Timber.tag(getTag()).d("Created temp file: ${tempFile.absolutePath}")

        var bytesCopied = 0L
        tempFile.outputStream().use { output ->
            bytesCopied = inputStream.copyTo(output)
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
        if (extractedCount % 100 == 0 || extractedCount == totalCount) {
            Timber.tag(getTag()).d("Extracted $extractedCount/$totalCount files (current: $fileName)")
        }
    }

    protected fun logExtractionComplete(extractedCount: Int) {
        Timber.tag(getTag()).d("${getTag()} extraction completed: $extractedCount files")
        Timber.tag(getTag()).d("Extraction completed: $extractedCount files")
    }

    protected abstract fun getFilePrefix(): String
    protected abstract fun getFileExtension(): String
}
