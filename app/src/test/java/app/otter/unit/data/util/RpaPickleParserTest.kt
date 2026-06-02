package app.otter.data.util

import org.junit.Test
import java.io.File
import java.util.zip.InflaterInputStream

/**
 * Local test to debug RPA pickle parsing without device.
 * 
 * NOTE: This parser reads pickle BINARY FORMAT only - it does NOT execute Python code.
 * Safe for untrusted RPA archives (no code execution risk).
 */
class RpaPickleParserTest {

    @Test
    fun `parse real RPA archive locally`() {
        val archivesDir = System.getProperty("archives.dir", "../archives")
        val rpaFile = File("$archivesDir/test_archive.rpa")
        require(rpaFile.exists()) { "Test archive not found: ${rpaFile.absolutePath}" }

        println("=== Parsing RPA archive: ${rpaFile.name} (${rpaFile.length()} bytes) ===")

        // Parse RPA-3.0 header
        val header = parseRpaHeader(rpaFile)
        println("Header: indexOffset=${header.indexOffset}, key=${header.key}")

        // Read and decompress index
        val decompressedIndex = rpaFile.inputStream().use { input ->
            input.skip(header.indexOffset)
            InflaterInputStream(input).use { it.readBytes() }
        }
        println("Decompressed index: ${decompressedIndex.size} bytes")

        // Parse pickle
        val entries = RpaPickleParser.parseIndex(decompressedIndex, header.key)
        println("Extracted ${entries.size} entries")

        entries.take(10).forEach { entry ->
            println("  ${entry.name}: offset=${entry.offset}, size=${entry.size}")
        }
    }

    private fun parseRpaHeader(file: File): RpaHeader {
        file.inputStream().use { input ->
            val headerBytes = ByteArray(34)
            val bytesRead = input.read(headerBytes)
            require(bytesRead == 34) { "Invalid RPA header: file too small" }

            val headerString = String(headerBytes, Charsets.US_ASCII)
            require(headerString.startsWith("RPA-3.0 ")) { "Not an RPA-3.0 archive" }

            // Parse index offset (hex)
            val offsetHex = headerString.substring(8, 24).trim()
            val indexOffset = offsetHex.toLong(16)

            // Parse obfuscation key (hex)
            val keyHex = headerString.substring(25, 33).trim()
            val key = keyHex.toLong(16)

            return RpaHeader(indexOffset, key)
        }
    }

    private data class RpaHeader(val indexOffset: Long, val key: Long)
}
