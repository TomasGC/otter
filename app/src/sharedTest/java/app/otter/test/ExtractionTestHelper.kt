package app.otter.test

import app.otter.data.extractor.ArchiveExtractor
import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import java.io.File

/**
 * Helper for testing archive extraction with real files.
 */
object ExtractionTestHelper {

    /**
     * Extracts an archive file to a destination directory.
     *
     * @param extractor Archive extractor to use
     * @param archiveFile Source archive file
     * @param outputDir Destination directory
     * @param archiveType Type of archive
     * @return Extraction result
     */
    suspend fun extractArchive(
        extractor: ArchiveExtractor,
        archiveFile: File,
        outputDir: File,
        archiveType: ArchiveType
    ): ExtractionResult {
        outputDir.mkdirs()

        val progressList = mutableListOf<ExtractionProgress>()

        return archiveFile.inputStream().use { input ->
            extractor.extract(
                inputStream = input,
                destinationPath = outputDir,
                archiveType = archiveType,
                sourceFileName = archiveFile.name,
                onProgress = { progress -> progressList.add(progress) }
            )
        }
    }
}
