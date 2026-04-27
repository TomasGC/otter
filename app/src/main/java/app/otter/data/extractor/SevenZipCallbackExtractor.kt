package app.otter.data.extractor

import app.otter.util.PathValidator
import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IArchiveExtractCallback
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import java.io.File
import java.io.FileOutputStream

/**
 * Reusable extraction callback for 7-Zip-JBinding archives (RAR, 7z, etc.).
 *
 * Handles progress tracking, path traversal protection, and file extraction.
 *
 * @param inArchive The opened archive to extract from
 * @param destinationPath Output directory for extracted files
 * @param pathValidator Validates file paths against traversal attacks
 * @param onProgress Callback invoked after each file extraction (extractedCount, totalCount, fileName)
 */
internal class SevenZipCallbackExtractor(
    private val inArchive: IInArchive,
    private val destinationPath: File,
    private val pathValidator: PathValidator,
    private val onProgress: (extractedCount: Int, totalCount: Int, fileName: String) -> Unit
) : IArchiveExtractCallback {

    companion object {
        private const val UNKNOWN_FILE_NAME = "unknown"
    }

    private var currentIndex = 0
    private var currentOutputStream: FileOutputStream? = null
    private var extractedCount = 0

    val totalCount: Int = inArchive.numberOfItems

    override fun setTotal(total: Long) {
        // Total bytes - not needed for file count-based progress
    }

    override fun setCompleted(completeValue: Long) {
        // Bytes completed - not needed for file count-based progress
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
        // Called before extraction - no-op
    }

    override fun setOperationResult(extractOperationResult: ExtractOperationResult) {
        currentOutputStream?.close()
        currentOutputStream = null

        if (extractOperationResult == ExtractOperationResult.OK) {
            val isDirectory = inArchive.getProperty(currentIndex, PropID.IS_FOLDER) as? Boolean ?: false
            if (!isDirectory) {
                extractedCount++
                val path = inArchive.getProperty(currentIndex, PropID.PATH) as? String ?: UNKNOWN_FILE_NAME

                onProgress(extractedCount, totalCount, path)
            }
        }
    }

    /**
     * Returns the number of files successfully extracted.
     */
    fun getExtractedCount(): Int = extractedCount
}
