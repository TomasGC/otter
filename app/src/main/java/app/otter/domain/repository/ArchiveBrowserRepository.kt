package app.otter.domain.repository

import android.net.Uri
import app.otter.domain.model.ArchiveEntry
import app.otter.domain.model.ExtractionProgress
import kotlinx.coroutines.flow.Flow

/**
 * Repository for browsing and extracting archives.
 */
interface ArchiveBrowserRepository {

    /**
     * Lists entries at the given path inside an archive.
     *
     * @param archiveUri URI of the archive file
     * @param path Path inside archive (empty for root)
     * @return Result containing list of entries at the given path
     */
    suspend fun listEntries(archiveUri: Uri, path: String = ""): Result<List<ArchiveEntry>>

    /**
     * Extracts selected entries from an archive.
     *
     * @param archiveUri URI of the archive file
     * @param entryPaths List of entry paths to extract
     * @param destinationUri Destination directory URI
     * @return Flow of extraction progress events
     */
    fun extractSelected(
        archiveUri: Uri,
        entryPaths: List<String>,
        destinationUri: Uri,
    ): Flow<ExtractionProgress>
}
