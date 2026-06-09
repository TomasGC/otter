package app.otter.domain.model

/**
 * Represents an entry (file or directory) inside an archive.
 *
 * @property path Full path inside archive (e.g., "folder/subfolder/file.txt")
 * @property isDirectory True if this is a directory
 * @property sizeBytes Uncompressed size in bytes
 * @property compressedSize Compressed size in bytes
 * @property lastModified Last modified timestamp in milliseconds
 */
data class ArchiveEntry(
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val compressedSize: Long,
    val lastModified: Long,
) {
    init {
        require(path.isNotBlank()) { "Path cannot be blank" }
        require(!path.contains("..")) { "Path traversal not allowed: $path" }
        require(sizeBytes >= 0) { "Size cannot be negative: $sizeBytes" }
        require(compressedSize >= 0) { "Compressed size cannot be negative: $compressedSize" }
        require(lastModified >= 0) { "Last modified cannot be negative: $lastModified" }
    }
}
