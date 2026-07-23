package app.otter.util

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MimeTypeUtil @Inject constructor() {

    fun getMimeType(filename: String): String {
        val baseName = filename.substringAfterLast('/')
        val parts = baseName.split('.')
        if (parts.size >= 3) {
            val twoPartExt = "${parts[parts.size - 2]}.${parts[parts.size - 1]}".lowercase()
            MIME_TYPES[twoPartExt]?.let { return it }
        }
        val extension = baseName.substringAfterLast('.', "").lowercase()
        return MIME_TYPES[extension] ?: DEFAULT_MIME_TYPE
    }

    companion object {
        private const val DEFAULT_MIME_TYPE = "application/octet-stream"

        private val MIME_TYPES = mapOf(
            // Archive types (comprehensive list)
            "zip" to "application/zip",
            "rar" to "application/x-rar-compressed",
            "7z" to "application/x-7z-compressed",
            "tar" to "application/x-tar",
            "gz" to "application/gzip",
            "gzip" to "application/gzip",
            "bz2" to "application/x-bzip2",
            "bzip2" to "application/x-bzip2",
            "xz" to "application/x-xz",
            "tgz" to "application/x-compressed-tar",
            "tar.gz" to "application/x-compressed-tar",
            "tbz2" to "application/x-bzip-compressed-tar",
            "tar.bz2" to "application/x-bzip-compressed-tar",
            "txz" to "application/x-xz-compressed-tar",
            "tar.xz" to "application/x-xz-compressed-tar",
            "rpa" to "application/x-rpa",
            // Common file types
            "txt" to "text/plain",
            "pdf" to "application/pdf",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "png" to "image/png",
            "gif" to "image/gif",
            "mp4" to "video/mp4",
            "mkv" to "video/x-matroska",
            "mp3" to "audio/mpeg",
            "flac" to "audio/flac",
        )
    }
}
