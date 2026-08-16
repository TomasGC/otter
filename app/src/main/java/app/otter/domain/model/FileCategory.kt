package app.otter.domain.model

/**
 * Coarse file-type buckets used for the settings/file-browser filter.
 * Distinct from [app.otter.util.FileTypeIconInfo]'s icon groupings — PDF gets its own
 * icon there, but folds into [DOCUMENT] here per the approved filter category list.
 */
enum class FileCategory {
    IMAGE, VIDEO, AUDIO, DOCUMENT, SPREADSHEET, PRESENTATION, ARCHIVE, OTHER;

    companion object {
        private val RULES: Map<List<String>, FileCategory> = mapOf(
            MimeGroups.IMAGE to IMAGE,
            MimeGroups.VIDEO to VIDEO,
            MimeGroups.AUDIO to AUDIO,
            MimeGroups.ARCHIVE to ARCHIVE,
            MimeGroups.PDF to DOCUMENT,
            MimeGroups.SPREADSHEET to SPREADSHEET,
            MimeGroups.PRESENTATION to PRESENTATION,
            MimeGroups.TEXT_DOCUMENT to DOCUMENT,
        )

        fun forMimeType(mimeType: String?): FileCategory {
            if (mimeType == null) return OTHER
            return RULES.entries
                .firstOrNull { (prefixes, _) -> prefixes.any { mimeType.startsWith(it) } }
                ?.value ?: OTHER
        }
    }
}
