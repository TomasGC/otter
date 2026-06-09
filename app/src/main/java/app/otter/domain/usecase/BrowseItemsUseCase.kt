package app.otter.domain.usecase

import app.otter.domain.model.BrowseResult
import app.otter.domain.model.ResourcePath
import app.otter.domain.repository.ItemBrowserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Use case for browsing items (files and directories) within a resource path.
 *
 * This use case handles browsing operations with hybrid pagination strategy:
 * - Small lists (<10k items): Returns Complete result with all items
 * - Large lists (≥10k items): Returns Paginated result with partial items and pagination metadata
 *
 * The use case delegates the actual browsing logic to the repository, allowing for different
 * implementations (file system, archive file, remote storage, etc.).
 */
class BrowseItemsUseCase @Inject constructor(
    private val repository: ItemBrowserRepository
) {

    /**
     * Browse items at the specified path with pagination support.
     *
     * @param path The resource path to browse (FileSystem or ArchiveEntry)
     * @param offset The number of items to skip (0-indexed)
     * @param limit The maximum number of items to return
     * @return Result containing BrowseResult (Complete or Paginated) or failure with exception
     */
    suspend operator fun invoke(
        path: ResourcePath,
        offset: Int = 0,
        limit: Int = PAGINATION_THRESHOLD
    ): Result<BrowseResult> = withContext(Dispatchers.IO) {
        repository.browse(path, offset, limit)
    }

    companion object {
        /**
         * Pagination threshold - Lists with ≥10k items are paginated.
         *
         * This constant defines the boundary between small lists (returned as Complete) and
         * large lists (returned as Paginated). Adjust this value based on performance
         * considerations and user experience testing.
         */
        const val PAGINATION_THRESHOLD = 10_000
    }
}
