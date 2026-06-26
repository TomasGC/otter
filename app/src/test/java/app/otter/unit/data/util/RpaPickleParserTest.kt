package app.otter.data.util

import org.junit.Assert.assertEquals
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

    @Test
    fun `parseIndex extracts entries from SETITEMS batch dict construction`() {
        // Pickle pattern used by real Ren'Py games: EMPTY_DICT + MARK + (key, [(offset, size)], ...) + SETITEMS
        // key=0 so XOR is identity
        val pickle = byteArrayOf(
            0x80.toByte(), 0x02,                          // PROTO 2
            '}'.code.toByte(),                             // EMPTY_DICT
            0x94.toByte(),                                 // MEMOIZE
            '('.code.toByte(),                             // MARK

            // Entry 1: "name1" -> [(100, 200)]
            0x8C.toByte(), 0x05,                           // SHORT_BINUNICODE length=5
            'n'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(), 'e'.code.toByte(), '1'.code.toByte(),
            ']'.code.toByte(),                             // EMPTY_LIST
            0x94.toByte(),                                 // MEMOIZE
            'J'.code.toByte(), 100, 0, 0, 0,              // BININT 100
            'J'.code.toByte(), 200.toByte(), 0, 0, 0,     // BININT 200
            0x86.toByte(),                                 // TUPLE2
            'a'.code.toByte(),                             // APPEND

            // Entry 2: "name2" -> [(1000, 150)]
            0x8C.toByte(), 0x05,                           // SHORT_BINUNICODE length=5
            'n'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(), 'e'.code.toByte(), '2'.code.toByte(),
            ']'.code.toByte(),                             // EMPTY_LIST
            0x94.toByte(),                                 // MEMOIZE
            'J'.code.toByte(), 0xE8.toByte(), 0x03, 0, 0, // BININT 1000
            'J'.code.toByte(), 0x96.toByte(), 0, 0, 0,    // BININT 150
            0x86.toByte(),                                 // TUPLE2
            'a'.code.toByte(),                             // APPEND

            'u'.code.toByte(),                             // SETITEMS
            '.'.code.toByte()                              // STOP
        )

        val entries = RpaPickleParser.parseIndex(pickle, key = 0L)

        assertEquals(2, entries.size)
        val e1 = entries.first { it.name == "name1" }
        assertEquals(100L, e1.offset)
        assertEquals(200L, e1.size)
        val e2 = entries.first { it.name == "name2" }
        assertEquals(1000L, e2.offset)
        assertEquals(150L, e2.size)
    }
}
