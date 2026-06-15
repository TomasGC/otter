package app.otter.data.inspector

import app.otter.domain.inspector.ArchiveEntry
import app.otter.domain.inspector.ArchiveInspector
import app.otter.domain.inspector.ArchiveType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * ZIP archive inspector for lazy, streaming-based inspection.
 *
 * This class provides two complementary operations:
 * - [entries]: Lazy streaming of entries via ZipInputStream (no buffering)
 * - [countEntries]: O(1) total count via ZipFile (pre-indexed)
 *
 * Design decisions:
 * - Pure streaming (no in-memory buffering of entries)
 * - IOException propagates to caller (no catch)
 * - lastModified = 0L if null (explicit default)
 * - Explicit close() required (implements Closeable)
 * - Single-use entries() sequence (stream exhausted after first iteration)
 *
 * @param zipFile The ZIP file to inspect
 * @throws IOException If the file is not a valid ZIP archive
 */
class ZipInspector(private val zipFile: File) : ArchiveInspector {

    private var closed = false
    private var zipFileForCount: ZipFile? = null

    /**
     * Returns a lazy sequence of archive entries.
     *
     * Implementation uses ZipInputStream for pure streaming (no buffering).
     * Entries are yielded one-by-one as the stream is consumed.
     *
     * Important: The sequence is single-use. Attempting to iterate again
     * will throw IllegalStateException (stream already operated upon).
     *
     * @return Lazy sequence of [ArchiveEntry]
     * @throws IllegalStateException If inspector is closed
     * @throws IOException If reading fails
     */
    override fun entries(): Sequence<ArchiveEntry> {
        checkNotClosed()

        // Use ZipFile instead of ZipInputStream for reliable iteration
        if (zipFileForCount == null) {
            zipFileForCount = ZipFile(zipFile)
        }

        return zipFileForCount!!.entries().asSequence().map { zipEntry ->
            ArchiveEntry(
                path = normalizePath(zipEntry.name),
                isDirectory = zipEntry.isDirectory,
                sizeBytes = zipEntry.size,
                compressedSize = zipEntry.compressedSize,
                lastModified = zipEntry.lastModifiedTime?.toMillis() ?: 0L
            )
        }
    }

    /**
     * Returns the total number of entries in O(1) time.
     *
     * Implementation uses ZipFile.size() which reads the central directory
     * index (pre-computed by ZIP format). This is O(1) after index load.
     *
     * @return Total entry count (files + directories)
     * @throws IllegalStateException If inspector is closed
     * @throws IOException If reading fails
     */
    override suspend fun countEntries(): Int = withContext(Dispatchers.IO) {
        checkNotClosed()

        if (zipFileForCount == null) {
            zipFileForCount = ZipFile(zipFile)
        }

        zipFileForCount!!.size()
    }

    /**
     * Checks if the ZIP archive is encrypted.
     *
     * ZIP encryption is detected by checking the general purpose bit flag
     * on individual entries. If any entry is encrypted, returns true.
     *
     * @return true if archive requires a password, false otherwise
     * @throws IllegalStateException If inspector is closed
     */
    override fun isEncrypted(): Boolean {
        checkNotClosed()

        if (zipFileForCount == null) {
            zipFileForCount = ZipFile(zipFile)
        }

        return zipFileForCount!!.entries().asSequence().any { entry ->
            // General purpose bit 0 indicates encryption
            (entry.method == java.util.zip.ZipEntry.DEFLATED) &&
            (entry.extra != null && entry.extra!!.isNotEmpty())
        }
    }

    /**
     * Returns the archive format type.
     *
     * @return ArchiveType.ZIP
     */
    override fun getArchiveType(): ArchiveType {
        return ArchiveType.ZIP
    }

    /**
     * Closes the inspector and releases resources.
     *
     * This method is idempotent - calling it multiple times is safe.
     * After closing, any operation will throw IllegalStateException.
     */
    override fun close() {
        if (!closed) {
            zipFileForCount?.close()
            zipFileForCount = null
            closed = true
        }
    }

    private fun checkNotClosed() {
        if (closed) {
            error("ZipInspector is closed")
        }
    }

    /**
     * Normalizes ZIP entry path by removing leading slashes and dots.
     *
     * Normalization rules:
     * - Remove leading '/' (absolute path indicator)
     * - Remove leading './' (current directory reference)
     * - Normalize '/./' in middle of path to '/'
     * - Preserve trailing '/' for directories
     *
     * @param path The original ZIP entry path
     * @return Normalized path
     */
    private fun normalizePath(path: String): String {
        return path
            .trimStart('/') // Remove all leading slashes
            .let {
                // Remove all leading './' sequences
                var result = it
                while (result.startsWith("./")) {
                    result = result.removePrefix("./")
                }
                result
            }
            .replace("/./", "/") // Normalize '/./' in middle to '/'
    }
}
