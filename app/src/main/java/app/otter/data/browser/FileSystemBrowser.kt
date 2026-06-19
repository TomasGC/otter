package app.otter.data.browser

import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.BrowseResult
import app.otter.domain.model.ResourcePath
import app.otter.util.MimeTypeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Browser for file system directories.
 *
 * Maps file system files and directories to BrowsableItem types with polymorphic
 * dispatch based on file type:
 * - isDirectory → FileSystemDirectory
 * - archive extension → ArchiveFile
 * - regular file → FileSystemFile
 *
 * This class performs file I/O operations on Dispatchers.IO for thread-safety.
 */
class FileSystemBrowser @Inject constructor(
    private val mimeTypeUtil: MimeTypeUtil
) {

    companion object {
        /**
         * Archive file extensions that should be mapped to ArchiveFile type.
         */
        val ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z", "tar", "gz", "rpa")
    }

    /**
     * Browses a file system directory and returns its contents as BrowsableItems.
     *
     * File system browsing always returns Complete results (no pagination) since
     * directories typically contain a manageable number of entries.
     *
     * @param path The file system path to browse
     * @return Result containing BrowseResult.Complete with list of items, or error
     */
    suspend fun browse(path: ResourcePath.FileSystem): Result<BrowseResult> = withContext(Dispatchers.IO) {
        try {
            val directory = File(path.path)

            if (!directory.exists()) {
                return@withContext Result.failure(IllegalArgumentException("Directory does not exist: ${path.path}"))
            }

            if (!directory.isDirectory) {
                return@withContext Result.failure(IllegalArgumentException("Path is not a directory: ${path.path}"))
            }

            val files = directory.listFiles()
                ?: return@withContext Result.failure(IllegalStateException("Cannot read directory: ${path.path}"))

            val items = files.map { file ->
                file.toBrowsableItem()
            }.sortedWith(compareBy({ !it.canNavigateInto }, { it.name }))

            Result.success(BrowseResult.Complete(items))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Returns the parent directory path, or null if at root.
     *
     * @param path The current file system path
     * @return The parent path, or null if at root
     */
    fun getParent(path: ResourcePath.FileSystem): ResourcePath? {
        val file = File(path.path)
        val parent = file.parentFile ?: return null

        return if (parent.path == file.path) {
            null // We're at root
        } else {
            ResourcePath.FileSystem(parent.absolutePath)
        }
    }

    /**
     * Checks if the path is the file system root.
     *
     * @param path The file system path to check
     * @return True if this is the root path
     */
    fun isRoot(path: ResourcePath.FileSystem): Boolean {
        val file = File(path.path)
        return file.parentFile == null || file.parentFile?.path == file.path
    }

    /**
     * Maps a File to a BrowsableItem with polymorphic type selection:
     * - Directory → FileSystemDirectory
     * - Archive file → ArchiveFile
     * - Regular file → FileSystemFile
     */
    private fun File.toBrowsableItem(): BrowsableItem {
        val isArchive = extension.lowercase() in ARCHIVE_EXTENSIONS

        return when {
            isDirectory -> {
                BrowsableItem.FileSystemDirectory(
                    path = ResourcePath.FileSystem(absolutePath),
                    name = name,
                    sizeBytes = 0L, // Directory size calculation is expensive
                    lastModified = lastModified()
                )
            }
            isArchive -> {
                val archivePath = ResourcePath.ArchiveEntry(
                    archivePath = absolutePath,
                    entryPath = "" // Empty = archive root
                )
                BrowsableItem.ArchiveFile(
                    path = archivePath,
                    name = name,
                    sizeBytes = length(),
                    lastModified = lastModified(),
                    archivePath = archivePath,
                    mimeType = mimeTypeUtil.getMimeType(name)
                )
            }
            else -> {
                BrowsableItem.FileSystemFile(
                    path = ResourcePath.FileSystem(absolutePath),
                    name = name,
                    sizeBytes = length(),
                    lastModified = lastModified(),
                    mimeType = mimeTypeUtil.getMimeType(name)
                )
            }
        }
    }
}
