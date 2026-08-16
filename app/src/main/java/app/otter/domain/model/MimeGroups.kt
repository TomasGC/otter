package app.otter.domain.model

/**
 * Shared MIME-prefix groupings, consumed by [FileCategory] (filtering) and
 * [app.otter.util.FileTypeIconInfo] (icon lookup) so the MIME strings live in one place.
 */
internal object MimeGroups {
    val IMAGE = listOf("image/")
    val VIDEO = listOf("video/")
    val AUDIO = listOf("audio/")
    val ARCHIVE = listOf(
        "application/zip",
        "application/x-zip",
        "application/x-rar",
        "application/vnd.rar",
        "application/x-7z-compressed",
        "application/x-tar",
        "application/gzip",
        "application/x-bzip",
        "application/x-compressed-tar",
        "application/x-bzip-compressed-tar",
        "application/x-xz-compressed-tar",
        "application/x-rpa",
    )
    val PDF = listOf("application/pdf")
    val SPREADSHEET = listOf(
        "text/csv",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml",
        "application/vnd.oasis.opendocument.spreadsheet",
    )
    val PRESENTATION = listOf(
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml",
        "application/vnd.oasis.opendocument.presentation",
    )
    val TEXT_DOCUMENT = listOf(
        "text/",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml",
        "application/vnd.ms-",
        "application/vnd.oasis.opendocument",
    )
}
