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
    private val archiveLibraryManager: ArchiveLibraryManager,
    tempFileManager: ITempFileManager,
    sevenZipHelper: SevenZipExtractorHelper
) : BaseArchiveExtractor(tempFileManager, sevenZipHelper) {

    override fun supports(type: ArchiveType): Boolean = type == ArchiveType.RAR

    override fun getTag(): String = "RAR"

    override suspend fun extractInternal(
        inputStream: InputStream,
        destinationPath: File,
        archiveType: ArchiveType,
        sourceFileName: String,
        selectedItems: List<String>?,
        onProgress: (ExtractionProgress) -> Unit
    ): ExtractionResult {
        return extractWithTempFile(inputStream, archiveType) { tempFile ->
            val inArchive = archiveLibraryManager.openArchive(tempFile)
            sevenZipHelper.extract(inArchive, destinationPath, pathValidator, onProgress, logger)
        }
    }
}
