package app.otter.domain.model

enum class ArchiveType(val extensions: List<String>) {
    ZIP(listOf(".zip")),
    RAR(listOf(".rar")),
    SEVEN_ZIP(listOf(".7z")),
    TAR(listOf(".tar")),
    TAR_GZ(listOf(".tar.gz", ".tgz")),
    TAR_BZ2(listOf(".tar.bz2", ".tbz2")),
    GZIP(listOf(".gz", ".gzip")),
    RPA(listOf(".rpa"));

    companion object {
        fun fromFileName(name: String): ArchiveType? {
            // Check multi-extension formats first (e.g., .tar.gz before .gz)
            val sortedTypes = entries.sortedByDescending { type ->
                type.extensions.maxOfOrNull { it.length } ?: 0
            }

            return sortedTypes.find { type ->
                type.extensions.any { ext ->
                    name.endsWith(ext, ignoreCase = true)
                }
            }
        }
    }
}
