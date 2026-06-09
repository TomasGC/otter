package app.otter.domain.inspector

/**
 * Represents metadata about a single entry within an archive.
 *
 * @property path The relative path of the entry within the archive (e.g., "documents/report.pdf")
 * @property isDirectory Whether this entry represents a directory
 * @property sizeBytes The uncompressed size of the entry in bytes
 * @property compressedSize The compressed size of the entry in bytes
 * @property lastModified Last modified timestamp in milliseconds
 */
data class ArchiveEntry(
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val compressedSize: Long,
    val lastModified: Long
)
