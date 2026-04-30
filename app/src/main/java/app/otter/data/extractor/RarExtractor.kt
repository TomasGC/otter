package app.otter.data.extractor

import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import app.otter.util.PathValidator
import java.io.File
import java.io.InputStream
import javax.inject.Inject

class RarExtractor @Inject constructor(
    private val pathValidator: PathValidator,
    private val archiveLibraryManager: ArchiveLibraryManager
) : BaseArchiveExtractor() {

    override fun supports(type: ArchiveType): Boolean = type == ArchiveType.RAR

    override fun getTag(): String = "RAR"

    override suspend fun extractInternal(
        inputStream: InputStream,
        destinationPath: File,
        archiveType: ArchiveType,
        sourceFileName: String,
        onProgress: (ExtractionProgress) -> Unit
    ): ExtractionResult {
        return extractWithTempFile(inputStream, destinationPath, archiveType, sourceFileName, onProgress) { tempFile ->
            val inArchive = archiveLibraryManager.openArchive(tempFile)
            extractWith7Zip(inArchive, destinationPath, pathValidator, onProgress)
        }
    }
}
