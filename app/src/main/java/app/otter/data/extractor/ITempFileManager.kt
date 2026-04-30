package app.otter.data.extractor

import app.otter.domain.model.ArchiveType
import java.io.File
import java.io.InputStream

/**
 * Interface for temporary file management in archive extraction.
 *
 * Defines the contract for creating and managing temporary files needed
 * by archive formats that require random access (RAR, 7z, TAR with counting).
 *
 * Benefits of this interface:
 * - DIP: High-level extractors depend on abstraction, not concrete implementation
 * - Testability: Easy to mock for unit tests without filesystem operations
 * - Flexibility: Can swap implementations (e.g., in-memory temp files, custom temp directories)
 */
interface ITempFileManager {
    /**
     * Creates a temporary file from an InputStream with proper extension.
     *
     * @param inputStream The input stream to copy to temp file
     * @param archiveType The archive type (determines file extension)
     * @param tag Log tag for Timber logging
     * @return Created temporary file (caller responsible for deletion)
     * @throws IllegalStateException if temp file is empty or doesn't exist
     */
    fun createTempFile(
        inputStream: InputStream,
        archiveType: ArchiveType,
        tag: String
    ): File
}
