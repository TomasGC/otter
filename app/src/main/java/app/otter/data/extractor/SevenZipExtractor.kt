package app.otter.data.extractor

import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import app.otter.util.PathValidator
import kotlinx.coroutines.isActive
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

class SevenZipExtractor @Inject constructor(
    private val pathValidator: PathValidator,
    private val archiveLibraryManager: ArchiveLibraryManager,
    tempFileManager: ITempFileManager,
    sevenZipHelper: SevenZipExtractorHelper,
    private val sizeGuardFactory: () -> ArchiveSizeGuard = { ArchiveSizeGuard() }
) : BaseArchiveExtractor(tempFileManager, sevenZipHelper) {

    override fun supports(type: ArchiveType): Boolean = type == ArchiveType.SEVEN_ZIP

    override fun getTag(): String = "7ZIP"

    override suspend fun extractInternal(
        inputStream: InputStream,
        destinationPath: File,
        archiveType: ArchiveType,
        sourceFileName: String,
        selectedItems: List<String>?,
        onProgress: (ExtractionProgress) -> Unit
    ): ExtractionResult {
        val activeContext = coroutineContext
        return extractWithTempFile(inputStream, archiveType) { tempFile ->
            val inArchive = archiveLibraryManager.openArchive(tempFile)
            sevenZipHelper.extract(
                inArchive, destinationPath, pathValidator, selectedItems?.toSet(),
                SevenZipExtractorHelper.ExtractionSession(
                    onProgress, logger, sizeGuardFactory, isActiveCheck = { activeContext.isActive }
                )
            )
        }
    }
}
