package app.otter.test.fakes

import app.otter.data.extractor.ITempFileManager
import app.otter.domain.model.ArchiveType
import java.io.File
import java.io.InputStream

/**
 * Simple test implementation of ITempFileManager for integration tests.
 * Uses real file system operations in a temporary directory.
 */
class SimpleTempFileManager : ITempFileManager {

    private val tempFiles = mutableListOf<File>()

    override fun createTempFile(
        inputStream: InputStream,
        archiveType: ArchiveType,
        tag: String
    ): File {
        val extension = when (archiveType) {
            ArchiveType.ZIP -> "zip"
            ArchiveType.RAR -> "rar"
            ArchiveType.SEVEN_ZIP -> "7z"
            ArchiveType.TAR -> "tar"
            ArchiveType.TAR_GZ -> "tar.gz"
            ArchiveType.GZIP -> "gz"
            ArchiveType.RPA -> "rpa"
        }

        val tempFile = kotlin.io.path.createTempFile(suffix = ".$extension").toFile()
        tempFile.outputStream().use { output ->
            inputStream.copyTo(output)
        }

        if (!tempFile.exists() || tempFile.length() == 0L) {
            throw IllegalStateException("Temp file is empty or doesn't exist: ${tempFile.absolutePath}")
        }

        tempFiles.add(tempFile)
        return tempFile
    }

    /**
     * Clean up all temp files created during tests.
     */
    fun cleanup() {
        tempFiles.forEach { file ->
            if (file.exists()) {
                file.delete()
            }
        }
        tempFiles.clear()
    }
}
