package app.otter.domain.repository

import android.net.Uri
import app.otter.domain.model.ArchiveFile
import app.otter.domain.model.ExtractionProgress
import kotlinx.coroutines.flow.Flow

interface ArchiveRepository {
    fun extractArchive(
        archive: ArchiveFile,
        destinationUri: Uri
    ): Flow<ExtractionProgress>
}
