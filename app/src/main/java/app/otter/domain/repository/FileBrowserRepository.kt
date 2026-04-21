package app.otter.domain.repository

import android.net.Uri
import app.otter.domain.model.FileItem

/**
 * Repository for browsing files and directories.
 */
interface FileBrowserRepository {

    /**
     * Lists files and directories at the given path.
     *
     * @param uri URI of the directory to browse
     * @return List of files and directories in the given path
     */
    suspend fun listFiles(uri: Uri): Result<List<FileItem>>

    /**
     * Gets the parent directory URI.
     *
     * @param currentUri Current directory URI
     * @return Parent directory URI, or null if at root
     */
    fun getParent(currentUri: Uri): Uri?

    /**
     * Checks if the given URI is a root directory.
     *
     * @param uri URI to check
     * @return True if this is a root directory
     */
    fun isRoot(uri: Uri): Boolean
}
