package app.otter.data.extractor

import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import app.otter.util.PathValidator
import java.io.File
import javax.inject.Inject

class SevenZipExtractor @Inject constructor(
    private val pathValidator: PathValidator,
    private val archiveLibraryManager: ArchiveLibraryManager
) : BaseArchiveExtractor() {

    override fun supports(type: ArchiveType): Boolean = type == ArchiveType.SEVEN_ZIP

    override fun getTag(): String = "7ZIP"

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
