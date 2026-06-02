package app.otter.domain.model

/**
 * Represents an item that can be browsed in the file browser UI.
 *
 * This sealed class enables type-safe polymorphic rendering of different item types:
 * - Regular file system files and directories
 * - Archive files (which can be navigated into)
 * - Directories within archives
 *
 * The [canNavigateInto] property determines whether the user can navigate into this item.
 */
sealed class BrowsableItem {
    abstract val path: ResourcePath
    abstract val name: String
    abstract val sizeBytes: Long
    abstract val lastModified: Long
    abstract val canNavigateInto: Boolean

    /**
     * Represents a regular file system directory.
     *
     * This type is navigable - tapping it will display its contents.
     *
     * @property path The file system path to the directory
     * @property name The directory name (e.g., "Download")
     * @property sizeBytes The total size of the directory (0 if not calculated)
     * @property lastModified The last modification timestamp (Unix epoch milliseconds)
     */
    data class FileSystemDirectory(
        override val path: ResourcePath.FileSystem,
        override val name: String,
        override val sizeBytes: Long,
        override val lastModified: Long
    ) : BrowsableItem() {
        override val canNavigateInto: Boolean = true
    }

    /**
     * Represents a regular file system file (not an archive).
     *
     * This type is NOT navigable - tapping it may trigger a share/open action.
     *
     * @property path The file system path to the file
     * @property name The file name (e.g., "document.pdf")
     * @property sizeBytes The file size in bytes
     * @property lastModified The last modification timestamp (Unix epoch milliseconds)
     * @property mimeType The MIME type of the file (e.g., "application/pdf"), null if unknown
     */
    data class FileSystemFile(
        override val path: ResourcePath.FileSystem,
        override val name: String,
        override val sizeBytes: Long,
        override val lastModified: Long,
        val mimeType: String?
    ) : BrowsableItem() {
        override val canNavigateInto: Boolean = false
    }

    /**
     * Represents an archive file on the file system.
     *
     * This type is navigable - tapping it will browse into the archive contents.
     *
     * @property path The resource path (ArchiveEntry with empty entryPath = archive root)
     * @property name The archive file name (e.g., "archive.zip")
     * @property sizeBytes The archive file size in bytes
     * @property lastModified The last modification timestamp (Unix epoch milliseconds)
     * @property archivePath The archive entry path pointing to the archive root
     * @property mimeType The MIME type of the archive (e.g., "application/zip"), null if unknown
     */
    data class ArchiveFile(
        override val path: ResourcePath.ArchiveEntry,
        override val name: String,
        override val sizeBytes: Long,
        override val lastModified: Long,
        val archivePath: ResourcePath.ArchiveEntry,
        val mimeType: String?
    ) : BrowsableItem() {
        override val canNavigateInto: Boolean = true
    }

    /**
     * Represents a directory within an archive file.
     *
     * This type is navigable - tapping it will display the directory's contents within the archive.
     *
     * @property path The resource path pointing to the directory within the archive
     * @property name The directory name (e.g., "folder")
     * @property sizeBytes The total size of the directory (0 if not calculated)
     * @property lastModified The last modification timestamp (Unix epoch milliseconds)
     * @property archivePath The archive entry path pointing to this directory
     */
    data class ArchiveDirectory(
        override val path: ResourcePath.ArchiveEntry,
        override val name: String,
        override val sizeBytes: Long,
        override val lastModified: Long,
        val archivePath: ResourcePath.ArchiveEntry
    ) : BrowsableItem() {
        override val canNavigateInto: Boolean = true
    }

    /**
     * Represents a regular file within an archive file.
     *
     * This type is NOT navigable - tapping it may trigger extraction/preview.
     *
     * @property path The resource path pointing to the file within the archive
     * @property name The file name (e.g., "document.pdf")
     * @property sizeBytes The uncompressed file size in bytes
     * @property lastModified The last modification timestamp (Unix epoch milliseconds)
     * @property archivePath The archive entry path pointing to this file
     * @property mimeType The MIME type of the file (e.g., "application/pdf"), null if unknown
     */
    data class ArchiveFileEntry(
        override val path: ResourcePath.ArchiveEntry,
        override val name: String,
        override val sizeBytes: Long,
        override val lastModified: Long,
        val archivePath: ResourcePath.ArchiveEntry,
        val mimeType: String?
    ) : BrowsableItem() {
        override val canNavigateInto: Boolean = false
    }
}
