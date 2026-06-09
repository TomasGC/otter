package app.otter.domain.model

/**
 * Sealed class representing the result of a browse operation.
 *
 * This enables type-safe handling of both complete and paginated results.
 */
sealed class BrowseResult {
    abstract val items: List<BrowsableItem>

    /**
     * Complete result - All items loaded (small list).
     *
     * This result type is returned when the total number of items is below the pagination
     * threshold, indicating that all items have been loaded and no pagination is needed.
     *
     * @property items The complete list of browsable items
     */
    data class Complete(override val items: List<BrowsableItem>) : BrowseResult()

    /**
     * Paginated result - Partial items (large list).
     *
     * This result type is returned when the total number of items exceeds or equals the
     * pagination threshold, indicating that the list is paginated and more items may be
     * available.
     *
     * @property items The current page of browsable items
     * @property hasMore true if more items are available beyond the current page
     * @property totalEstimate Estimated total number of items (may be approximate)
     * @property nextOffset The offset to use for fetching the next page (or current offset + items.size)
     */
    data class Paginated(
        override val items: List<BrowsableItem>,
        val hasMore: Boolean,
        val totalEstimate: Int,
        val nextOffset: Int
    ) : BrowseResult()
}
