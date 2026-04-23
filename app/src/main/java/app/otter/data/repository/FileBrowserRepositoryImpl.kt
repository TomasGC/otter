package app.otter.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import app.otter.domain.model.FileItem
import app.otter.domain.repository.FileBrowserRepository
import app.otter.util.MimeTypeUtil
import java.io.File

/**
 * Implementation of FileBrowserRepository for browsing the file system.
 */
class FileBrowserRepositoryImpl(
    private val context: Context,
    private val mimeTypeUtil: MimeTypeUtil,
) : FileBrowserRepository {

    override suspend fun listFiles(uri: Uri): Result<List<FileItem>> {
        return try {

            val files = when (uri.scheme) {
                "file" -> listFilesFromFile(File(uri.path ?: return Result.success(emptyList())))
                "content" -> listFilesFromDocumentFile(uri)
                else -> {
                    return Result.failure(IllegalArgumentException("Unsupported URI scheme: ${uri.scheme}"))
                }
            }

            Result.success(files)
        } catch (e: SecurityException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun listFilesFromFile(directory: File): List<FileItem> {
        if (!directory.exists() || !directory.isDirectory) {
            return emptyList()
        }

        val files = directory.listFiles()

        return files?.mapNotNull { file ->
            try {
                val mimeType = if (file.isFile) mimeTypeUtil.getMimeType(file.name) else null

                FileItem(
                    uri = Uri.fromFile(file),
                    name = file.name,
                    isDirectory = file.isDirectory,
                    sizeBytes = if (file.isFile) file.length() else null,
                    lastModified = file.lastModified(),
                    mimeType = mimeType
                )
            } catch (e: SecurityException) {
                null
            } catch (e: Exception) {
                null
            }
        } ?: emptyList()
    }

    private fun listFilesFromDocumentFile(uri: Uri): List<FileItem> {
        val documentFile = DocumentFile.fromTreeUri(context, uri) ?: return emptyList()

        return documentFile.listFiles().mapNotNull { file ->
            try {
                FileItem(
                    uri = file.uri,
                    name = file.name ?: return@mapNotNull null,
                    isDirectory = file.isDirectory,
                    sizeBytes = if (file.isFile) file.length() else null,
                    lastModified = file.lastModified(),
                    mimeType = if (file.isFile) file.type else null
                )
            } catch (e: SecurityException) {
                null
            }
        }
    }

    override fun getParent(currentUri: Uri): Uri? {
        return when (currentUri.scheme) {
            "file" -> {
                val file = File(currentUri.path ?: return null)
                val parent = file.parentFile ?: return null
                if (parent.path == "/" || parent.path == Environment.getExternalStorageDirectory().path) {
                    null
                } else {
                    Uri.fromFile(parent)
                }
            }
            "content" -> {
                try {
                    DocumentsContract.getDocumentId(currentUri)
                    DocumentsContract.buildDocumentUriUsingTree(
                        currentUri,
                        DocumentsContract.getTreeDocumentId(currentUri)
                    )
                } catch (e: Exception) {
                    null
                }
            }
            else -> null
        }
    }

    override fun isRoot(uri: Uri): Boolean {
        return when (uri.scheme) {
            "file" -> {
                val path = uri.path ?: return true
                path == "/" || path == Environment.getExternalStorageDirectory().path
            }
            "content" -> {
                try {
                    val documentId = DocumentsContract.getDocumentId(uri)
                    val treeDocumentId = DocumentsContract.getTreeDocumentId(uri)
                    documentId == treeDocumentId
                } catch (e: Exception) {
                    true
                }
            }
            else -> true
        }
    }

}
