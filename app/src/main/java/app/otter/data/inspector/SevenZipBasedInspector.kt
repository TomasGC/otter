package app.otter.data.inspector

import app.otter.data.extractor.ArchiveLibraryManager
import app.otter.domain.inspector.ArchiveEntry
import app.otter.domain.inspector.ArchiveInspector
import app.otter.domain.inspector.ArchiveType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.PropID
import java.io.File
import java.util.Date

class SevenZipBasedInspector(
    private val file: File,
    private val archiveType: ArchiveType,
    private val libraryManager: ArchiveLibraryManager
) : ArchiveInspector {

    private var closed = false
    private var inArchive: IInArchive? = null

    @Synchronized
    private fun getOrOpenArchive(): IInArchive {
        if (inArchive == null) {
            inArchive = libraryManager.openArchive(file)
        }
        return inArchive!!
    }

    override suspend fun countEntries(): Int = withContext(Dispatchers.IO) {
        checkNotClosed()
        getOrOpenArchive().numberOfItems
    }

    override fun entries(): Sequence<ArchiveEntry> {
        checkNotClosed()
        val archive = getOrOpenArchive()
        return sequence {
            for (i in 0 until archive.numberOfItems) {
                val isDirectory = (archive.getProperty(i, PropID.IS_FOLDER) as? Boolean) ?: false
                val rawPath = (archive.getProperty(i, PropID.PATH) as? String) ?: ""
                // 7-Zip-JBinding reports folder paths without a trailing slash, unlike TAR/ZIP.
                // Normalize so ArchiveBrowser's implicit-directory synthesis doesn't double-count.
                val path = if (isDirectory && !rawPath.endsWith("/")) "$rawPath/" else rawPath
                yield(
                    ArchiveEntry(
                        path = path,
                        isDirectory = isDirectory,
                        sizeBytes = (archive.getProperty(i, PropID.SIZE) as? Long) ?: 0L,
                        compressedSize = (archive.getProperty(i, PropID.PACKED_SIZE) as? Long) ?: 0L,
                        lastModified = (archive.getProperty(i, PropID.LAST_MODIFICATION_TIME) as? Date)?.time ?: 0L
                    )
                )
            }
        }
    }

    override fun isEncrypted(): Boolean {
        checkNotClosed()
        return (getOrOpenArchive().getArchiveProperty(PropID.ENCRYPTED) as? Boolean) ?: false
    }

    override fun getArchiveType(): ArchiveType = archiveType

    @Synchronized
    override fun close() {
        if (!closed) {
            inArchive?.close()
            inArchive = null
            closed = true
        }
    }

    private fun checkNotClosed() {
        check(!closed) { "SevenZipBasedInspector is closed" }
    }
}
