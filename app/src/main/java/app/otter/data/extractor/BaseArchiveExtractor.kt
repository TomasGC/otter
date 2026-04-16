package app.otter.data.extractor

import android.util.Log
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import app.otter.util.FileLogger
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
            FileLogger.log("${getTag()} extraction cancelled", getTag())
            throw e // Re-throw to propagate cancellation
        } catch (e: Exception) {
            FileLogger.logError("${getTag()} extraction failed: ${e.message}", e, getTag())
            Log.e(getTag(), "Extraction failed", e)
            ExtractionResult.Failure(
                errorMessage = "${getTag()} extraction failed: ${e.message}",
                cause = e
            )
        } finally {
            tempFile?.delete()
            FileLogger.log("Temp file deleted", getTag())
        }
    }

    protected abstract suspend fun extractFromTempFile(
        tempFile: File,
        destinationPath: File,
        onProgress: (ExtractionProgress) -> Unit
    ): ExtractionResult

    protected fun createTempFile(inputStream: InputStream): File {
        val tempFile = File.createTempFile(getFilePrefix(), getFileExtension())
        FileLogger.log("Created temp file: ${tempFile.absolutePath}", getTag())
        Log.d(getTag(), "Created temp file: ${tempFile.absolutePath}")

        var bytesCopied = 0L
        tempFile.outputStream().use { output ->
            bytesCopied = inputStream.copyTo(output)
        }
        FileLogger.log("Copied $bytesCopied bytes to temp file. File size: ${tempFile.length()}", getTag())
        Log.d(getTag(), "Copied $bytesCopied bytes to temp file. File size: ${tempFile.length()}")

        if (!tempFile.exists() || tempFile.length() == 0L) {
            val error = "Temp file is empty or doesn't exist"
            FileLogger.logError(error, null, getTag())
            throw IllegalStateException(error)
        }

        return tempFile
    }

    protected fun validatePath(outputFile: File, destinationPath: File, entryName: String) {
        if (!outputFile.canonicalPath.startsWith(destinationPath.canonicalPath)) {
            val error = "Entry outside destination: $entryName"
            FileLogger.logError(error, null, getTag())
            throw SecurityException(error)
        }
    }

    protected fun logExtractionProgress(extractedCount: Int, totalCount: Int, fileName: String) {
        // Only log every 100 files to avoid performance issues
        if (extractedCount % 100 == 0 || extractedCount == totalCount) {
            FileLogger.log("Extracted $extractedCount/$totalCount files (current: $fileName)", getTag())
        }
    }

    protected fun logExtractionComplete(extractedCount: Int) {
        FileLogger.log("${getTag()} extraction completed: $extractedCount files", getTag())
        Log.d(getTag(), "Extraction completed: $extractedCount files")
    }

    protected abstract fun getFilePrefix(): String
    protected abstract fun getFileExtension(): String
}
