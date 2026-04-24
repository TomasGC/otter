package app.otter.domain.model

/**
 * Represents a file or directory in the file browser.
 *
 * @property path The path to the file or directory
 * @property name Display name of the file or directory
 * @property isDirectory True if this is a directory, false if it's a file
 * @property sizeBytes Size in bytes (null for directories)
 * @property lastModified Last modified timestamp in milliseconds
 * @property mimeType MIME type of the file (null for directories)
 */
data class FileItem(
    val path: ResourcePath,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long?,
    val lastModified: Long,
    val mimeType: String?,
) {
    val isArchive: Boolean
        get() = mimeType in listOf(
            "application/zip",
            "application/x-zip-compressed",
            "application/x-rar-compressed",
            "application/vnd.rar",
            "application/x-7z-compressed",
            "application/x-tar",
            "application/gzip",
        )
}
