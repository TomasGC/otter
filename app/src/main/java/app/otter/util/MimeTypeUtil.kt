package app.otter.util

object MimeTypeUtil {
    fun getMimeType(filename: String): String {
        val extension = filename.substringAfterLast('.', "").lowercase()

        // Archive types (comprehensive list)
        return when (extension) {
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "tar" -> "application/x-tar"
            "gz", "gzip" -> "application/gzip"
            "bz2", "bzip2" -> "application/x-bzip2"
            "xz" -> "application/x-xz"
            "tgz" -> "application/x-compressed-tar"
            "tbz2" -> "application/x-bzip-compressed-tar"
            "txz" -> "application/x-xz-compressed-tar"
            // Common file types
            "txt" -> "text/plain"
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            else -> "application/octet-stream"  // Generic binary
        }
    }
}
