package app.otter.data.extractor

import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject

class ZipExtractor @Inject constructor() : ArchiveExtractor {

    override fun supports(type: ArchiveType): Boolean = type == ArchiveType.ZIP

    override suspend fun extract(
        inputStream: InputStream,
        destinationPath: File,
        onProgress: (ExtractionProgress) -> Unit
    ): ExtractionResult = withContext(Dispatchers.IO) {
        try {
            // Read entire stream into memory (required for two-pass approach)
            val bytes = inputStream.readBytes()

            // First pass: count entries
            val entries = mutableListOf<ZipEntry>()
            ZipInputStream(bytes.inputStream()).use { zipStream ->
                var entry: ZipEntry? = zipStream.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        entries.add(entry)
                    }
                    entry = zipStream.nextEntry
                }
            }

            val totalCount = entries.size
            var extractedCount = 0

            // Second pass: extract files
            ZipInputStream(bytes.inputStream()).use { zipStream ->
                var entry: ZipEntry? = zipStream.nextEntry
                while (entry != null) {
                    // Process current entry
                    entry?.let { currentEntry ->
                        if (!currentEntry.isDirectory) {
                            val outputFile = File(destinationPath, currentEntry.name)

                            // Path traversal protection
                            if (!outputFile.canonicalPath.startsWith(destinationPath.canonicalPath)) {
                                throw SecurityException("Zip entry outside destination: ${currentEntry.name}")
                            }

                            outputFile.parentFile?.mkdirs()

                            FileOutputStream(outputFile).use { output ->
                                zipStream.copyTo(output)
                            }

                            extractedCount++
                            onProgress(
                                ExtractionProgress.Extracting(
                                    currentFile = currentEntry.name,
                                    extractedCount = extractedCount,
                                    totalCount = totalCount,
                                    progress = extractedCount.toFloat() / totalCount
                                )
                            )
                        }
                    }

                    // Move to next entry
                    entry = zipStream.nextEntry
                }
            }

            ExtractionResult.Success(
                outputPath = destinationPath.absolutePath,
                extractedFilesCount = extractedCount
            )
        } catch (e: Exception) {
            ExtractionResult.Failure(
                errorMessage = "Extraction failed: ${e.message}",
                cause = e
            )
        }
    }
}
