package app.otter.ui.viewmodel

import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.ResourcePath

/**
 * Pure functions for archive selection logic.
 *
 * Extracted for unit testing without ViewModel dependencies.
 */
object ArchiveSelectionHelper {

    /**
     * Filters selectable items from a list of browsable items.
     *
     * Pure function - returns ALL items (files, archives, directories) for selection.
     * This is used by "Select All" to select everything in the current directory.
     *
     * @param items List of browsable items to filter
     * @return All items (currently returns all input items as everything is selectable)
     */
    fun filterSelectableItems(items: List<BrowsableItem>): List<BrowsableItem> {
        // Return all items - everything is selectable (files, archives, directories)
        return items
    }

    /**
     * Filters only archives from a list of browsable items.
     *
     * Pure function - extracts ArchiveFile, ArchiveFileEntry, and ArchiveDirectory types.
     * This is used when you want to select ONLY archives (not regular files).
     *
     * @param items List of browsable items to filter
     * @return List containing only archive items (files and directories inside archives)
     */
    fun filterArchives(items: List<BrowsableItem>): List<BrowsableItem> {
        return items.filter { item ->
            item is BrowsableItem.ArchiveFile ||
            item is BrowsableItem.ArchiveFileEntry ||
            item is BrowsableItem.ArchiveDirectory
        }
    }

    /**
     * Adds items to selection set (idempotent - won't add duplicates).
     *
     * Pure function - returns new selection set without side effects.
     *
     * @param items Items to add to selection
     * @param currentSelection Current selection set
     * @return New selection set with items added
     */
    fun addToSelection(
        items: List<BrowsableItem>,
        currentSelection: Set<ResourcePath>
    ): Set<ResourcePath> {
        return currentSelection + items.map { it.path }
    }
}
