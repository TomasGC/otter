package app.otter.data.extractor

import app.otter.domain.model.ArchiveType
import timber.log.Timber
import java.io.File
import java.io.InputStream
import javax.inject.Inject

/**
 * Implementation of temporary file management for archive extraction.
 *
 * Some archive formats (RAR, 7z, TAR with counting) require random access
 * and cannot be extracted directly from an InputStream. This class handles
 * the creation and validation of temporary files for these formats.
 *
 * Responsibilities:
 * - Create temporary files with proper extension (for format detection)
 * - Copy InputStream content to temp file
 * - Validate temp file (exists, non-empty)
 * - Provide logging for debugging
 */
class TempFileManager @Inject constructor() : ITempFileManager {

    override fun createTempFile(
        inputStream: InputStream,
        archiveType: ArchiveType,
        tag: String
    ): File {
        // Use proper extension for format detection (critical for .tar.gz and .tgz)
        val extension = archiveType.extensions.first()

        val tempFile = File.createTempFile(TEMP_FILE_PREFIX, extension)
        Timber.tag(tag).d("Created temp file: ${tempFile.absolutePath}")

        val bytesCopied = tempFile.outputStream().use { output ->
            inputStream.copyTo(output)
        }
        Timber.tag(tag).d("Copied $bytesCopied bytes to temp file. File size: ${tempFile.length()}")

        if (!tempFile.exists() || tempFile.length() == 0L) {
            val error = "Temp file is empty or doesn't exist"
            Timber.tag(tag).e(error)
            throw IllegalStateException(error)
        }

        return tempFile
    }

    companion object {
        private const val TEMP_FILE_PREFIX = "otter_archive_"
    }
}
