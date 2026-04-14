package app.otter.data.repository

import android.content.Context
import android.net.Uri
import app.otter.data.extractor.ArchiveExtractor
import app.otter.domain.model.ArchiveFile
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import app.otter.domain.repository.ArchiveRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject

class ArchiveRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val extractors: List<@JvmSuppressWildcards ArchiveExtractor>
) : ArchiveRepository {

    override fun extractArchive(
        archive: ArchiveFile,
        destinationUri: Uri
    ): Flow<ExtractionProgress> = flow {
        emit(ExtractionProgress.Idle)

        val extractor = extractors.firstOrNull { it.supports(archive.type) }
            ?: run {
                emit(ExtractionProgress.Error("No extractor for ${archive.type}", null))
                return@flow
            }

        val inputStream = context.contentResolver.openInputStream(archive.uri)
            ?: run {
                emit(ExtractionProgress.Error("Cannot open archive", null))
                return@flow
            }

        val archiveNameWithoutExt = archive.name.removeSuffix(
            ".${archive.type.extensions.first().removePrefix(".")}"
        )
        val destinationPath = File(destinationUri.path, archiveNameWithoutExt)
        destinationPath.mkdirs()

        val progressEvents = mutableListOf<ExtractionProgress>()
        val result = extractor.extract(inputStream, destinationPath) { progress ->
            progressEvents.add(progress)
        }

        // Emit all progress events
        progressEvents.forEach { emit(it) }

        when (result) {
            is ExtractionResult.Success -> emit(
                ExtractionProgress.Success(
                    outputPath = result.outputPath,
                    extractedCount = result.extractedFilesCount
                )
            )
            is ExtractionResult.Failure -> emit(
                ExtractionProgress.Error(
                    message = result.errorMessage,
                    exception = result.cause
                )
            )
        }
    }.flowOn(Dispatchers.IO)
}
