package app.otter.data.extractor

import android.util.Log
import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import app.otter.util.PathValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.inject.Inject

class ZipExtractor @Inject constructor() : ArchiveExtractor {

    override fun supports(type: ArchiveType): Boolean = type == ArchiveType.ZIP

    override suspend fun extract(
        inputStream: InputStream,
        destinationPath: File,
        onProgress: (ExtractionProgress) -> Unit
    ): ExtractionResult = withContext(Dispatchers.IO) {
        var extractedCount = 0
        var tempFile: File? = null

        try {
            // Create temp file to enable counting
            tempFile = File.createTempFile("otter_zip_", ".zip")
            tempFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            Log.d(TAG, "Created temp file: ${tempFile.absolutePath}, size: ${tempFile.length()}")

            // Count total entries using ZipFile (allows random access)
            val totalCount = ZipFile(tempFile).use { zipFile ->
                zipFile.entries().asSequence().count { !it.isDirectory }
            }
            Log.d(TAG, "Total files in archive: $totalCount")

            // Pre-allocate large buffer for optimal I/O
            val buffer = ByteArray(BUFFER_SIZE_BYTES)

            // Extract from temp file
            ZipFile(tempFile).use { zipFile ->
                val entries = zipFile.entries()
                var lastNotificationTime = 0L

                while (entries.hasMoreElements() && isActive) {
                    val entry = entries.nextElement()
                    if (!entry.isDirectory) {
                        // Path traversal protection + directory creation
                        val outputFile = PathValidator.createSafeOutputFile(destinationPath, entry.name)

                        // Extract using ZipFile.getInputStream (more reliable than stream)
                        zipFile.getInputStream(entry).use { input ->
                            outputFile.outputStream().buffered(BUFFER_SIZE_BYTES).use { output ->
                                var bytesRead: Int
                                while (input.read(buffer).also { bytesRead = it } != -1 && isActive) {
                                    output.write(buffer, 0, bytesRead)
                                }
                            }
                        }

                        // Check if cancelled during file extraction
                        if (!isActive) break

                        extractedCount++

                        // Throttle notifications to reduce overhead
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastNotificationTime > PROGRESS_THROTTLE_MS) {
                            lastNotificationTime = currentTime
                            val progress = if (totalCount > 0) extractedCount.toFloat() / totalCount else 0f
                            onProgress(
                                ExtractionProgress.Extracting(
                                    currentFile = entry.name,
                                    extractedCount = extractedCount,
                                    totalCount = totalCount,
                                    progress = progress
                                )
                            )
                        }
                    }
                }
            }

            ExtractionResult.Success(
                outputPath = destinationPath.absolutePath,
                extractedFilesCount = extractedCount
            )
        } catch (e: CancellationException) {
            throw e // Re-throw to propagate cancellation
        } catch (e: Exception) {
            Log.e(TAG, "ZIP extraction failed: ${e.message}", e)
            ExtractionResult.Failure(
                errorMessage = "Extraction failed: ${e.message}",
                cause = e
            )
        } finally {
            // Clean up temp file
            tempFile?.delete()
        }
    }

    companion object {
        private const val TAG = "ZipExtractor"
        private const val BUFFER_SIZE_BYTES = 256 * 1024 // 256 KB
        private const val PROGRESS_THROTTLE_MS = 1000L // 1 second
    }
}
