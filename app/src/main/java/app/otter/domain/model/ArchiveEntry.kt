package app.otter.domain.model

/**
 * Represents an entry (file or directory) inside an archive.
 *
 * @property path Full path inside archive (e.g., "folder/subfolder/file.txt")
 * @property name Display name (e.g., "file.txt")
 * @property isDirectory True if this is a directory
 * @property sizeBytes Uncompressed size in bytes (null for directories)
 * @property compressedSize Compressed size in bytes (null for directories)
 * @property lastModified Last modified timestamp in milliseconds
 */
data class ArchiveEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long?,
    val compressedSize: Long?,
    val lastModified: Long,
)
