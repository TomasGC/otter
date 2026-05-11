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
        Timber.tag(TAG).d("Resolving destination for: $archiveUri, fileName: $fileName")
        Timber.tag(TAG).d("URI scheme: ${archiveUri.scheme}, authority: ${archiveUri.authority}")

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

    /**
     * Extracts real file path from a content URI.
     *
     * @param uri The content URI
     * @return The real path, or null if cannot be determined
     */
    /**
     * Gets the parent directory path from MediaStore content URI.
     * Works for Samsung My Files which uses content://media/external/file/XXX URIs.
     *
     * @param uri The MediaStore content URI
     * @return The parent directory path, or null if cannot be determined
     */
    fun getPathFromMediaStore(uri: Uri): String? {
        return try {
            Timber.tag(TAG).d("Querying MediaStore for URI: $uri")

            context.contentResolver.query(
                uri,
                arrayOf(android.provider.MediaStore.MediaColumns.DATA),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                    if (columnIndex >= 0) {
                        val filePath = cursor.getString(columnIndex)
                        Timber.tag(TAG).d("MediaStore file path: $filePath")

                        if (filePath != null) {
                            val parentPath = File(filePath).parent
                            Timber.tag(TAG).d("MediaStore parent path: $parentPath")
                            return parentPath
                        }
                    }
                }
            }

            Timber.tag(TAG).d("MediaStore query returned null")
            null
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error querying MediaStore")
            null
        }
    }

    /**
     * Builds a file path by walking up the DocumentFile hierarchy.
     * This works even when getRealPathFromUri() fails (e.g., Samsung My Files).
     *
     * @param documentFile The DocumentFile to get the path for
     * @return The real path, or null if cannot be determined
     */
    fun getPathFromDocumentHierarchy(documentFile: DocumentFile?): String? {
        if (documentFile == null) return null

        try {
            val uri = documentFile.uri
            Timber.tag(TAG).d("Building path from hierarchy for URI: $uri")

            // Try standard document URI parsing
            if (DocumentsContract.isDocumentUri(context, uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                Timber.tag(TAG).d("Document ID: $docId")

                // Handle different authorities
                when (uri.authority) {
                    EXTERNAL_STORAGE_AUTHORITY -> {
                        // com.android.externalstorage.documents
                        val split = docId.split(":")
                        if (split.size >= 2) {
                            val type = split[0]
                            val path = split[1]

                            if ("primary".equals(type, ignoreCase = true)) {
                                val realPath = "${android.os.Environment.getExternalStorageDirectory()}/$path"
                                Timber.tag(TAG).d("Resolved from external storage: $realPath")
                                return realPath
                            }
                        }
                    }
                    "com.samsung.android.app.myfiles.providers.FileProvider",
                    "com.sec.android.app.myfiles.FileProvider" -> {
                        // Samsung My Files specific handling
                        // Extract path from docId which often contains the full path
                        val path = docId.substringAfter(":", "")
                        if (path.isNotEmpty() && path.startsWith("/")) {
                            Timber.tag(TAG).d("Resolved Samsung My Files path: $path")
                            return path
                        }

                        // Try to extract from URI path
                        val uriPath = uri.path
                        if (uriPath != null && uriPath.contains("/storage/emulated/")) {
                            val extractedPath = uriPath.substringAfter("/storage/emulated/")
                            val finalPath = "/storage/emulated/$extractedPath"
                            Timber.tag(TAG).d("Extracted Samsung path from URI: $finalPath")
                            return finalPath
                        }
                    }
                    DOWNLOADS_PROVIDER_AUTHORITY -> {
                        // Downloads provider
                        context.contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val columnIndex = cursor.getColumnIndex("_data")
                                if (columnIndex >= 0) {
                                    val path = cursor.getString(columnIndex)
                                    if (path != null) {
                                        Timber.tag(TAG).d("Resolved downloads path: $path")
                                        return path
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        Timber.tag(TAG).d("Unknown authority: ${uri.authority}")
                    }
                }
            }

            return null
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error building path from document hierarchy")
            return null
        }
    }

    fun getRealPathFromUri(uri: Uri): String? {
        return try {
            Timber.tag(TAG).d("getRealPathFromUri: $uri, authority: ${uri.authority}")

            if (DocumentsContract.isDocumentUri(context, uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                Timber.tag(TAG).d("Document ID: $docId")

                // Primary storage
                if (uri.authority == EXTERNAL_STORAGE_AUTHORITY) {
                    val split = docId.split(":")
                    Timber.tag(TAG).d("Split: $split")

                    if (split.size >= 2) {
                        val type = split[0]
                        val path = split[1]

                        if ("primary".equals(type, ignoreCase = true)) {
                            val realPath = "${android.os.Environment.getExternalStorageDirectory()}/$path"
                            Timber.tag(TAG).d("Resolved primary storage path: $realPath")
                            return realPath
                        }
                    }
                }

                // Downloads provider
                if (uri.authority == DOWNLOADS_PROVIDER_AUTHORITY) {
                    Timber.tag(TAG).d("Downloads provider URI")
                    // Try to get path from content resolver
                    context.contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val columnIndex = cursor.getColumnIndex("_data")
                            if (columnIndex >= 0) {
                                val path = cursor.getString(columnIndex)
                                if (path != null) {
                                    val parentPath = File(path).parent
                                    Timber.tag(TAG).d("Resolved downloads path: $parentPath")
                                    return parentPath
                                }
                            }
                        }
                    }
                }
            }

            Timber.tag(TAG).d("Could not resolve real path, returning null")
            null
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting real path from URI")
            null
        }
    }
}
