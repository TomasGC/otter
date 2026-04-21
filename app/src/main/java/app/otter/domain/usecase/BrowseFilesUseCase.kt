package app.otter.domain.usecase

import android.net.Uri
import app.otter.domain.model.FileItem
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
     * @param uri URI of the directory to browse
     * @return Result containing list of files and directories, sorted: archives → directories → files
     */
    suspend operator fun invoke(uri: Uri): Result<List<FileItem>> {
        return repository.listFiles(uri).map { files ->
            files.sortedWith(
                compareBy<FileItem> { !it.isArchive }  // Archives first
                    .thenBy { !it.isDirectory }         // Then directories
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }  // Then alphabetical
            )
        }
    }

    /**
     * Gets the parent directory URI.
     *
     * @param currentUri Current directory URI
     * @return Parent directory URI, or null if at root
     */
    fun getParent(currentUri: Uri): Uri? = repository.getParent(currentUri)

    /**
     * Checks if the given URI is a root directory.
     *
     * @param uri URI to check
     * @return True if this is a root directory
     */
    fun isRoot(uri: Uri): Boolean = repository.isRoot(uri)
}
