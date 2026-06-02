package app.otter.data.repository

import android.content.Context
import android.net.Uri
import app.otter.data.extractor.ArchiveExtractor
import app.otter.data.util.ResourcePathConverter
import app.otter.domain.model.ArchiveFile
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import app.otter.domain.model.ResourcePath
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
        destinationPath: ResourcePath,
        selectedItems: List<String>?
    ): Flow<ExtractionProgress> = callbackFlow {
        try {
            send(ExtractionProgress.Idle)

            val extractor = extractors.firstOrNull { it.supports(archive.type) }
                ?: run {
                    send(ExtractionProgress.Error("No extractor for ${archive.type}", null))
                    close()
                    return@callbackFlow
                }

            val archiveUri = ResourcePathConverter.toUri(archive.path)
            val destinationUri = ResourcePathConverter.toUri(destinationPath)
            val destinationFile = File(destinationUri.path ?: run {
                send(ExtractionProgress.Error("Invalid destination path", null))
                close()
                return@callbackFlow
            })
            if (!destinationFile.exists()) {
                destinationFile.mkdirs()
            }

            // Extract source filename for extractors that need it (e.g., GZIP)
            val sourceFileName = getFileNameFromUri(context, archiveUri)

            // Emit progress events in real-time
            val result = context.contentResolver.openInputStream(archiveUri)?.use { inputStream ->
                extractor.extract(inputStream, destinationFile, archive.type, sourceFileName, selectedItems) { progress ->
                    trySend(progress)
                }
            } ?: run {
                send(ExtractionProgress.Error("Cannot open archive", null))
                close()
                return@callbackFlow
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

    /**
     * Extracts the filename from a URI using ContentResolver.
     * Falls back to the last path segment if query fails.
     */
    private fun getFileNameFromUri(context: Context, uri: Uri): String {
        // Try to query display name from content resolver
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    val displayName = cursor.getString(nameIndex)
                    if (!displayName.isNullOrBlank()) {
                        return displayName
                    }
                }
            }
        }

        // Fallback: extract from URI path
        return uri.lastPathSegment ?: "unknown"
    }
}
