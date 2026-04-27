package app.otter.data.extractor

import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import app.otter.util.PathValidator
import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IArchiveExtractCallback
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class SevenZipExtractor @Inject constructor(
    private val pathValidator: PathValidator,
    private val archiveLibraryManager: ArchiveLibraryManager
) : BaseArchiveExtractor() {

    companion object {
        private const val UNKNOWN_FILE_NAME = "unknown"
    }

    override fun supports(type: ArchiveType): Boolean = type == ArchiveType.SEVEN_ZIP

    override fun getTag(): String = "7ZIP"

    override suspend fun extractFromTempFile(
        tempFile: File,
        destinationPath: File,
        onProgress: (ExtractionProgress) -> Unit
    ): ExtractionResult {
        var inArchive: IInArchive? = null

        try {
            // Open archive via manager (singleton handles native library lifecycle)
            inArchive = archiveLibraryManager.openArchive(tempFile)

            val totalCount = inArchive.numberOfItems
            var extractedCount = 0

            // Extract with callback for progress tracking
            val extractCallback = object : IArchiveExtractCallback {
                private var currentIndex = 0
                private var currentOutputStream: FileOutputStream? = null

                override fun setTotal(total: Long) {
                    // Total bytes - not needed for our progress
                }

                override fun setCompleted(completeValue: Long) {
                    // Bytes completed - not needed for our progress
                }

                override fun getStream(index: Int, extractAskMode: ExtractAskMode): ISequentialOutStream? {
                    currentIndex = index

                    if (extractAskMode != ExtractAskMode.EXTRACT) {
                        return null
                    }

                    val isDirectory = inArchive.getProperty(index, PropID.IS_FOLDER) as? Boolean ?: false
                    if (isDirectory) {
                        return null
                    }

                    val path = inArchive.getProperty(index, PropID.PATH) as? String ?: return null

                    // Path traversal protection + directory creation
                    val outputFile = pathValidator.createSafeOutputFile(destinationPath, path)
                    currentOutputStream = FileOutputStream(outputFile)

                    return ISequentialOutStream { data ->
                        currentOutputStream?.write(data)
                        data.size
                    }
                }

                override fun prepareOperation(extractAskMode: ExtractAskMode) {
                    // Called before extraction
                }

                override fun setOperationResult(extractOperationResult: ExtractOperationResult) {
                    currentOutputStream?.close()
                    currentOutputStream = null

                    if (extractOperationResult == ExtractOperationResult.OK) {
                        val isDirectory = inArchive.getProperty(currentIndex, PropID.IS_FOLDER) as? Boolean ?: false
                        if (!isDirectory) {
                            extractedCount++
                            val path = inArchive.getProperty(currentIndex, PropID.PATH) as? String ?: UNKNOWN_FILE_NAME

                            logExtractionProgress(extractedCount, totalCount, path)
                            onProgress(
                                ExtractionProgress.Extracting(
                                    currentFile = path,
                                    extractedCount = extractedCount,
                                    totalCount = totalCount,
                                    progress = if (totalCount > 0) extractedCount.toFloat() / totalCount else 0f
                                )
                            )
                        }
                    }
                }
            }

            // Extract all items
            inArchive.extract(null, false, extractCallback)

            logExtractionComplete(extractedCount)

            return ExtractionResult.Success(
                outputPath = destinationPath.absolutePath,
                extractedFilesCount = extractedCount
            )
        } finally {
            inArchive?.close()
        }
    }
}
