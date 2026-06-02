package app.otter.domain.model

/**
 * Represents a path that can be browsed - either a file system path or an archive entry path.
 *
 * This sealed class enables type-safe polymorphic dispatch when handling different path types
 * in the archive browsing feature. The compiler enforces exhaustive when expressions.
 */
sealed class ResourcePath {

    /**
     * Represents a file system path (e.g., /storage/emulated/0/Download).
     *
     * @property path The absolute file system path
     */
    data class FileSystem(val path: String) : ResourcePath()

    /**
     * Represents an entry path within an archive file.
     *
     * @property archivePath The absolute path to the archive file
     * @property entryPath The path within the archive (empty string = archive root)
     */
    data class ArchiveEntry(
        val archivePath: String,
        val entryPath: String = ""
    ) : ResourcePath()
}
