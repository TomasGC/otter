package app.otter.data.extractor

import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry

/**
 * Abstraction for reading ZIP archive contents.
 * Allows testing ZIP extraction logic without real file I/O.
 */
interface IZipFileReader : AutoCloseable {
    /**
     * Get all entries in the ZIP archive (excluding directories).
     */
    fun getEntries(): Sequence<ZipEntry>

    /**
     * Count total files (non-directory entries) in the archive.
     */
    fun countFiles(): Int

    /**
     * Open an input stream for the given ZIP entry.
     */
    fun getInputStream(entry: ZipEntry): InputStream
}
