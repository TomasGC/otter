package app.otter.data.repository

import android.content.Context
import android.net.Uri
import app.otter.data.extractor.ArchiveExtractor
import app.otter.domain.model.ArchiveFile
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import app.otter.domain.repository.ArchiveRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
    ): Flow<ExtractionProgress> = callbackFlow {
        try {
            send(ExtractionProgress.Idle)

            val extractor = extractors.firstOrNull { it.supports(archive.type) }
                ?: run {
                    send(ExtractionProgress.Error("No extractor for ${archive.type}", null))
                    close()
                    return@callbackFlow
                }

            val inputStream = context.contentResolver.openInputStream(archive.uri)
                ?: run {
                    send(ExtractionProgress.Error("Cannot open archive", null))
                    close()
                    return@callbackFlow
                }

            val destinationPath = File(destinationUri.path)
            if (!destinationPath.exists()) {
                destinationPath.mkdirs()
            }

            // Emit progress events in real-time
            val result = extractor.extract(inputStream, destinationPath) { progress ->
                trySend(progress)
            }

            when (result) {
                is ExtractionResult.Success -> send(
                    ExtractionProgress.Success(
                        outputPath = result.outputPath,
                        extractedCount = result.extractedFilesCount
                    )
                )
                is ExtractionResult.Failure -> send(
                    ExtractionProgress.Error(
                        message = result.errorMessage,
                        exception = result.cause
                    )
                )
            }

            close()
        } catch (e: CancellationException) {
            // Extraction cancelled - close the flow cleanly
            close()
        }
        awaitClose()
    }.flowOn(Dispatchers.IO)
}
