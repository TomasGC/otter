package app.otter.domain.repository

import app.otter.domain.model.ArchiveEntry
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ResourcePath
import kotlinx.coroutines.flow.Flow

/**
 * Repository for browsing and extracting archives.
 */
interface ArchiveBrowserRepository {

    /**
     * Lists entries at the given path inside an archive.
     *
     * @param archivePath Path to the archive file
     * @param path Path inside archive (empty for root)
     * @return Result containing list of entries at the given path
     */
    suspend fun listEntries(archivePath: ResourcePath, path: String = ""): Result<List<ArchiveEntry>>

    /**
     * Extracts selected entries from an archive.
     *
     * @param archivePath Path to the archive file
     * @param entryPaths List of entry paths to extract
     * @param destinationPath Destination directory path
     * @return Flow of extraction progress events
     */
    fun extractSelected(
        archivePath: ResourcePath,
        entryPaths: List<String>,
        destinationPath: ResourcePath,
    ): Flow<ExtractionProgress>
}
