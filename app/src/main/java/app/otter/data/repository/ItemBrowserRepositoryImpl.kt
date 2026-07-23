package app.otter.data.repository

import app.otter.data.browser.ArchiveBrowser
import app.otter.data.browser.FileSystemBrowser
import app.otter.data.inspector.ArchiveInspectorFactory
import app.otter.domain.model.BrowseResult
import app.otter.domain.model.ResourcePath
import app.otter.domain.repository.ItemBrowserRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ItemBrowserRepository that orchestrates browsing operations using
 * polymorphic dispatch based on ResourcePath type.
 *
 * This repository acts as a facade over FileSystemBrowser and ArchiveBrowser, routing
 * requests to the appropriate browser implementation based on the path type:
 * - ResourcePath.FileSystem → FileSystemBrowser
 * - ResourcePath.ArchiveEntry → ArchiveBrowser, one instance per archivePath reused for the
 *   life of this (Singleton) repository, so ArchiveBrowser's own entry cache actually pays
 *   off across the many paginated calls a scroll session makes into the same archive.
 *
 * The polymorphic dispatch pattern leverages Kotlin's sealed class exhaustiveness checking,
 * ensuring compile-time safety when new ResourcePath types are added.
 *
 * @property fileSystemBrowser Browser for file system directories
 * @property inspectorFactory Factory for creating archive inspectors
 */
@Singleton
class ItemBrowserRepositoryImpl @Inject constructor(
    private val fileSystemBrowser: FileSystemBrowser,
    private val inspectorFactory: ArchiveInspectorFactory
) : ItemBrowserRepository {

    // Assumes archive files are read-only for the life of the app process — see ArchiveBrowser's
    // own caching contract for the same tradeoff. Bounded LRU (access-order LinkedHashMap):
    // this is a Singleton, app-lifetime cache, so an unbounded map would accumulate one
    // ArchiveBrowser (each holding a full raw-entries snapshot) per distinct archive ever
    // browsed in the session. Access via getOrCreateBrowser is @Synchronized, so the plain
    // (non-concurrent) LinkedHashMap is safe.
    private val browserCache = object : LinkedHashMap<String, ArchiveBrowser>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArchiveBrowser>): Boolean =
            size > MAX_CACHED_ARCHIVES
    }

    override suspend fun browse(
        path: ResourcePath,
        offset: Int,
        limit: Int
    ): Result<BrowseResult> {
        return when (path) {
            is ResourcePath.FileSystem -> fileSystemBrowser.browse(path)
            is ResourcePath.ArchiveEntry -> runCatching {
                getOrCreateBrowser(path.archivePath).browse(path.entryPath, offset, limit)
            }
        }
    }

    @Synchronized
    private fun getOrCreateBrowser(archivePath: String): ArchiveBrowser {
        browserCache[archivePath]?.let { return it }
        val inspector = inspectorFactory.create(File(archivePath)).getOrThrow()
        return ArchiveBrowser(inspector, archivePath).also { browserCache[archivePath] = it }
    }

    companion object {
        internal const val MAX_CACHED_ARCHIVES = 5
    }

    override fun getParent(currentPath: ResourcePath): ResourcePath? {
        return when (currentPath) {
            is ResourcePath.FileSystem -> {
                fileSystemBrowser.getParent(currentPath)
            }
            is ResourcePath.ArchiveEntry -> {
                if (currentPath.entryPath.isEmpty()) {
                    // At archive root, parent is the file system location
                    ResourcePath.FileSystem(currentPath.archivePath)
                } else {
                    // Inside archive, parent is the parent directory
                    val parentPath = currentPath.entryPath.substringBeforeLast("/", "")
                    ResourcePath.ArchiveEntry(currentPath.archivePath, parentPath)
                }
            }
        }
    }

    override fun isRoot(path: ResourcePath): Boolean {
        return when (path) {
            is ResourcePath.FileSystem -> {
                fileSystemBrowser.isRoot(path)
            }
            is ResourcePath.ArchiveEntry -> {
                // Archive entries are never "root" - they always have a file system parent
                false
            }
        }
    }
}
