package app.otter.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import app.otter.data.util.ResourcePathConverter
import app.otter.domain.model.FileItem
import app.otter.domain.model.ResourcePath
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

    override suspend fun listFiles(path: ResourcePath): Result<List<FileItem>> {
        val uri = ResourcePathConverter.toUri(path)
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

        val canonicalDir = directory.canonicalPath
        val files = directory.listFiles()

        return files?.mapNotNull { file ->
            try {
                // Validate file is within directory bounds
                if (!file.canonicalPath.startsWith(canonicalDir)) {
                    return@mapNotNull null
                }

                val mimeType = if (file.isFile) mimeTypeUtil.getMimeType(file.name) else null

                FileItem(
                    path = ResourcePathConverter.fromUri(Uri.fromFile(file)),
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
                    path = ResourcePathConverter.fromUri(file.uri),
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

    override fun getParent(currentPath: ResourcePath): ResourcePath? {
        val currentUri = ResourcePathConverter.toUri(currentPath)
        val parentUri = when (currentUri.scheme) {
            "file" -> {
                val file = File(currentUri.path ?: return null)
                val parent = file.parentFile ?: return null
                if (parent.path == "/" || parent.path == Environment.getExternalStorageDirectory().path) {
                    return null
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
                    return null
                }
            }
            else -> return null
        }
        return ResourcePathConverter.fromUri(parentUri)
    }

    override fun isRoot(path: ResourcePath): Boolean {
        val uri = ResourcePathConverter.toUri(path)
        return when (uri.scheme) {
            "file" -> {
                val filePath = uri.path ?: return true
                filePath == "/" || filePath == Environment.getExternalStorageDirectory().path
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
