package app.otter.data.inspector

import app.otter.data.util.RpaPickleParser
import app.otter.domain.inspector.ArchiveEntry
import app.otter.domain.inspector.ArchiveInspector
import app.otter.domain.inspector.ArchiveType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.zip.InflaterInputStream

/**
 * RPA (Ren'Py Archive) inspector for RPA-3.0 format.
 *
 * Format specification:
 * - Header: "RPA-3.0 " (8 bytes)
 * - Index offset: 16 hex digits (16 bytes)
 * - Space: 1 byte
 * - Obfuscation key: 8 hex digits (8 bytes)
 * - Newline: 1 byte
 * - At offset: Zlib-compressed Python pickle index
 *
 * Security note: We parse the pickle binary format manually without executing Python code.
 * This is safe for untrusted RPA archives - no arbitrary code execution risk.
 */
class RpaInspector(private val rpaFile: File) : ArchiveInspector {

    private var closed = false
    private var cachedEntries: List<ArchiveEntry>? = null
    private var cachedRawEntries: List<RpaPickleParser.RpaFileEntry>? = null
    private var cachedCount: Int? = null

    /**
     * Returns a sequence of archive entries by parsing the RPA index.
     */
    override fun entries(): Sequence<ArchiveEntry> {
        checkNotClosed()

        // Parse once and cache
        if (cachedEntries == null) {
            cachedEntries = parseRpaEntries()
        }

        return cachedEntries!!.asSequence()
    }

    /**
     * Returns the total number of entries in O(1) time after first parse.
     */
    override suspend fun countEntries(): Int = withContext(Dispatchers.IO) {
        checkNotClosed()

        // Parse once and cache
        if (cachedCount == null) {
            if (cachedEntries == null) {
                cachedEntries = parseRpaEntries()
            }
            cachedCount = cachedEntries!!.size
        }

        cachedCount!!
    }

    /**
     * RPA archives are not encrypted (just XOR obfuscation of offsets).
     */
    override fun isEncrypted(): Boolean = false

    /**
     * Returns the archive format type.
     */
    override fun getArchiveType(): ArchiveType = ArchiveType.RPA

    /**
     * Closes the inspector and releases resources.
     */
    override fun close() {
        if (!closed) {
            cachedEntries = null
            cachedRawEntries = null
            cachedCount = null
            closed = true
        }
    }

    /**
     * Internal API for RpaExtractor to access raw file entries with offsets.
     * This avoids duplicating the pickle parsing logic.
     */
    internal fun getRawFileEntries(): List<RpaPickleParser.RpaFileEntry> {
        checkNotClosed()

        // Parse once and cache
        if (cachedRawEntries == null) {
            val header = parseRpaHeader()
            cachedRawEntries = readRpaIndex(header)
        }

        return cachedRawEntries!!
    }

    private fun checkNotClosed() {
        if (closed) {
            throw IllegalStateException("RpaInspector is closed")
        }
    }

    /**
     * Parse RPA index and return list of entries.
     */
    private fun parseRpaEntries(): List<ArchiveEntry> {
        try {
            // Use getRawFileEntries to share caching logic
            val rawEntries = getRawFileEntries()
            Timber.d("RpaInspector: Total files in archive: ${rawEntries.size}")

            // Convert to ArchiveEntry list
            return rawEntries.map { rpaEntry ->
                ArchiveEntry(
                    path = rpaEntry.name,
                    isDirectory = false, // RPA only stores files, not directories
                    sizeBytes = rpaEntry.size,
                    compressedSize = rpaEntry.size, // RPA files are not compressed individually
                    lastModified = 0L // RPA format doesn't store timestamps
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "RpaInspector: Failed to parse RPA index")
            throw IllegalArgumentException("Failed to parse RPA archive: ${e.message}", e)
        }
    }

    /**
     * Parse RPA-3.0 header format:
     * RPA-3.0 XXXXXXXXXXXXXXXX YYYYYYYY\n
     */
    private fun parseRpaHeader(): RpaHeader {
        rpaFile.inputStream().use { input ->
            val headerBytes = ByteArray(HEADER_SIZE)
            val bytesRead = input.read(headerBytes)
            if (bytesRead < HEADER_SIZE) {
                throw IllegalArgumentException("Invalid RPA header: file too small")
            }

            val headerString = String(headerBytes, Charsets.US_ASCII)

            // Check magic "RPA-3.0 "
            if (!headerString.startsWith(MAGIC_RPA3)) {
                throw IllegalArgumentException("Not an RPA-3.0 archive")
            }

            // Parse index offset (hex)
            val offsetHex = headerString.substring(8, 24).trim()
            val indexOffset = offsetHex.toLong(16)

            // Parse obfuscation key (hex)
            val keyHex = headerString.substring(25, 33).trim()
            val key = keyHex.toLong(16)

            return RpaHeader(indexOffset, key)
        }
    }

    /**
     * Read and decompress the RPA index at the specified offset.
     */
    private fun readRpaIndex(header: RpaHeader): List<RpaPickleParser.RpaFileEntry> {
        rpaFile.inputStream().use { input ->
            // Seek to index offset
            input.skip(header.indexOffset)

            // Decompress with Zlib
            val decompressed = InflaterInputStream(input).use { it.readBytes() }
            Timber.d("RpaInspector: Decompressed index size: ${decompressed.size} bytes")

            // Parse Python pickle format using shared parser
            return RpaPickleParser.parseIndex(decompressed, header.key)
        }
    }

    // Data classes
    private data class RpaHeader(val indexOffset: Long, val key: Long)

    // Constants
    companion object {
        private const val MAGIC_RPA3 = "RPA-3.0 "
        private const val HEADER_SIZE = 34
    }
}
