package app.otter.data.extractor

import android.util.Log
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
            Log.d(getTag(), "${getTag()} extraction cancelled")
            throw e // Re-throw to propagate cancellation
        } catch (e: Exception) {
            Log.e(getTag(), "${getTag()} extraction failed: ${e.message}", e)
            ExtractionResult.Failure(
                errorMessage = "${getTag()} extraction failed: ${e.message}",
                cause = e
            )
        } finally {
            tempFile?.delete()
            Log.d(getTag(), "Temp file deleted")
        }
    }

    protected abstract suspend fun extractFromTempFile(
        tempFile: File,
        destinationPath: File,
        onProgress: (ExtractionProgress) -> Unit
    ): ExtractionResult

    protected fun createTempFile(inputStream: InputStream): File {
        val tempFile = File.createTempFile(getFilePrefix(), getFileExtension())
        Log.d(getTag(), "Created temp file: ${tempFile.absolutePath}")

        var bytesCopied = 0L
        tempFile.outputStream().use { output ->
            bytesCopied = inputStream.copyTo(output)
        }
        Log.d(getTag(), "Copied $bytesCopied bytes to temp file. File size: ${tempFile.length()}")

        if (!tempFile.exists() || tempFile.length() == 0L) {
            val error = "Temp file is empty or doesn't exist"
            Log.e(getTag(), error)
            throw IllegalStateException(error)
        }

        return tempFile
    }

    protected fun validatePath(outputFile: File, destinationPath: File, entryName: String) {
        if (!outputFile.canonicalPath.startsWith(destinationPath.canonicalPath)) {
            val error = "Entry outside destination: $entryName"
            Log.e(getTag(), error)
            throw SecurityException(error)
        }
    }

    protected fun logExtractionProgress(extractedCount: Int, totalCount: Int, fileName: String) {
        // Only log every 100 files to avoid performance issues
        if (extractedCount % 100 == 0 || extractedCount == totalCount) {
            Log.d(getTag(), "Extracted $extractedCount/$totalCount files (current: $fileName)")
        }
    }

    protected fun logExtractionComplete(extractedCount: Int) {
        Log.d(getTag(), "${getTag()} extraction completed: $extractedCount files")
        Log.d(getTag(), "Extraction completed: $extractedCount files")
    }

    protected abstract fun getFilePrefix(): String
    protected abstract fun getFileExtension(): String
}
