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
     */
    fun fromUri(uri: Uri): ResourcePath = ResourcePath(uri.toString())

    /**
     * Converts domain ResourcePath to Android Uri.
     */
    fun toUri(path: ResourcePath): Uri = Uri.parse(path.value)

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
}
