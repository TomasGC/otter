package app.otter.data.extractor

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.util.zip.InflaterInputStream

/**
 * Unit test to validate RPA archive creation and binary index parsing.
 * This test runs on JVM (not Android) so logs are visible in CI.
 *
 * Note: RPA format uses Python's binary protocol 2 for its index structure.
 * We create this format ourselves (no code execution), it's safe for testing.
 */
class RpaArchiveCreationTest {

    private lateinit var tempDir: File
    private lateinit var rpaFile: File

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "rpa-test-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        rpaFile = File(tempDir, "test.rpa")
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `createRpaArchive creates valid file`() {
        // Arrange & Act
        TestArchiveHelper.createRpaArchive(rpaFile)

        // Assert
        println("RPA file created: ${rpaFile.absolutePath}")
        println("File size: ${rpaFile.length()} bytes")
        assertTrue("RPA file should exist", rpaFile.exists())
        assertTrue("RPA file should not be empty", rpaFile.length() > 0)
    }

    @Test
    fun `RPA file has correct header format`() {
        // Arrange
        TestArchiveHelper.createRpaArchive(rpaFile)

        // Act - Read header
        val header = rpaFile.readText(Charsets.US_ASCII).take(100)
        println("RPA header (first 100 chars): $header")

        // Assert
        assertTrue("Header should start with RPA-3.0", header.startsWith("RPA-3.0 "))

        // Parse header: RPA-3.0 XXXXXXXXXXXXXXXX YYYYYYYY\n
        val parts = header.lines()[0].split(" ")
        assertEquals("Header should have 3 parts", 3, parts.size)
        assertEquals("First part should be RPA-3.0", "RPA-3.0", parts[0])

        val indexOffset = parts[1].toLong(16)
        val key = parts[2].toInt(16)

        println("Index offset (hex): ${parts[1]} = $indexOffset (decimal)")
        println("Key (hex): ${parts[2]} = $key (decimal)")

        assertTrue("Index offset should be > 0", indexOffset > 0)
        assertTrue("Key should be > 0", key > 0)
    }

    @Test
    fun `binary index can be extracted and decompressed`() {
        // Arrange
        TestArchiveHelper.createRpaArchive(rpaFile)

        // Act - Parse header to get index offset
        val headerLine = rpaFile.bufferedReader(Charsets.US_ASCII).readLine()
        val parts = headerLine.split(" ")
        val indexOffset = parts[1].toLong(16)
        val key = parts[2].toInt(16)

        println("Extracting binary index at offset $indexOffset with key $key")

        // Read compressed index
        val allBytes = rpaFile.readBytes()
        val indexBytes = allBytes.sliceArray(indexOffset.toInt() until allBytes.size)
        println("Compressed index size: ${indexBytes.size} bytes")
        println("First 20 bytes (hex): ${indexBytes.take(20).joinToString(" ") { "%02x".format(it) }}")

        // Check zlib header (0x78 0x9C for default compression)
        assertEquals("First byte should be 0x78 (zlib header)", 0x78.toByte(), indexBytes[0])
        assertTrue("Second byte should be compression method", indexBytes[1].toInt() and 0xFF in 0x01..0xDA)

        // Decompress with zlib (InflaterInputStream handles zlib format with header)
        val decompressed = try {
            InflaterInputStream(ByteArrayInputStream(indexBytes)).readBytes()
        } catch (e: Exception) {
            println("ERROR decompressing: ${e.message}")
            e.printStackTrace()
            throw e
        }

        println("Decompressed index size: ${decompressed.size} bytes")
        println("First 50 bytes (hex): ${decompressed.take(50).joinToString(" ") { "%02x".format(it) }}")

        assertNotNull("Decompressed data should not be null", decompressed)
        assertTrue("Decompressed data should not be empty", decompressed.isNotEmpty())

        // Check binary protocol 2 marker
        assertEquals("First byte should be PROTO (0x80)", 0x80.toByte(), decompressed[0])
        assertEquals("Second byte should be version 2", 0x02.toByte(), decompressed[1])
    }

    @Test
    fun `binary index contains expected file entries`() {
        // Arrange
        TestArchiveHelper.createRpaArchive(rpaFile)

        // Act - Parse header
        val headerLine = rpaFile.bufferedReader(Charsets.US_ASCII).readLine()
        val parts = headerLine.split(" ")
        val indexOffset = parts[1].toLong(16)
        val key = parts[2].toInt(16)

        // Extract and decompress index
        val allBytes = rpaFile.readBytes()
        val indexBytes = allBytes.sliceArray(indexOffset.toInt() until allBytes.size)
        val decompressed = InflaterInputStream(ByteArrayInputStream(indexBytes)).readBytes()

        println("\n=== Parsing binary index ===")
        println("Decompressed size: ${decompressed.size} bytes")

        // Parse binary format manually to debug
        val input = DataInputStream(ByteArrayInputStream(decompressed))

        // Skip PROTO + version
        val proto = input.readByte()
        val version = input.readByte()
        println("PROTO: 0x${"%02x".format(proto)}, version: $version")

        // Read all opcodes
        var opcodeCount = 0
        val opcodes = mutableListOf<Pair<Int, String>>()

        while (input.available() > 0) {
            val opcode = input.readUnsignedByte()
            opcodeCount++

            val opcodeName = when (opcode) {
                0x7D -> "EMPTY_DICT"
                0x8C -> "SHORT_BINUNICODE"
                0x5D -> "EMPTY_LIST"
                0x28 -> "MARK"
                0x4A -> "BININT"
                0x6C -> "LIST"
                0x61 -> "APPEND"
                0x73 -> "SETITEM"     // 's' - adds one key/value pair to dict
                0x75 -> "SETITEMS"    // 'u' - adds multiple key/value pairs from MARK
                0x2E -> "STOP"
                else -> "UNKNOWN_0x${"%02x".format(opcode)}"
            }

            opcodes.add(opcode to opcodeName)

            // Read opcode data
            when (opcode) {
                0x8C -> { // SHORT_BINUNICODE
                    val length = input.readUnsignedByte()
                    val bytes = ByteArray(length)
                    input.readFully(bytes)
                    val str = String(bytes, Charsets.UTF_8)
                    println("  [$opcodeCount] $opcodeName: \"$str\"")
                }
                0x4A -> { // BININT (little-endian 4 bytes)
                    val b1 = input.readUnsignedByte()
                    val b2 = input.readUnsignedByte()
                    val b3 = input.readUnsignedByte()
                    val b4 = input.readUnsignedByte()
                    val value = b1 or (b2 shl 8) or (b3 shl 16) or (b4 shl 24)
                    println("  [$opcodeCount] $opcodeName: $value")
                }
                0x2E -> { // STOP
                    println("  [$opcodeCount] $opcodeName")
                    break
                }
                else -> {
                    println("  [$opcodeCount] $opcodeName")
                }
            }
        }

        println("\nTotal opcodes: $opcodeCount")
        println("Opcode sequence: ${opcodes.map { it.second }.joinToString(" -> ")}")

        // Count expected patterns
        val emptyDictCount = opcodes.count { it.second == "EMPTY_DICT" }
        val shortBinUnicodeCount = opcodes.count { it.second == "SHORT_BINUNICODE" }
        val emptyListCount = opcodes.count { it.second == "EMPTY_LIST" }
        val markCount = opcodes.count { it.second == "MARK" }
        val listCount = opcodes.count { it.second == "LIST" }
        val appendCount = opcodes.count { it.second == "APPEND" }
        val setItemCount = opcodes.count { it.second == "SETITEM" }

        println("\n=== Opcode counts ===")
        println("EMPTY_DICT: $emptyDictCount (expected: 1)")
        println("SHORT_BINUNICODE: $shortBinUnicodeCount (expected: 3 filenames)")
        println("EMPTY_LIST: $emptyListCount (expected: 3)")
        println("MARK: $markCount (expected: 3)")
        println("LIST: $listCount (expected: 3)")
        println("APPEND: $appendCount (expected: 3)")
        println("SETITEM: $setItemCount (expected: 3)")

        // Assert expected structure
        assertEquals("Should have 1 EMPTY_DICT", 1, emptyDictCount)
        assertEquals("Should have 3 SHORT_BINUNICODE (filenames)", 3, shortBinUnicodeCount)
        assertEquals("Should have 3 EMPTY_LIST", 3, emptyListCount)
        assertEquals("Should have 3 MARK", 3, markCount)
        assertEquals("Should have 3 LIST", 3, listCount)
        assertEquals("Should have 3 APPEND", 3, appendCount)
        assertEquals("Should have 3 SETITEM", 3, setItemCount)
    }
}
