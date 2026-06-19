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
                for (entry in reader.getEntries().filter { isEntrySelected(it.name, selectedPaths) }) {
                    if (!isActive) break

                    val outputFile = pathValidator.createSafeOutputFile(destinationPath, entry.name)

                    reader.getInputStream(entry).use { input ->
                        outputFile.outputStream().buffered(BUFFER_SIZE_BYTES).use { output ->
                            while (true) {
                                val bytesRead = input.read(buffer)
                                if (bytesRead == -1 || !isActive) break
                                output.write(buffer, 0, bytesRead)
                            }
                        }
                    }

                    if (isActive) {
                        extractedCount++
                        notifyProgress(extractedCount, totalCount, entry.name, throttler, onProgress)
                    }
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
