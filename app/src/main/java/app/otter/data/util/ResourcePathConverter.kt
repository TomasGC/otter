package app.otter.data.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import app.otter.domain.model.ResourcePath
import timber.log.Timber
import java.io.File

/**
 * Converts between Android Uri and domain ResourcePath.
 * Located in data layer as it bridges Android framework and domain.
 */
object ResourcePathConverter {

    /**
     * Converts Android Uri to domain ResourcePath.
     * Returns a FileSystem path by default - archives are detected separately.
     *
     * @param uri The Uri to convert
     * @param context Optional Android context for resolving content:// URIs (required for Samsung My Files and other providers)
     */
    fun fromUri(uri: Uri, context: Context? = null): ResourcePath {
        // Extract actual file path from URI
        // - content:// URIs: Try to get real path, fallback to keeping URI string (for ContentResolver use)
        // - file:// URIs: Extract path segment (e.g., file:///storage/... → /storage/...)
        // - Other URIs: Use as-is
        val filePath = when (uri.scheme) {
            "content" -> {
                val resolved = if (context != null) {
                    getRealPathFromContentUri(context, uri)
                } else {
                    null
                }
                // If resolved to real path, use it. Otherwise keep the content:// URI string
                // so ArchiveFileFactory can use ContentResolver to open it.
                resolved ?: uri.toString()
            }
            "file" -> uri.path ?: uri.toString()  // Extract path from file:// URI
            else -> uri.toString()
        }
        return ResourcePath.FileSystem(filePath)
    }

    /**
     * Converts domain ResourcePath to Android Uri.
     * Handles both FileSystem and ArchiveEntry paths.
     */
    fun toUri(path: ResourcePath): Uri = when (path) {
        is ResourcePath.FileSystem -> {
            // Handle paths that are already URIs (e.g., content:// preserved from intent)
            if (path.path.startsWith("content://") || path.path.startsWith("file://")) {
                Uri.parse(path.path)
            } else {
                Uri.fromFile(java.io.File(path.path))
            }
        }
        is ResourcePath.ArchiveEntry -> {
            if (path.archivePath.startsWith("content://") || path.archivePath.startsWith("file://")) {
                Uri.parse(path.archivePath)
            } else {
                Uri.fromFile(java.io.File(path.archivePath))
            }
        }
    }

    /**
     * Extracts the file system path from a Uri, handling Windows malformed URIs.
     *
     * CRITICAL: On Windows (Robolectric tests), Uri.fromFile() creates malformed URIs like:
     * file://C%3A%5CUsers%5C... where uri.path returns empty string.
     *
     * This method handles both:
     * - Malformed Windows URIs: file://C%3A%5C... → C:\...
     * - Standard Unix URIs: file:///path/to/file → /path/to/file
     *
     * @param uri The Uri to extract path from
     * @return The decoded file system path, or null if Uri is invalid
     */
    fun getFilePathFromUri(uri: Uri): String? {
        val uriString = uri.toString()
        return when {
            // Handle malformed Windows URIs: file://C%3A%5C... → C:\...
            uriString.startsWith("file://") && !uriString.startsWith("file:///") -> {
                try {
                    // Decode URL-encoded path: C%3A%5C → C:\
                    java.net.URLDecoder.decode(uriString.removePrefix("file://"), "UTF-8")
                } catch (e: Exception) {
                    null
                }
            }
            // Handle standard Unix URIs: file:///path/to/file
            else -> uri.path
        }
    }

    /**
     * Creates a File from a Uri, handling Windows malformed URIs.
     *
     * @param uri The Uri to convert to File
     * @return File instance, or null if Uri is invalid
     */
    fun toFile(uri: Uri): File? {
        val path = getFilePathFromUri(uri) ?: return null
        return File(path)
    }

    /**
     * Resolves a content:// URI to its real file system path.
     *
     * Handles various Android content providers:
     * - MediaStore (images, videos, audio)
     * - ExternalStorageProvider (primary storage, SD cards)
     * - DownloadsProvider (downloads folder)
     *
     * @param context Android application context
     * @param uri The content URI to resolve
     * @return Real file system path, or null if resolution fails
     */
    fun getRealPathFromContentUri(context: Context, uri: Uri): String? {
        try {
            // Method 1: Query DATA column (works for some providers)
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DATA),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (columnIndex >= 0) {
                        val path = cursor.getString(columnIndex)
                        if (!path.isNullOrBlank() && File(path).exists()) {
                            return path
                        }
                    }
                }
            }

            // Method 2: Samsung My Files FileProvider (check authority first, not DocumentUri)
            if (uri.authority == "com.sec.android.app.myfiles.FileProvider") {
                try {
                    // Samsung URI format: content://com.sec.android.app.myfiles.FileProvider/device_storage/0/Download/file.zip
                    // Extract path directly from URI (not using getDocumentId which throws exception)
                    val uriPath = uri.path ?: uri.encodedPath ?: ""
                    Timber.d("Samsung My Files URI path: $uriPath")

                    // Remove leading slash: /device_storage/0/Download/file.zip -> device_storage/0/Download/file.zip
                    val cleanPath = uriPath.removePrefix("/")
                    val pathComponents = cleanPath.split("/")

                    if (pathComponents.size >= 3 && pathComponents[0] == "device_storage") {
                        // Transform: device_storage/0/Download/file.zip -> /storage/emulated/0/Download/file.zip
                        val relativePath = pathComponents.drop(2).joinToString("/")
                        val realPath = "${android.os.Environment.getExternalStorageDirectory()}/$relativePath"
                        Timber.d("Samsung My Files transformed: $cleanPath -> $realPath")
                        return realPath
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse Samsung My Files URI")
                }
            }

            // Method 3: For DocumentFile URIs (Documents Provider)
            if (DocumentsContract.isDocumentUri(context, uri)) {
                val docId = DocumentsContract.getDocumentId(uri)

                // ExternalStorageProvider
                if (uri.authority == "com.android.externalstorage.documents") {
                    val split = docId.split(":")
                    val type = split[0]
                    if ("primary".equals(type, ignoreCase = true)) {
                        return "${android.os.Environment.getExternalStorageDirectory()}/${split[1]}"
                    }
                }

                // DownloadsProvider
                if (uri.authority == "com.android.providers.downloads.documents") {
                    val contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"),
                        docId.toLongOrNull() ?: return null
                    )
                    return getRealPathFromContentUri(context, contentUri)
                }

                // MediaProvider
                if (uri.authority == "com.android.providers.media.documents") {
                    val split = docId.split(":")
                    val type = split[0]

                    val contentUri = when (type) {
                        "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        else -> return null
                    }

                    val selection = "_id=?"
                    val selectionArgs = arrayOf(split[1])
                    return getRealPathFromContentUri(context, contentUri)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to resolve real path from content URI")
        }

        return null
    }
}
