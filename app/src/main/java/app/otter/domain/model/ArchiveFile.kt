package app.otter.domain.model

import android.net.Uri

data class ArchiveFile(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val type: ArchiveType
)
