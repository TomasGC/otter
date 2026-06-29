package app.otter.domain.inspector

import java.io.Closeable
import java.io.InputStream

/**
 * Interface for inspecting archive contents without extraction.
 *
 * Provides read-only access to archive metadata and structure.
 * Implementations must be thread-safe for read operations after initialization.
 *
 * Usage:
 * ```kotlin
 * ArchiveInspectorFactory.create(inputStream, ArchiveType.ZIP).use { inspector ->
 *     val totalEntries = inspector.countEntries()
 *     val entries = inspector.listEntries()
 *     // Process entries...
 * }
 * ```
 */
interface ArchiveInspector : Closeable {

    /**
     * Returns the total number of entries in the archive.
     *
     * Counts both files and directories.
     *
     * @return Total entry count (≥ 0)
     */
    suspend fun countEntries(): Int

    /**
     * Returns a sequence of all entries in the archive.
     *
     * Returns entries in archive-order (implementation-defined).
     * The sequence is lazy and should be consumed only once.
     *
     * @return Sequence of archive entries (never null, may be empty)
     */
    fun entries(): Sequence<ArchiveEntry>

    /**
     * Checks if the archive is encrypted.
     *
     * Format-specific:
     * - ZIP: Checks for encryption flags
     * - RAR/7z: Checks for password protection
     * - TAR: Always returns false (unsupported)
     *
     * @return true if archive requires a password, false otherwise
     */
    fun isEncrypted(): Boolean

    /**
     * Returns the archive format type.
     *
     * @return Archive type (ZIP, RAR, 7Z, TAR, etc.)
     */
    fun getArchiveType(): ArchiveType

    /**
     * Releases underlying resources (streams, native handles, cached data).
     *
     * Safe to call multiple times (idempotent).
     *
     * After calling close(), all other methods will throw [IllegalStateException].
     */
    override fun close()
}

/**
 * Supported archive formats.
 */
enum class ArchiveType {
    ZIP,
    RAR,
    SEVEN_ZIP,
    TAR,
    TAR_GZ,
    TAR_BZ2,
    GZIP,
    RPA
}
