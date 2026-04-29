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
        sourceFileName: String,
        onProgress: (ExtractionProgress) -> Unit
    ): ExtractionResult {
        val inArchive = archiveLibraryManager.openArchive(tempFile)
        return extractWith7Zip(inArchive, destinationPath, pathValidator, onProgress)
    }
}
