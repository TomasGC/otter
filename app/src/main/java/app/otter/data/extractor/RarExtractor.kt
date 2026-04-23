package app.otter.data.extractor

import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import app.otter.util.PathValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IArchiveExtractCallback
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import javax.inject.Inject

class RarExtractor @Inject constructor(
    private val pathValidator: PathValidator
) : ArchiveExtractor {

    override fun supports(type: ArchiveType): Boolean = type == ArchiveType.RAR

    override suspend fun extract(
        inputStream: InputStream,
        destinationPath: File,
        onProgress: (ExtractionProgress) -> Unit
    ): ExtractionResult = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        var inArchive: IInArchive? = null

        try {
            // Create temporary file (7-Zip-JBinding requires RandomAccessFile)
            tempFile = File.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX)
            tempFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }

            // Open archive with 7-Zip-JBinding (auto-detects RAR4/RAR5)
            val randomAccessFile = RandomAccessFile(tempFile, "r")
            inArchive = SevenZip.openInArchive(null, RandomAccessFileInStream(randomAccessFile))

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

                    val isDirectory = inArchive?.getProperty(index, PropID.IS_FOLDER) as? Boolean ?: false
                    if (isDirectory) {
                        return null
                    }

                    val path = inArchive?.getProperty(index, PropID.PATH) as? String ?: return null

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
                        val isDirectory = inArchive?.getProperty(currentIndex, PropID.IS_FOLDER) as? Boolean ?: false
                        if (!isDirectory) {
                            extractedCount++
                            val path = inArchive?.getProperty(currentIndex, PropID.PATH) as? String ?: "unknown"

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

            ExtractionResult.Success(
                outputPath = destinationPath.absolutePath,
                extractedFilesCount = extractedCount
            )
        } catch (e: Exception) {
            ExtractionResult.Failure(
                errorMessage = "RAR extraction failed: ${e.message}",
                cause = e
            )
        } finally {
            inArchive?.close()
            tempFile?.delete()
        }
    }

    companion object {
        private const val TEMP_FILE_PREFIX = "otter_rar_"
        private const val TEMP_FILE_SUFFIX = ".rar"
    }
}
