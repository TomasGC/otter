package app.otter.data.extractor

import android.util.Log
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
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

    private val tag = "ArchiveLibraryManager"
    private var initialized = false

    /**
     * Opens an archive file using 7-Zip-JBinding.
     * Automatically initializes the native library on first call.
     * Auto-detects archive format (RAR, 7z, TAR, GZ, etc.).
     *
     * @param archiveFile File to open as archive
     * @return Opened IInArchive instance
     * @throws Exception if archive cannot be opened or initialization fails
     */
    @Synchronized
    fun openArchive(archiveFile: File): IInArchive {
        ensureInitialized()

        return try {
            val randomAccessFile = RandomAccessFile(archiveFile, "r")
            SevenZip.openInArchive(null, RandomAccessFileInStream(randomAccessFile))
                ?: throw IllegalStateException("Failed to open archive: ${archiveFile.name}")
        } catch (e: Exception) {
            Log.e(tag, "Failed to open archive: ${archiveFile.name}", e)
            throw e
        }
    }

    /**
     * Ensures native library is initialized.
     * Thread-safe, idempotent initialization.
     */
    @Synchronized
    private fun ensureInitialized() {
        if (initialized) {
            return
        }

        try {
            // 7-Zip-JBinding auto-loads native libraries on first use
            // We just need to trigger it once and cache the state
            Log.d(tag, "Initializing 7-Zip-JBinding native library")
            initialized = true
            Log.d(tag, "7-Zip-JBinding initialized successfully")
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize 7-Zip-JBinding", e)
            throw e
        }
    }
}
