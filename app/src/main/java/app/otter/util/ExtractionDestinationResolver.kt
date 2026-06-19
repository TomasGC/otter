package app.otter.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.otter.data.util.ResourcePathConverter
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
        Timber.tag(TAG).d("Resolving destination for: $archiveUri, fileName: $fileName")
        Timber.tag(TAG).d("URI scheme: ${archiveUri.scheme}, authority: ${archiveUri.authority}")

        // Try method 0: Direct file:// URI (works for file browser)
        if (archiveUri.scheme == "file") {
            val file = ResourcePathConverter.toFile(archiveUri)
            Timber.tag(TAG).d("File URI path: ${file?.absolutePath}")

            if (file != null) {
                val parentPath = file.parent
                Timber.tag(TAG).d("Parent path from file URI: $parentPath")

                if (parentPath != null) {
                    Timber.tag(TAG).d("Extracting to same folder as archive (file URI): $parentPath")
                    return createDestinationFolder(parentPath, fileName)
                }
            }
        }

        // Try method 1: MediaStore query (works for Samsung My Files with content://media URIs)
        if (archiveUri.authority == "media") {
            val parentPath = getPathFromMediaStore(archiveUri)
            Timber.tag(TAG).d("Parent path from MediaStore: $parentPath")

            if (parentPath != null) {
                Timber.tag(TAG).d("Extracting to same folder as archive (MediaStore): $parentPath")
                return createDestinationFolder(parentPath, fileName)
            }
        }

        // Try method 2: DocumentFile parent (works for standard document URIs)
        val documentFile = DocumentFile.fromSingleUri(context, archiveUri)
        Timber.tag(TAG).d("DocumentFile: $documentFile, name: ${documentFile?.name}")

        val parentUri = documentFile?.parentFile?.uri
        Timber.tag(TAG).d("Parent URI: $parentUri")

        if (parentUri != null) {
            // Try method 2a: Get real path from parent URI (works for standard authorities)
            val parentPath = getRealPathFromUri(parentUri)
            Timber.tag(TAG).d("Parent real path (method 2a): $parentPath")

            if (parentPath != null) {
                Timber.tag(TAG).d("Extracting to same folder as archive: $parentPath")
                return createDestinationFolder(parentPath, fileName)
            }

            // Try method 2b: Build path from DocumentFile hierarchy (works for Samsung My Files)
            val pathFromHierarchy = getPathFromDocumentHierarchy(documentFile.parentFile)
            Timber.tag(TAG).d("Parent path from hierarchy (method 2b): $pathFromHierarchy")

            if (pathFromHierarchy != null) {
                Timber.tag(TAG).d("Extracting to same folder as archive (from hierarchy): $pathFromHierarchy")
                return createDestinationFolder(pathFromHierarchy, fileName)
            }
        }

        // Fallback: Extract to Downloads folder
        Timber.tag(TAG).w("Could not determine archive folder, using Downloads fallback")
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

    fun getPathFromMediaStore(uri: Uri): String? = try {
        Timber.tag(TAG).d("Querying MediaStore for URI: $uri")
        val filePath = queryDataColumn(uri) ?: return null
        File(filePath).parent
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Error querying MediaStore")
        null
    }

    private fun queryDataColumn(uri: Uri): String? =
        context.contentResolver.query(
            uri, arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val idx = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
            if (idx < 0) return@use null
            cursor.getString(idx)
        }

    fun getPathFromDocumentHierarchy(documentFile: DocumentFile?): String? {
        if (documentFile == null) return null
        return try {
            val uri = documentFile.uri
            Timber.tag(TAG).d("Building path from hierarchy for URI: $uri")
            if (!DocumentsContract.isDocumentUri(context, uri)) return null
            val docId = DocumentsContract.getDocumentId(uri)
            resolveDocumentPath(uri, docId)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error building path from document hierarchy")
            null
        }
    }

    private fun resolveDocumentPath(uri: Uri, docId: String): String? = when (uri.authority) {
        EXTERNAL_STORAGE_AUTHORITY -> resolveExternalStorageDocPath(docId)
        "com.samsung.android.app.myfiles.providers.FileProvider",
        "com.sec.android.app.myfiles.FileProvider" -> resolveSamsungDocPath(uri, docId)
        DOWNLOADS_PROVIDER_AUTHORITY -> resolveDownloadsDocPath(uri)
        else -> null
    }

    private fun resolveExternalStorageDocPath(docId: String): String? {
        val split = docId.split(":")
        if (split.size < 2 || !split[0].equals("primary", ignoreCase = true)) return null
        return "${android.os.Environment.getExternalStorageDirectory()}/${split[1]}"
    }

    private fun resolveSamsungDocPath(uri: Uri, docId: String): String? {
        val path = docId.substringAfter(":", "")
        if (path.isNotEmpty() && path.startsWith("/")) return path
        val uriPath = uri.path ?: return null
        if (!uriPath.contains("/storage/emulated/")) return null
        return "/storage/emulated/${uriPath.substringAfter("/storage/emulated/")}"
    }

    private fun resolveDownloadsDocPath(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val idx = cursor.getColumnIndex("_data")
            if (idx < 0) return@use null
            cursor.getString(idx)
        }

    fun getRealPathFromUri(uri: Uri): String? {
        return try {
            Timber.tag(TAG).d("getRealPathFromUri: $uri, authority: ${uri.authority}")
            val realPath = ResourcePathConverter.getRealPathFromContentUri(context, uri)

            if (realPath != null) {
                Timber.tag(TAG).d("Resolved real path: $realPath")
            } else {
                Timber.tag(TAG).d("Could not resolve real path, returning null")
            }

            realPath
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting real path from URI")
            null
        }
    }
}
