package app.otter.data.extractor

import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton manager for 7-Zip-JBinding native library lifecycle.
 *
 * 7-Zip-JBinding supports multiple archive formats: RAR, 7z, TAR, GZ, BZIP2, XZ, etc.
 *
 * Problem: Multiple extractors loading native libraries independently causes issues
 * during cleanup, especially in Android instrumented tests where emulator shutdown hangs.
 *
 * Solution: Single manager that handles library initialization once and provides
 * archive opening functionality to all extractors (RarExtractor, SevenZipExtractor, etc.).
 *
 * Benefits:
 * - Native library loaded only once per application lifecycle
 * - Consistent error handling for library initialization
 * - Centralized logging for debugging
 * - Prevents emulator shutdown issues in CI tests
 * - Reduces memory footprint (single library instance)
 */
@Singleton
class ArchiveLibraryManager @Inject constructor() {

    companion object {
        private const val TAG = "ArchiveLibraryManager"
        private const val ERROR_MESSAGE_UNSUPPORTED_FORMAT = "unsupported format or corrupted"
    }

    /**
     * Opens an archive file using 7-Zip-JBinding.
     * Auto-detects archive format (RAR, 7z, TAR, GZ, etc.).
     *
     * Thread-safe: Multiple calls can safely execute concurrently.
     * The native library is loaded automatically on first use by 7-Zip-JBinding.
     *
     * Note on resource management: The RandomAccessFile is wrapped in RandomAccessFileInStream
     * and ownership is transferred to the IInArchive. When IInArchive.close() is called,
     * it closes the underlying stream and file handle.
     *
     * @param archiveFile File to open as archive
     * @return Opened IInArchive instance
     * @throws IllegalStateException if archive format is not recognized
     * @throws Exception if archive cannot be opened
     */
    @Synchronized
    fun openArchive(archiveFile: File): IInArchive {
        return try {
            val randomAccessFile = RandomAccessFile(archiveFile, "r")
            SevenZip.openInArchive(null, RandomAccessFileInStream(randomAccessFile))
                ?: error("Failed to open archive: ${archiveFile.name} ($ERROR_MESSAGE_UNSUPPORTED_FORMAT)")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to open archive: ${archiveFile.name}")
            throw e
        }
    }
}
