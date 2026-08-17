package app.otter.domain.model

data class UserSettings(
    val cacheWindowSize: Int = DEFAULT_CACHE_WINDOW_SIZE,
    val fileCategoryFilters: Map<FileCategory, FileCategoryFilterState> = emptyMap(),
) {
    companion object {
        const val DEFAULT_CACHE_WINDOW_SIZE = 100
        const val MIN_CACHE_WINDOW_SIZE = 50
        const val MAX_CACHE_WINDOW_SIZE = 500
    }
}
