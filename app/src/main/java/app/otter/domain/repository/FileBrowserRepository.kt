package app.otter.domain.repository

import app.otter.domain.model.FileItem
import app.otter.domain.model.ResourcePath

/**
 * Repository for browsing files and directories.
 */
interface FileBrowserRepository {

    /**
     * Lists files and directories at the given path.
     *
     * @param path Path of the directory to browse
     * @return List of files and directories in the given path
     */
    suspend fun listFiles(path: ResourcePath): Result<List<FileItem>>

    /**
     * Gets the parent directory path.
     *
     * @param currentPath Current directory path
     * @return Parent directory path, or null if at root
     */
    fun getParent(currentPath: ResourcePath): ResourcePath?

    /**
     * Checks if the given path is a root directory.
     *
     * @param path Path to check
     * @return True if this is a root directory
     */
    fun isRoot(path: ResourcePath): Boolean
}
