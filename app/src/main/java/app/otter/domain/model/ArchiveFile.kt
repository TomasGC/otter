package app.otter.domain.model

data class ArchiveFile(
    val path: ResourcePath,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val type: ArchiveType
)
