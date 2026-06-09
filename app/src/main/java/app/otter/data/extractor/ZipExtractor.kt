package app.otter.data.extractor

import timber.log.Timber
import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import app.otter.util.PathValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import javax.inject.Inject

class ZipExtractor @Inject constructor(
    private val pathValidator: PathValidator,
    tempFileManager: ITempFileManager,
    sevenZipHelper: SevenZipExtractorHelper,
    private val zipFileReaderFactory: IZipFileReaderFactory = RealZipFileReaderFactory()
) : BaseArchiveExtractor(tempFileManager, sevenZipHelper) {

    override fun supports(type: ArchiveType): Boolean = type == ArchiveType.ZIP

    override fun getTag(): String = "ZIP"

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
            // Use base class helper to create temp file with validation
            tempFile = tempFileManager.createTempFile(inputStream, archiveType, getTag())

            // Count total entries using ZipFileReader abstraction
            val totalCount = zipFileReaderFactory.create(tempFile).use { reader ->
                reader.countFiles()
            }
            Timber.tag(getTag()).d("Total files in archive: $totalCount")

            // Pre-allocate large buffer for optimal I/O
            val buffer = ByteArray(BUFFER_SIZE_BYTES)

            // Progress throttler from base class
            val throttler = ProgressThrottler()

            // Convert selectedItems to Set for O(1) lookup if provided
            val selectedPaths = selectedItems?.toSet()

            // Extract from temp file using ZipFileReader abstraction
            zipFileReaderFactory.create(tempFile).use { reader ->
                val entries = reader.getEntries()

                for (entry in entries) {
                    if (!isActive) break

                    // Skip if selective extraction and entry not selected
                    if (!isEntrySelected(entry.name, selectedPaths)) {
                        continue
                    }

                    // Path traversal protection + directory creation
                    val outputFile = pathValidator.createSafeOutputFile(destinationPath, entry.name)

                    // Extract using reader
                    reader.getInputStream(entry).use { input ->
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

                    // Use base class helper for throttled progress notifications
                    notifyProgress(extractedCount, totalCount, entry.name, throttler, onProgress)
                }
            }

            logger.logComplete(extractedCount)

            ExtractionResult.Success(
                outputPath = destinationPath.absolutePath,
                extractedFilesCount = extractedCount
            )
        } finally {
            // Clean up temp file
            tempFile?.delete()
            Timber.tag(getTag()).d("Temp file deleted")
        }
    }
}
