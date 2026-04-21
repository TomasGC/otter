package app.otter.domain.usecase

import android.net.Uri
import app.otter.domain.model.ArchiveEntry
import app.otter.domain.repository.ArchiveBrowserRepository

/**
 * Use case for browsing archive contents.
 */
class BrowseArchiveUseCase(
    private val repository: ArchiveBrowserRepository,
) {

    /**
     * Lists entries at the given path inside an archive.
     *
     * @param archiveUri URI of the archive file
     * @param path Path inside archive (empty for root)
     * @return Result containing sorted list of entries (directories first, then alphabetical)
     */
    suspend operator fun invoke(archiveUri: Uri, path: String = ""): Result<List<ArchiveEntry>> {
        return repository.listEntries(archiveUri, path).map { entries ->
            entries.sortedWith(
                compareBy<ArchiveEntry> { !it.isDirectory }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
        }
    }
}
