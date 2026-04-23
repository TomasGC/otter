package app.otter.domain.repository

import app.otter.domain.model.ArchiveFile
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ResourcePath
import kotlinx.coroutines.flow.Flow

interface ArchiveRepository {
    fun extractArchive(
        archive: ArchiveFile,
        destinationPath: ResourcePath
    ): Flow<ExtractionProgress>
}
