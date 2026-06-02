package app.otter.domain.usecase

import app.otter.domain.model.ArchiveFile
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ResourcePath
import app.otter.domain.repository.ArchiveRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class ExtractArchiveUseCase @Inject constructor(
    private val repository: ArchiveRepository
) {
    operator fun invoke(
        archive: ArchiveFile,
        destinationPath: ResourcePath,
        selectedItems: List<String>? = null
    ): Flow<ExtractionProgress> = flow {
        if (archive.sizeBytes == 0L) {
            emit(ExtractionProgress.Error("Archive is empty", null))
            return@flow
        }

        repository.extractArchive(archive, destinationPath, selectedItems)
            .collect { progress ->
                emit(progress)
            }
    }
}
