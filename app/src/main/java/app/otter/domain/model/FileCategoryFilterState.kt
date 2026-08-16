package app.otter.domain.model

enum class FileCategoryFilterState { INCLUDE, EXCLUDE }

/**
 * Cycles a category's tap state: null (neutral) -> INCLUDE -> EXCLUDE -> null.
 */
fun FileCategoryFilterState?.next(): FileCategoryFilterState? = when (this) {
    null -> FileCategoryFilterState.INCLUDE
    FileCategoryFilterState.INCLUDE -> FileCategoryFilterState.EXCLUDE
    FileCategoryFilterState.EXCLUDE -> null
}
