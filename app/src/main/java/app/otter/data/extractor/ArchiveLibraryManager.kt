package app.otter.data.extractor

import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import net.sf.sevenzipjbinding.impl.VolumedArchiveInStream
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
        private const val ERROR_MESSAGE_MULTI_VOLUME_FAILED = "failed to open multi-volume archive"
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

    /**
     * Opens a potentially multi-volume archive using [VolumedArchiveInStream].
     *
     * Works for both single-volume and multi-volume archives:
     * - Single-volume: the callback is called once for the first file, then returns null.
     * - Multi-volume (RAR old/new-style, 7z .001/.002/...): the library requests each
     *   successive volume by name through the callback.
     *
     * Returns the opened [IInArchive] and the [MultiVolumeCallback] that must be closed
     * after extraction to release the underlying [RandomAccessFile] handles.
     *
     * @param baseFile The first (or only) volume file.
     */
    @Synchronized
    internal fun openVolumedArchive(baseFile: File): Pair<IInArchive, MultiVolumeCallback> {
        val callback = MultiVolumeCallback(baseFile.parentFile
            ?: error("Archive file has no parent directory: ${baseFile.absolutePath}"))
        return try {
            val volumedStream = VolumedArchiveInStream(baseFile.name, callback)
            val inArchive = SevenZip.openInArchive(null, volumedStream)
                ?: error("Failed to open archive: ${baseFile.name} ($ERROR_MESSAGE_MULTI_VOLUME_FAILED)")
            Timber.tag(TAG).d("Opened volumed archive: ${baseFile.name}")
            inArchive to callback
        } catch (e: Exception) {
            callback.close()
            Timber.tag(TAG).e(e, "Failed to open volumed archive: ${baseFile.name}")
            throw e
        }
    }
}
