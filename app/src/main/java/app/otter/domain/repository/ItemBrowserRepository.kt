package app.otter.domain.repository

import app.otter.domain.model.BrowseResult
import app.otter.domain.model.ResourcePath

/**
 * Repository interface for browsing items (files and directories) within both file system
 * and archive resources.
 *
 * This interface abstracts the data source for browsing operations, allowing the use case
 * to remain agnostic of whether items are retrieved from the file system, an archive file,
 * or a remote source.
 *
 * Implementations must handle pagination efficiently for large directories/archives (>10k items).
 *
 * Note: getParent() and isRoot() methods will be used by future navigation use cases
 * (not by BrowseItemsUseCase). They are included here as part of the browsing abstraction.
 */
interface ItemBrowserRepository {

    /**
     * Browse items at the specified path with pagination support.
     *
     * @param path The resource path to browse (FileSystem or ArchiveEntry)
     * @param offset The number of items to skip (0-indexed)
     * @param limit The maximum number of items to return
     * @return Result containing BrowseResult (Complete or Paginated) or failure with exception
     */
    suspend fun browse(
        path: ResourcePath,
        offset: Int,
        limit: Int
    ): Result<BrowseResult>

    /**
     * Get the parent path of the current path.
     *
     * Used by navigation logic (not browsing logic). Will be consumed by future
     * navigation use cases to enable "back" navigation.
     *
     * @param currentPath The current resource path
     * @return Parent ResourcePath, or null if current path is root
     */
    fun getParent(currentPath: ResourcePath): ResourcePath?

    /**
     * Check if the specified path is the root (no parent).
     *
     * Used by navigation logic to determine if back button should be enabled.
     * Will be consumed by future navigation use cases.
     *
     * @param path The resource path to check
     * @return true if path is root (no parent), false otherwise
     */
    fun isRoot(path: ResourcePath): Boolean
}
