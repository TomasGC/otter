package app.otter.data.inspector

import app.otter.domain.inspector.ArchiveInspector
import app.otter.domain.inspector.ArchiveType
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArchiveInspectorFactory @Inject constructor() {

    /**
     * Creates a new ArchiveInspector instance for the given file.
     * Detection is performed each time this method is called.
     *
     * @param file The archive file to inspect
     * @return Result containing the inspector or an error
     */
    fun create(file: File): Result<ArchiveInspector> {
        return runCatching {
            // Validate file
            when {
                !file.exists() -> throw IllegalArgumentException("File does not exist: ${file.absolutePath}")
                !file.isFile -> throw IllegalArgumentException("Path is not a file: ${file.absolutePath}")
            }

            // Detect format
            val archiveType = detectFormat(file)

            // Create inspector based on detected type
            when (archiveType) {
                ArchiveType.ZIP -> ZipInspector(file)
                ArchiveType.RPA -> RpaInspector(file)
                ArchiveType.RAR -> throw UnsupportedOperationException("RAR format is not yet supported")
                ArchiveType.SEVEN_ZIP -> throw UnsupportedOperationException("SEVEN_ZIP format is not yet supported")
                ArchiveType.TAR -> throw UnsupportedOperationException("TAR format is not yet supported")
                ArchiveType.TAR_GZ -> throw UnsupportedOperationException("TAR_GZ format is not yet supported")
                ArchiveType.TAR_BZ2 -> throw UnsupportedOperationException("TAR_BZ2 format is not yet supported")
            }
        }
    }

    /**
     * Detects the archive format by checking file extension first,
     * then falling back to magic bytes if extension is ambiguous.
     */
    private fun detectFormat(file: File): ArchiveType {
        // Try extension first
        val typeByExtension = detectByExtension(file.name)

        // If extension detection succeeds and matches ZIP, verify with magic bytes
        if (typeByExtension == ArchiveType.ZIP) {
            // Verify ZIP magic bytes (PK\x03\x04)
            val magicBytes = readMagicBytes(file, 4)
            if (magicBytes.size >= 4 &&
                magicBytes[0] == 0x50.toByte() &&
                magicBytes[1] == 0x4B.toByte() &&
                magicBytes[2] == 0x03.toByte() &&
                magicBytes[3] == 0x04.toByte()
            ) {
                return ArchiveType.ZIP
            }
        }

        // If no extension match, try magic bytes
        if (typeByExtension == null) {
            val typeByMagic = detectByMagicBytes(file)
            if (typeByMagic != null) {
                return typeByMagic
            }
        }

        // If extension detection succeeded (but not ZIP), return it
        if (typeByExtension != null) {
            return typeByExtension
        }

        // Unknown format
        throw IllegalArgumentException(
            "Could not detect archive format for file: ${file.name}"
        )
    }

    /**
     * Detects archive format by reading magic bytes from the file header.
     */
    private fun detectByMagicBytes(file: File): ArchiveType? {
        val magicBytes = readMagicBytes(file, 8)

        return when {
            // ZIP: PK\x03\x04
            magicBytes.size >= 4 &&
                    magicBytes[0] == 0x50.toByte() &&
                    magicBytes[1] == 0x4B.toByte() &&
                    magicBytes[2] == 0x03.toByte() &&
                    magicBytes[3] == 0x04.toByte() -> ArchiveType.ZIP

            // RAR: Rar!\x1A\x07
            magicBytes.size >= 6 &&
                    magicBytes[0] == 0x52.toByte() &&
                    magicBytes[1] == 0x61.toByte() &&
                    magicBytes[2] == 0x72.toByte() &&
                    magicBytes[3] == 0x21.toByte() &&
                    magicBytes[4] == 0x1A.toByte() &&
                    magicBytes[5] == 0x07.toByte() -> ArchiveType.RAR

            // 7z: 7z\xBC\xAF\x27\x1C
            magicBytes.size >= 6 &&
                    magicBytes[0] == 0x37.toByte() &&
                    magicBytes[1] == 0x7A.toByte() &&
                    magicBytes[2] == 0xBC.toByte() &&
                    magicBytes[3] == 0xAF.toByte() &&
                    magicBytes[4] == 0x27.toByte() &&
                    magicBytes[5] == 0x1C.toByte() -> ArchiveType.SEVEN_ZIP

            // Unknown
            else -> null
        }
    }

    /**
     * Detects archive format by file extension.
     */
    private fun detectByExtension(fileName: String): ArchiveType? {
        val lowerName = fileName.lowercase()
        return when {
            lowerName.endsWith(".zip") -> ArchiveType.ZIP
            lowerName.endsWith(".rar") -> ArchiveType.RAR
            lowerName.endsWith(".7z") -> ArchiveType.SEVEN_ZIP
            lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tgz") -> ArchiveType.TAR_GZ
            lowerName.endsWith(".tar.bz2") || lowerName.endsWith(".tbz2") -> ArchiveType.TAR_BZ2
            lowerName.endsWith(".tar") -> ArchiveType.TAR
            lowerName.endsWith(".rpa") -> ArchiveType.RPA
            else -> null
        }
    }

    /**
     * Reads the first N bytes from a file.
     */
    private fun readMagicBytes(file: File, count: Int): ByteArray {
        return FileInputStream(file).use { input ->
            val buffer = ByteArray(count)
            val bytesRead = input.read(buffer)
            if (bytesRead < count) {
                buffer.copyOfRange(0, bytesRead)
            } else {
                buffer
            }
        }
    }
}
