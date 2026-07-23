package app.otter.util

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validates file paths for security (path traversal protection).
 * Extracted from extractors for DRY principle and testability.
 */
@Singleton
class PathValidator @Inject constructor() {

    /**
     * Validates that the output file is within the destination directory.
     * Prevents path traversal attacks (../../etc/passwd).
     *
     * @param outputFile The file to be created
     * @param destinationPath The extraction destination root
     * @param entryName The archive entry name (for error messages)
     * @throws SecurityException if path traversal is detected
     */
    fun validatePath(outputFile: File, destinationPath: File, entryName: String) {
        val canonicalOutput = try {
            outputFile.canonicalPath
        } catch (e: java.io.IOException) {
            throw SecurityException("Entry has an invalid path: $entryName", e)
        }
        if (!canonicalOutput.startsWith(destinationPath.canonicalPath + File.separator)) {
            throw SecurityException("Entry outside destination: $entryName")
        }
    }

    /**
     * Validates a path string before creating a File object.
     * Checks for common path traversal patterns.
     *
     * @param path The path string to validate
     * @return true if path appears safe, false otherwise
     */
    fun isSafePath(path: String): Boolean {
        // Reject paths with traversal patterns
        if (path.contains("..")) return false

        // Reject absolute paths on Unix/Linux
        if (path.startsWith("/")) return false

        // Reject UNC network paths on Windows (\\server\share\...)
        if (path.startsWith("\\\\")) return false

        // Reject absolute (C:\...) and drive-relative (C:foo) Windows paths.
        // Drive-relative paths resolve against that drive's current directory,
        // not the destination folder, so a bare backslash check is not enough.
        if (path.matches(Regex("^[A-Za-z]:.+"))) return false

        return true
    }

    /**
     * Creates a safe output file within the destination directory.
     * Validates the path and creates parent directories if needed.
     *
     * @param destinationPath The extraction destination root
     * @param entryName The archive entry name
     * @return The validated output File
     * @throws SecurityException if path traversal is detected
     */
    fun createSafeOutputFile(destinationPath: File, entryName: String): File {
        if (!isSafePath(entryName)) {
            throw SecurityException("Entry outside destination: $entryName")
        }
        val outputFile = File(destinationPath, entryName)
        validatePath(outputFile, destinationPath, entryName)
        outputFile.parentFile?.mkdirs()
        return outputFile
    }
}
