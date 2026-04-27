package app.otter.domain.model

enum class ArchiveType(val extensions: List<String>) {
    ZIP(listOf(".zip")),
    RAR(listOf(".rar")),
    SEVEN_ZIP(listOf(".7z")),
    TAR(listOf(".tar")),
    TAR_GZ(listOf(".tar.gz", ".tgz"));

    companion object {
        fun fromFileName(name: String): ArchiveType? {
            return values().find { type ->
                type.extensions.any { ext ->
                    name.endsWith(ext, ignoreCase = true)
                }
            }
        }
    }
}
