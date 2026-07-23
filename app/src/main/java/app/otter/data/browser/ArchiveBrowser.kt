package app.otter.data.browser

import app.otter.domain.inspector.ArchiveInspector
import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.BrowseResult
import app.otter.domain.model.ResourcePath
import app.otter.util.MimeTypeUtil

/**
 * Browses archive contents with hybrid pagination strategy.
 *
 * Strategy:
 * - Small archives (<10k entries): Returns Complete result with all items
 * - Large archives (≥10k entries): Returns Paginated result with partial items
 *
 * Performance characteristics:
 * - First browse() call: O(n) where n = total entries (streams inspector, filters + sorts)
 * - Repeated browse() calls on an already-visited directory: O(1) amortized — the sorted,
 *   filtered item list for that directory is cached on this instance, so paginating through
 *   the same directory (the common scroll scenario) never re-streams the inspector.
 *
 * The cache assumes the underlying archive file does not change during this browser's
 * lifetime (read-only viewing) — callers that keep an ArchiveBrowser alive across a whole
 * browsing session (see ItemBrowserRepositoryImpl) rely on this.
 *
 * Implementation details:
 * - Uses [ArchiveInspector.countEntries] to determine pagination strategy (O(1))
 * - Filters entries to match the requested directory path
 * - Sorts directories first, then files (case-insensitive)
 * - Applies pagination offset/limit for large archives
 *
 * @property inspector The archive inspector to read entries from
 * @property archivePath The absolute path to the archive file
 */
class ArchiveBrowser(
    private val inspector: ArchiveInspector,
    private val archivePath: String,
    private val mimeTypeUtil: MimeTypeUtil = MimeTypeUtil()
) {

    // Sorted + filtered (but not yet offset/limit-sliced) items per normalized directory path.
    // ConcurrentHashMap + computeIfAbsent: ItemBrowserRepositoryImpl keeps one ArchiveBrowser
    // alive per archive, so concurrent scroll-prefetch calls can race here.
    private val directoryCache = java.util.concurrent.ConcurrentHashMap<String, List<BrowsableItem>>()

    // All entries in the archive (explicit + synthesized implicit directories), read once.
    private var cachedRawEntries: List<app.otter.domain.inspector.ArchiveEntry>? = null

    /**
     * Browse items at the specified path within the archive.
     *
     * @param entryPath The directory path within the archive (empty string = root)
     * @param offset The number of items to skip (0-indexed)
     * @param limit The maximum number of items to return
     * @return BrowseResult (Complete or Paginated) containing browsable items
     */
    suspend fun browse(
        entryPath: String,
        offset: Int = 0,
        limit: Int = PAGINATION_THRESHOLD
    ): BrowseResult {
        val totalEntries = inspector.countEntries()

        // Normalize entryPath (ensure trailing slash for directories, or empty for root)
        val normalizedPath = if (entryPath.isEmpty()) "" else entryPath.trimEnd('/') + "/"

        val filteredEntries = directoryCache.computeIfAbsent(normalizedPath) {
            computeDirectoryEntries(it)
        }

        // Determine pagination strategy based on filtered items count
        val filteredCount = filteredEntries.size

        // Handle offset beyond total (empty result)
        if (offset >= filteredCount) {
            return BrowseResult.Complete(emptyList())
        }

        // Apply pagination (offset/limit)
        val paginatedItems = filteredEntries
            .drop(offset)
            .take(limit)

        val hasMore = offset + paginatedItems.size < filteredCount

        // Decide result type based on pagination strategy
        return if (totalEntries < PAGINATION_THRESHOLD) {
            // Small archive: Always return Complete (even with pagination)
            BrowseResult.Complete(paginatedItems)
        } else {
            // Large archive: Return Paginated if more pages, Complete if last page
            if (hasMore) {
                BrowseResult.Paginated(
                    items = paginatedItems,
                    hasMore = true,
                    totalEstimate = totalEntries,
                    nextOffset = offset + paginatedItems.size
                )
            } else {
                BrowseResult.Complete(paginatedItems)
            }
        }
    }

    /**
     * Returns the sorted, filtered items for [normalizedPath], computed from the (cached)
     * raw entry list. Only the raw inspector stream is expensive; this per-directory pass
     * is cheap CPU work and is itself cached by the caller.
     */
    private fun computeDirectoryEntries(normalizedPath: String): List<BrowsableItem> {
        return getRawEntriesWithImplicitDirs().asSequence()
            .filter { entry -> isInDirectory(entry.path, normalizedPath) }
            .map { entry -> mapToNavigableItem(entry, normalizedPath) }
            .sortedWith(compareBy(
                { !it.canNavigateInto }, // Directories first (canNavigateInto = true)
                { it.name.lowercase() }  // Case-insensitive sort
            ))
            .toList()
    }

    /**
     * Streams all entries from the inspector once, synthesizes implicit directories at every
     * nesting level, and caches the combined list — this is the one truly expensive operation
     * per archive, regardless of how many directories are later browsed. Synchronized because
     * concurrent first-callers would otherwise each stream the inspector independently.
     */
    @Synchronized
    private fun getRawEntriesWithImplicitDirs(): List<app.otter.domain.inspector.ArchiveEntry> {
        cachedRawEntries?.let { return it }

        val allEntries = inspector.entries().toList()

        val implicitDirs = allEntries
            .flatMap { entry -> ancestorDirPaths(entry.path) }
            .distinct()
            .map { dirPath ->
                app.otter.domain.inspector.ArchiveEntry(
                    path = dirPath,
                    isDirectory = true,
                    sizeBytes = 0L,
                    compressedSize = 0L,
                    lastModified = 0L
                )
            }

        val combined = (allEntries + implicitDirs).distinctBy { it.path }
        cachedRawEntries = combined
        return combined
    }

    /** All ancestor directory paths implied by an entry path, e.g. "a/b/c.txt" -> ["a/", "a/b/"]. */
    private fun ancestorDirPaths(entryPath: String): List<String> {
        val result = mutableListOf<String>()
        var index = entryPath.indexOf('/')
        while (index > 0) {
            result.add(entryPath.substring(0, index + 1))
            index = entryPath.indexOf('/', index + 1)
        }
        return result
    }

    /**
     * Checks if an entry belongs to the specified directory.
     *
     * Examples:
     * - isInDirectory("file.txt", "") -> true (root level)
     * - isInDirectory("dir/file.txt", "") -> false (not root level)
     * - isInDirectory("dir/file.txt", "dir/") -> true (direct child)
     * - isInDirectory("dir/subdir/file.txt", "dir/") -> false (nested child)
     * - isInDirectory("dir/", "dir/") -> false (directory itself is not a child)
     *
     * @param entryPath The full path of the entry (e.g., "dir/file.txt")
     * @param directoryPath The directory path to check (e.g., "dir/" or "" for root)
     * @return true if the entry is a direct child of the directory
     */
    private fun isInDirectory(entryPath: String, directoryPath: String): Boolean {
        // Root directory: Only entries without "/" (or only trailing "/")
        if (directoryPath.isEmpty()) {
            val slashCount = entryPath.count { it == '/' }
            return slashCount == 0 || (slashCount == 1 && entryPath.endsWith('/'))
        }

        // Directory itself should not be included in its own listing
        if (entryPath == directoryPath || entryPath == directoryPath.trimEnd('/')) {
            return false
        }

        // Subdirectory: Entry must start with directory path and have no additional "/"
        if (!entryPath.startsWith(directoryPath)) {
            return false
        }

        val relativePath = entryPath.removePrefix(directoryPath)

        // Empty relative path means the directory itself (already handled above)
        if (relativePath.isEmpty()) {
            return false
        }

        val slashCount = relativePath.count { it == '/' }
        return slashCount == 0 || (slashCount == 1 && relativePath.endsWith('/'))
    }

    /**
     * Maps an archive entry to a navigable browsable item.
     *
     * Directories (either marked as isDirectory or ending with "/") are mapped to
     * [BrowsableItem.ArchiveDirectory] with canNavigateInto = true.
     *
     * Files are mapped to [BrowsableItem.ArchiveFileEntry] with canNavigateInto = false.
     *
     * @param entry The archive entry to map
     * @param parentPath The parent directory path
     * @return A browsable item (ArchiveDirectory or ArchiveFileEntry)
     */
    private fun mapToNavigableItem(
        entry: app.otter.domain.inspector.ArchiveEntry,
        parentPath: String
    ): BrowsableItem {
        val isDirectory = entry.isDirectory || entry.path.endsWith('/')
        val name = entry.path
            .removePrefix(parentPath)
            .removeSuffix("/")

        val resourcePath = ResourcePath.ArchiveEntry(
            archivePath = archivePath,
            entryPath = entry.path
        )

        return if (isDirectory) {
            BrowsableItem.ArchiveDirectory(
                path = resourcePath,
                name = name,
                sizeBytes = entry.sizeBytes,
                lastModified = entry.lastModified,
                archivePath = resourcePath
            )
        } else {
            BrowsableItem.ArchiveFileEntry(
                path = resourcePath,
                name = name,
                sizeBytes = entry.sizeBytes,
                lastModified = entry.lastModified,
                archivePath = resourcePath,
                mimeType = mimeTypeUtil.getMimeType(name)
            )
        }
    }

    companion object {
        /**
         * Pagination threshold - Archives with ≥10k entries are paginated.
         */
        private const val PAGINATION_THRESHOLD = 10_000
    }
}
