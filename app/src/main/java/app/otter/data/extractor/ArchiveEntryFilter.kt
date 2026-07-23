package app.otter.data.extractor

internal fun matchesSelectionFilter(entryName: String, selectedPaths: Set<String>?): Boolean {
    if (selectedPaths == null) return true
    if (selectedPaths.contains(entryName)) return true
    return selectedPaths.any { path -> path.endsWith("/") && entryName.startsWith(path) }
}
