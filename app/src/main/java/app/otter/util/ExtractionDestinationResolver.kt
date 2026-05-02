package app.otter.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import androidx.documentfile.provider.DocumentFile
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves destination path for archive extraction.
 * Extracted from ExtractionService for testability.
 *
 * Strategy:
 * 1. Try to extract to same folder as archive (if accessible)
 * 2. Fallback to Downloads folder
 */
@Singleton
class ExtractionDestinationResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "DestinationResolver"
        private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
        private const val DOWNLOADS_PROVIDER_AUTHORITY = "com.android.providers.downloads.documents"
    }

    /**
     * Resolves the destination folder for extraction.
     *
     * @param archiveUri The URI of the archive file
     * @param fileName The archive file name
     * @return The destination folder for extraction
     */
    fun resolveDestination(archiveUri: Uri, fileName: String): File {
        // Try to get parent folder from URI
        val documentFile = DocumentFile.fromSingleUri(context, archiveUri)
        val parentUri = documentFile?.parentFile?.uri

        if (parentUri != null) {
            // Try to get real path from parent URI
            val parentPath = getRealPathFromUri(parentUri)
            if (parentPath != null) {
                Timber.tag(TAG).d("Extracting to same folder as archive: $parentPath")
                return createDestinationFolder(parentPath, fileName)
            }
        }

        // Fallback: Extract to Downloads folder
        Timber.tag(TAG).d("Could not determine archive folder, using Downloads")
        return createDownloadsDestination(fileName)
    }

    /**
     * Creates a destination folder within a parent directory.
     *
     * @param parentPath The parent directory path
     * @param fileName The archive file name
     * @return The destination folder
     */
    fun createDestinationFolder(parentPath: String, fileName: String): File {
        val folderName = fileName.substringBeforeLast(".")
        return File(parentPath, folderName)
    }

    /**
     * Creates a destination folder in the Downloads directory.
     *
     * @param fileName The archive file name
     * @return The destination folder in Downloads
     */
    fun createDownloadsDestination(fileName: String): File {
        val downloadFolder = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        val folderName = fileName.substringBeforeLast(".")
        return File(downloadFolder, folderName)
    }

    /**
     * Extracts real file path from a content URI.
     *
     * @param uri The content URI
     * @return The real path, or null if cannot be determined
     */
    fun getRealPathFromUri(uri: Uri): String? {
        return try {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                val docId = DocumentsContract.getDocumentId(uri)

                // Primary storage
                if (uri.authority == EXTERNAL_STORAGE_AUTHORITY) {
                    val split = docId.split(":")
                    if (split.size >= 2) {
                        val type = split[0]
                        val path = split[1]

                        if ("primary".equals(type, ignoreCase = true)) {
                            return "${android.os.Environment.getExternalStorageDirectory()}/$path"
                        }
                    }
                }

                // Downloads provider
                if (uri.authority == DOWNLOADS_PROVIDER_AUTHORITY) {
                    // Try to get path from content resolver
                    context.contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val columnIndex = cursor.getColumnIndex("_data")
                            if (columnIndex >= 0) {
                                val path = cursor.getString(columnIndex)
                                if (path != null) {
                                    return File(path).parent
                                }
                            }
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting real path from URI")
            null
        }
    }
}
