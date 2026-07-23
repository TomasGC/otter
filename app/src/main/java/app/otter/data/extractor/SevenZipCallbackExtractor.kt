package app.otter.data.extractor

import app.otter.util.PathValidator
import kotlinx.coroutines.CancellationException
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
 * @param selectedPaths Set of paths to extract selectively, or null to extract all
 * @param config Extraction-session collaborators (size guard, cancellation check, progress callback)
 */
internal class SevenZipCallbackExtractor(
    private val inArchive: IInArchive,
    private val destinationPath: File,
    private val pathValidator: PathValidator,
    private val selectedPaths: Set<String>? = null,
    private val config: Config = Config()
) : IArchiveExtractCallback {

    internal class Config(
        val sizeGuard: ArchiveSizeGuard = ArchiveSizeGuard(),
        val isActiveCheck: () -> Boolean = { true },
        val onProgress: (extractedCount: Int, totalCount: Int, fileName: String) -> Unit = { _, _, _ -> }
    )

    companion object {
        private const val UNKNOWN_FILE_NAME = "unknown"
    }

    private fun isEntrySelected(entryName: String): Boolean {
        if (selectedPaths == null) return true
        if (selectedPaths.contains(entryName)) return true
        return selectedPaths.any { path -> path.endsWith("/") && entryName.startsWith(path) }
    }

    private var currentIndex = 0
    private var currentIndexWasWritten = false
    private var currentOutputStream: FileOutputStream? = null
    private var extractedCount = 0
    private var hadErrors = false

    val totalCount: Int = inArchive.numberOfItems

    override fun setTotal(total: Long) {
        // Total bytes - not needed for file count-based progress
    }

    override fun setCompleted(completeValue: Long) {
        // Bytes completed - not needed for file count-based progress
    }

    override fun getStream(index: Int, extractAskMode: ExtractAskMode): ISequentialOutStream? {
        currentIndex = index
        currentIndexWasWritten = false

        if (!config.isActiveCheck()) {
            throw CancellationException("Extraction cancelled")
        }

        if (extractAskMode != ExtractAskMode.EXTRACT) {
            return null
        }

        val isDirectory = inArchive.getProperty(index, PropID.IS_FOLDER) as? Boolean ?: false
        if (isDirectory) {
            return null
        }

        val path = inArchive.getProperty(index, PropID.PATH) as? String ?: return null

        if (!isEntrySelected(path)) return null

        // Path traversal protection + directory creation
        val outputFile = pathValidator.createSafeOutputFile(destinationPath, path)
        currentOutputStream = FileOutputStream(outputFile)
        currentIndexWasWritten = true
        config.sizeGuard.startEntry()

        return ISequentialOutStream { data ->
            if (!config.isActiveCheck()) {
                throw CancellationException("Extraction cancelled")
            }
            config.sizeGuard.track(data.size)
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
            // 7-Zip reports OK even for entries getStream() skipped (directories, unselected
            // entries) — only count entries we actually wrote to disk.
            if (currentIndexWasWritten) {
                extractedCount++
                val path = inArchive.getProperty(currentIndex, PropID.PATH) as? String ?: UNKNOWN_FILE_NAME

                config.onProgress(extractedCount, totalCount, path)
            }
        } else {
            hadErrors = true
        }
    }

    /**
     * Returns the number of files successfully extracted.
     */
    fun getExtractedCount(): Int = extractedCount

    /**
     * Returns true if any entry reported a non-OK operation result
     * (e.g. CRC/data error from a corrupted archive).
     */
    fun hasErrors(): Boolean = hadErrors
}
