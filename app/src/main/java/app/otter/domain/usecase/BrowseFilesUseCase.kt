package app.otter.domain.usecase

import app.otter.domain.model.FileItem
import app.otter.domain.model.ResourcePath
import app.otter.domain.repository.FileBrowserRepository

/**
 * Use case for browsing files and directories.
 */
class BrowseFilesUseCase(
    private val repository: FileBrowserRepository,
) {

    /**
     * Lists files and directories at the given path.
     *
     * @param path Path of the directory to browse
     * @return Result containing list of files and directories, sorted: archives → directories → files
     */
    suspend operator fun invoke(path: ResourcePath): Result<List<FileItem>> {
        return repository.listFiles(path).map { files ->
            files.sortedWith(
                compareBy<FileItem> { !it.isArchive }  // Archives first
                    .thenBy { !it.isDirectory }         // Then directories
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }  // Then alphabetical
            )
        }
    }

    /**
     * Gets the parent directory path.
     *
     * @param currentPath Current directory path
     * @return Parent directory path, or null if at root
     */
    fun getParent(currentPath: ResourcePath): ResourcePath? = repository.getParent(currentPath)

    /**
     * Checks if the given path is a root directory.
     *
     * @param path Path to check
     * @return True if this is a root directory
     */
    fun isRoot(path: ResourcePath): Boolean = repository.isRoot(path)
}
