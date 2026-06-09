package app.otter.domain.usecase.helpers

import android.net.Uri
import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.ResourcePath
import app.otter.domain.usecase.BrowseItemsUseCase
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import java.io.File

/**
 * Helper for archive navigation and browsing in instrumented tests.
 * Uses BrowseItemsUseCase (new architecture with cache) injected via Hilt.
 */
object ArchiveNavigationTestHelper {

    // Test archive names
    const val TEST_ARCHIVE_RPA = "test_archive.rpa"
    const val TEST_ARCHIVE_ZIP = "test_archive.zip"
    const val TEST_ARCHIVE_RAR = "test_archive.rar"
    const val TEST_ARCHIVE_TAR = "test_archive.tar"
    const val TEST_ARCHIVE_TAR_GZ = "test_archive.tar.gz"
    const val TEST_ARCHIVE_7Z = "test_archive.7z"

    private val testArchivesDir: File
        get() = File(TestConstants.TestArchives.devicePath)

    // ========== Path & Archive Utilities ==========

    fun getArchivePath(archiveName: String): String {
        val path = File(testArchivesDir, archiveName).absolutePath
        assertTrue("Test archive not found: $path", File(path).exists())
        return path
    }

    // ========== Browse (using injected BrowseItemsUseCase) ==========

    /**
     * Browse archive using BrowseItemsUseCase (new architecture).
     * @param useCase Injected via Hilt in test class
     */
    suspend fun browseArchive(
        useCase: BrowseItemsUseCase,
        archivePath: String,
        path: ResourcePath? = null
    ): List<BrowsableItem> {
        // Convert path to ResourcePath for use case
        val resourcePath = if (path != null) {
            path
        } else {
            // For root of archive, use ArchiveEntry with absolute path
            ResourcePath.ArchiveEntry(archivePath = archivePath, entryPath = "")
        }

        // Browse using invoke operator (returns Result<BrowseResult>)
        val result = useCase(
            path = resourcePath,
            offset = 0,
            limit = 2000  // Covers root: 6 dirs + 1000 files = 1006 items
        )

        // Extract items from result
        return result.getOrThrow().items
    }

    // ========== Assertions ==========

    fun assertRootHasFoldersAndFiles(items: List<BrowsableItem>) {
        assertTrue("Root should have items", items.isNotEmpty())

        val folders = items.filterIsInstance<BrowsableItem.ArchiveDirectory>()
        val files = items.filterIsInstance<BrowsableItem.ArchiveFileEntry>()

        assertTrue("Root should have folders", folders.isNotEmpty())
        assertTrue("Root should have files", files.isNotEmpty())
    }
}
