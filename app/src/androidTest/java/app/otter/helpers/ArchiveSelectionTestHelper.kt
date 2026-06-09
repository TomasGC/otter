package app.otter.domain.usecase.helpers

import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.ResourcePath

/**
 * Helper for archive selection utilities in instrumented tests.
 */
object ArchiveSelectionTestHelper {

    // ========== Expected Names Extraction ==========

    fun getExpectedFileNames(selectedItems: List<BrowsableItem>): Set<String> {
        return selectedItems
            .filterIsInstance<BrowsableItem.ArchiveFileEntry>()
            .map {
                val entryPath = (it.path as ResourcePath.ArchiveEntry).entryPath
                entryPath.replace("\\", "/")
            }
            .toSet()
    }

    fun getExpectedFolderNames(selectedItems: List<BrowsableItem>): Set<String> {
        return selectedItems
            .filterIsInstance<BrowsableItem.ArchiveDirectory>()
            .map {
                val entryPath = (it.path as ResourcePath.ArchiveEntry).entryPath
                entryPath.trimEnd('/').replace("\\", "/")
            }
            .toSet()
    }
}
