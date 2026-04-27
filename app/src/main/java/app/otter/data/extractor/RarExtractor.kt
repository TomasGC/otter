package app.otter.data.extractor

import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import app.otter.util.PathValidator
import java.io.File
import javax.inject.Inject

class RarExtractor @Inject constructor(
    private val pathValidator: PathValidator,
    private val archiveLibraryManager: ArchiveLibraryManager
) : BaseArchiveExtractor() {

    override fun supports(type: ArchiveType): Boolean = type == ArchiveType.RAR

    override fun getTag(): String = "RAR"

    override suspend fun extractFromTempFile(
        tempFile: File,
        destinationPath: File,
        onProgress: (ExtractionProgress) -> Unit
    ): ExtractionResult {
        val inArchive = archiveLibraryManager.openArchive(tempFile)

        try {
            val callback = SevenZipCallbackExtractor(
                inArchive = inArchive,
                destinationPath = destinationPath,
                pathValidator = pathValidator
            ) { extractedCount, totalCount, fileName ->
                logExtractionProgress(extractedCount, totalCount, fileName)
                onProgress(
                    ExtractionProgress.Extracting(
                        currentFile = fileName,
                        extractedCount = extractedCount,
                        totalCount = totalCount,
                        progress = if (totalCount > 0) extractedCount.toFloat() / totalCount else 0f
                    )
                )
            }

            inArchive.extract(null, false, callback)

            val extractedCount = callback.getExtractedCount()
            logExtractionComplete(extractedCount)

            return ExtractionResult.Success(
                outputPath = destinationPath.absolutePath,
                extractedFilesCount = extractedCount
            )
        } finally {
            inArchive.close()
        }
    }
}
