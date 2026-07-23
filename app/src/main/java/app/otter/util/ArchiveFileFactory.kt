package app.otter.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.otter.data.util.ResourcePathConverter
import app.otter.domain.model.ArchiveFile
import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ResourcePath
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for creating ArchiveFile instances from ResourcePath.
 * Extracted from ExtractionService for testability and SRP.
 */
@Singleton
class ArchiveFileFactory @Inject constructor(
    private val context: Context,
    private val mimeTypeUtil: MimeTypeUtil
) {

    /**
     * Creates an ArchiveFile from a ResourcePath and filename.
     *
     * @param path The archive path
     * @param fileName The archive filename
     * @return ArchiveFile if valid, null otherwise
     */
    fun createFromPath(path: ResourcePath, fileName: String): ArchiveFile? {
        val uri = ResourcePathConverter.toUri(path)
        return when (uri.scheme) {
            "file" -> createFromFileUri(path, uri, fileName)
            "content" -> createFromContentUri(path, uri, fileName)
            else -> {
                // Last resort: if path is a FileSystem with content:// stored as string
                if (path is ResourcePath.FileSystem && path.path.startsWith("content://")) {
                    val contentUri = android.net.Uri.parse(path.path)
                    createFromContentUri(path, contentUri, fileName)
                } else null
            }
        }
    }

    /**
     * Legacy method for tests compatibility.
     * Directly processes Uri without double conversion.
     */
    fun createFromUri(uri: Uri, fileName: String): ArchiveFile? {
        return when (uri.scheme) {
            "file" -> {
                val file = ResourcePathConverter.toFile(uri) ?: return null
                val path = ResourcePathConverter.fromUri(uri)
                createFromFileUri(path, uri, fileName)
            }
            "content" -> {
                val path = ResourcePathConverter.fromUri(uri)
                createFromContentUri(path, uri, fileName)
            }
            else -> null
        }
    }

    private fun createFromFileUri(path: ResourcePath, uri: Uri, fileName: String): ArchiveFile? {
        val file = ResourcePathConverter.toFile(uri) ?: return null
        if (!file.exists() || !file.isFile) {
            return null
        }

        val archiveType = ArchiveType.fromFileName(fileName) ?: return null

        return ArchiveFile(
            path = path,
            name = fileName,
            sizeBytes = file.length(),
            mimeType = mimeTypeUtil.getMimeType(fileName),
            type = archiveType
        )
    }

    companion object {
        /** Sentinel stored in [ArchiveFile.sizeBytes] when the content:// provider did not supply a size. */
        const val UNKNOWN_SIZE = -1L
    }

    private fun createFromContentUri(path: ResourcePath, uri: Uri, fileName: String): ArchiveFile? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
            ?: return null

        return cursor.use {
            if (!it.moveToFirst()) return null

            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
            val size = if (sizeIndex != -1) it.getLong(sizeIndex) else UNKNOWN_SIZE

            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val archiveType = ArchiveType.fromFileName(fileName) ?: return null

            ArchiveFile(
                path = path,
                name = fileName,
                sizeBytes = size,
                mimeType = mimeType,
                type = archiveType
            )
        }
    }
}
