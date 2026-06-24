package app.otter.data.inspector

import app.otter.domain.inspector.ArchiveInspector
import app.otter.domain.inspector.ArchiveType
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArchiveInspectorFactory @Inject constructor() {

    companion object {
        private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        private val RAR_MAGIC = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07)
        private val SEVEN_ZIP_MAGIC = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)
        private val GZIP_MAGIC = byteArrayOf(0x1F, 0x8B.toByte())
    }

    fun create(file: File): Result<ArchiveInspector> {
        return runCatching {
            require(file.exists()) { "File does not exist: ${file.absolutePath}" }
            require(file.isFile) { "Path is not a file: ${file.absolutePath}" }
            createInspectorForType(detectFormat(file), file)
        }
    }

    private fun createInspectorForType(archiveType: ArchiveType, file: File): ArchiveInspector = when (archiveType) {
        ArchiveType.ZIP -> ZipInspector(file)
        ArchiveType.RPA -> RpaInspector(file)
        else -> throw UnsupportedOperationException("${archiveType.name} format is not yet supported for inspection")
    }

    private fun detectFormat(file: File): ArchiveType {
        val typeByExtension = detectByExtension(file.name)

        if (typeByExtension == ArchiveType.ZIP) {
            val magicBytes = readMagicBytes(file, 4)
            if (hasMatchingMagic(magicBytes, ZIP_MAGIC)) return ArchiveType.ZIP
        }

        if (typeByExtension == null) {
            detectByMagicBytes(file)?.let { return it }
        }

        return typeByExtension ?: throw IllegalArgumentException(
            "Could not detect archive format for file: ${file.name}"
        )
    }

    private fun detectByMagicBytes(file: File): ArchiveType? {
        val magicBytes = readMagicBytes(file, 8)
        return when {
            hasMatchingMagic(magicBytes, ZIP_MAGIC) -> ArchiveType.ZIP
            hasMatchingMagic(magicBytes, RAR_MAGIC) -> ArchiveType.RAR
            hasMatchingMagic(magicBytes, SEVEN_ZIP_MAGIC) -> ArchiveType.SEVEN_ZIP
            hasMatchingMagic(magicBytes, GZIP_MAGIC) -> ArchiveType.GZIP
            else -> null
        }
    }

    private fun hasMatchingMagic(bytes: ByteArray, magic: ByteArray): Boolean =
        bytes.size >= magic.size && bytes.copyOfRange(0, magic.size).contentEquals(magic)

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
            lowerName.endsWith(".gz") || lowerName.endsWith(".gzip") -> ArchiveType.GZIP
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
