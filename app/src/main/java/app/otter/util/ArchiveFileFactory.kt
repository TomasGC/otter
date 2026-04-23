package app.otter.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.otter.domain.model.ArchiveFile
import app.otter.domain.model.ArchiveType
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for creating ArchiveFile instances from URIs.
 * Extracted from ExtractionService for testability and SRP.
 */
@Singleton
class ArchiveFileFactory @Inject constructor(
    private val context: Context,
    private val mimeTypeUtil: MimeTypeUtil
) {

    /**
     * Creates an ArchiveFile from a URI and filename.
     *
     * @param uri The archive URI (file:// or content://)
     * @param fileName The archive filename
     * @return ArchiveFile if valid, null otherwise
     */
    fun createFromUri(uri: Uri, fileName: String): ArchiveFile? {
        return when (uri.scheme) {
            "file" -> createFromFileUri(uri, fileName)
            "content" -> createFromContentUri(uri, fileName)
            else -> null
        }
    }

    private fun createFromFileUri(uri: Uri, fileName: String): ArchiveFile? {
        val file = File(uri.path ?: return null)
        if (!file.exists() || !file.isFile) {
            return null
        }

        val archiveType = ArchiveType.fromFileName(fileName) ?: return null

        return ArchiveFile(
            uri = uri,
            name = fileName,
            sizeBytes = file.length(),
            mimeType = mimeTypeUtil.getMimeType(fileName),
            type = archiveType
        )
    }

    private fun createFromContentUri(uri: Uri, fileName: String): ArchiveFile? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
            ?: return null

        return cursor.use {
            if (!it.moveToFirst()) return null

            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
            val size = if (sizeIndex != -1) it.getLong(sizeIndex) else 0L

            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val archiveType = ArchiveType.fromFileName(fileName) ?: return null

            ArchiveFile(
                uri = uri,
                name = fileName,
                sizeBytes = size,
                mimeType = mimeType,
                type = archiveType
            )
        }
    }
}
