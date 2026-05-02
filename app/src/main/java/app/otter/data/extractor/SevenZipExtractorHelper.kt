package app.otter.data.extractor

import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import app.otter.util.PathValidator
import net.sf.sevenzipjbinding.IInArchive
import java.io.File

/**
 * Helper for extracting archives using 7-Zip-JBinding library.
 *
 * Provides common extraction logic for RAR, 7z, and TAR formats that use
 * the 7-Zip-JBinding library. Eliminates code duplication across these extractors.
 *
 * Responsibilities:
 * - Execute 7-Zip extraction with callback
 * - Handle progress reporting via SevenZipCallbackExtractor
 * - Ensure archive is properly closed
 * - Return extraction results
 */
class SevenZipExtractorHelper(
    private val progressCalculator: ProgressCalculator = StandardProgressCalculator()
) {

    /**
     * Extracts an archive using 7-Zip-JBinding library.
     *
     * @param inArchive The 7-Zip archive instance (will be closed by this method)
     * @param destinationPath The destination directory for extracted files
     * @param pathValidator Validator for path traversal protection
     * @param onProgress Progress callback
     * @param logger Logger for extraction progress
     * @return ExtractionResult.Success with extracted file count
     */
    fun extract(
        inArchive: IInArchive,
        destinationPath: File,
        pathValidator: PathValidator,
        onProgress: (ExtractionProgress) -> Unit,
        logger: ExtractionLogger
    ): ExtractionResult {
        return try {
            val callback = SevenZipCallbackExtractor(
                inArchive = inArchive,
                destinationPath = destinationPath,
                pathValidator = pathValidator
            ) { extractedCount, totalCount, fileName ->
                logger.logProgress(extractedCount, totalCount, fileName)
                val progress = progressCalculator.calculate(extractedCount, totalCount)
                onProgress(
                    ExtractionProgress.Extracting(
                        currentFile = fileName,
                        extractedCount = extractedCount,
                        totalCount = totalCount,
                        progress = progress
                    )
                )
            }

            inArchive.extract(null, false, callback)

            val extractedCount = callback.getExtractedCount()
            logger.logComplete(extractedCount)

            ExtractionResult.Success(
                outputPath = destinationPath.absolutePath,
                extractedFilesCount = extractedCount
            )
        } finally {
            inArchive.close()
        }
    }
}
