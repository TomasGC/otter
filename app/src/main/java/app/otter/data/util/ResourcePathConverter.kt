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

    fun getRealPathFromContentUri(context: Context, uri: Uri): String? = try {
        getDataColumnPath(context, uri)
            ?: resolveSamsungPath(uri)
            ?: resolveDocumentUri(context, uri)
    } catch (e: Exception) {
        Timber.e(e, "Failed to resolve real path from content URI")
        null
    }

    private fun getDataColumnPath(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            if (idx < 0) return@use null
            val path = cursor.getString(idx)
            if (!path.isNullOrBlank() && File(path).exists()) path else null
        }

    private fun resolveSamsungPath(uri: Uri): String? {
        if (uri.authority != "com.sec.android.app.myfiles.FileProvider") return null
        return try {
            val components = (uri.path ?: uri.encodedPath ?: "").removePrefix("/").split("/")
            if (components.size >= 3 && components[0] == "device_storage") {
                "${android.os.Environment.getExternalStorageDirectory()}/${components.drop(2).joinToString("/")}"
            } else null
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse Samsung My Files URI")
            null
        }
    }

    private fun resolveDocumentUri(context: Context, uri: Uri): String? {
        if (!DocumentsContract.isDocumentUri(context, uri)) return null
        val docId = DocumentsContract.getDocumentId(uri)
        return when (uri.authority) {
            "com.android.externalstorage.documents" -> resolveExternalStoragePath(docId)
            "com.android.providers.downloads.documents" -> resolveDownloadsPath(context, docId)
            "com.android.providers.media.documents" -> resolveMediaProviderPath(context, docId)
            else -> null
        }
    }

    private fun resolveExternalStoragePath(docId: String): String? {
        val split = docId.split(":")
        if (split.size < 2 || !split[0].equals("primary", ignoreCase = true)) return null
        return "${android.os.Environment.getExternalStorageDirectory()}/${split[1]}"
    }

    private fun resolveDownloadsPath(context: Context, docId: String): String? {
        val id = docId.toLongOrNull() ?: return null
        val contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), id)
        return getRealPathFromContentUri(context, contentUri)
    }

    private fun resolveMediaProviderPath(context: Context, docId: String): String? {
        val split = docId.split(":")
        val contentUri = when (split[0]) {
            "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> return null
        }
        return getRealPathFromContentUri(context, contentUri)
    }
}
