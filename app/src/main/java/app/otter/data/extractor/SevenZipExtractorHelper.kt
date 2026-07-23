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
     * Collaborators for a single extraction call that aren't part of "what to extract" —
     * progress reporting, logging, zip-bomb guarding, and cancellation.
     */
    class ExtractionSession(
        val onProgress: (ExtractionProgress) -> Unit,
        val logger: ExtractionLogger,
        val sizeGuardFactory: () -> ArchiveSizeGuard = { ArchiveSizeGuard() },
        val isActiveCheck: () -> Boolean = { true }
    )

    /**
     * Extracts an archive using 7-Zip-JBinding library.
     *
     * @param inArchive The 7-Zip archive instance (will be closed by this method)
     * @param destinationPath The destination directory for extracted files
     * @param pathValidator Validator for path traversal protection
     * @param selectedPaths Set of paths to extract selectively, or null to extract all
     * @param session Progress/logging/size-guard/cancellation collaborators
     * @return ExtractionResult.Success with extracted file count
     */
    fun extract(
        inArchive: IInArchive,
        destinationPath: File,
        pathValidator: PathValidator,
        selectedPaths: Set<String>? = null,
        session: ExtractionSession
    ): ExtractionResult {
        return try {
            val callback = SevenZipCallbackExtractor(
                inArchive = inArchive,
                destinationPath = destinationPath,
                pathValidator = pathValidator,
                selectedPaths = selectedPaths,
                config = SevenZipCallbackExtractor.Config(
                    sizeGuard = session.sizeGuardFactory(),
                    isActiveCheck = session.isActiveCheck
                ) { extractedCount, totalCount, fileName ->
                    session.logger.logProgress(extractedCount, totalCount, fileName)
                    val progress = progressCalculator.calculate(extractedCount, totalCount)
                    session.onProgress(
                        ExtractionProgress.Extracting(
                            currentFile = fileName,
                            extractedCount = extractedCount,
                            totalCount = totalCount,
                            progress = progress
                        )
                    )
                }
            )

            inArchive.extract(null, false, callback)

            if (callback.hasErrors()) {
                throw java.io.IOException("Archive extraction encountered errors (corrupted or unreadable entries)")
            }

            val extractedCount = callback.getExtractedCount()
            session.logger.logComplete(extractedCount)

            ExtractionResult.Success(
                outputPath = destinationPath.absolutePath,
                extractedFilesCount = extractedCount
            )
        } finally {
            inArchive.close()
        }
    }
}
