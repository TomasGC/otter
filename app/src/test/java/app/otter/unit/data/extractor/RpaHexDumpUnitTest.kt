package app.otter.data.extractor

import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Step 2: Hex dump analysis of RPA file structure.
 * Creates an RPA file and dumps its binary content for manual inspection.
 *
 * Note: RPA format uses Python binary protocol 2 for index (we create it safely, no code execution).
 */
class RpaHexDumpUnitTest {

    private lateinit var tempDir: File
    private lateinit var rpaFile: File

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "rpa-hexdump-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        rpaFile = File(tempDir, "test.rpa")
    }

    @After
    fun tearDown() {
        // Don't delete - we want to inspect the file
        println("\n=== RPA FILE LOCATION ===")
        println("File path: ${rpaFile.absolutePath}")
        println("File exists: ${rpaFile.exists()}")
        println("File size: ${rpaFile.length()} bytes")
        println("\nYou can inspect this file with a hex editor or:")
        println("  Format-Hex \"${rpaFile.absolutePath}\" | Select-Object -First 50")
    }

    @Test
    fun `dump RPA file structure with annotations`() {
        // Create RPA file
        TestArchiveHelper.createRpaArchive(rpaFile)

        val allBytes = rpaFile.readBytes()
        println("\n=== FULL RPA FILE HEX DUMP ===")
        println("Total size: ${allBytes.size} bytes\n")

        // Read header
        val headerLine = String(allBytes.sliceArray(0 until 34), Charsets.US_ASCII)
        println("=== HEADER (bytes 0-33) ===")
        println("Raw: $headerLine")

        val parts = headerLine.trim().split(" ")
        val indexOffset = parts[1].toLong(16)
        val key = parts[2].toInt(16)

        println("Parsed:")
        println("  Magic: ${parts[0]}")
        println("  Index offset: 0x${parts[1]} = $indexOffset decimal")
        println("  Key: 0x${parts[2]} = $key decimal")

        // Padding section
        println("\n=== PADDING (bytes 34-83) ===")
        val padding = String(allBytes.sliceArray(34 until 84), Charsets.US_ASCII)
        println("Content: \"$padding\"")

        // Index section
        println("\n=== COMPRESSED INDEX (bytes 84-${indexOffset + allBytes.size - 84 - 1}) ===")
        val indexBytes = allBytes.sliceArray(indexOffset.toInt() until minOf(indexOffset.toInt() + 100, allBytes.size))
        println("First 100 bytes of compressed index:")
        printHexDump(indexBytes, startOffset = indexOffset.toInt())

        // Decompress index
        println("\n=== DECOMPRESSED INDEX ===")
        val compressedIndex = allBytes.sliceArray(indexOffset.toInt() until allBytes.size)
        val decompressed = java.util.zip.InflaterInputStream(
            java.io.ByteArrayInputStream(compressedIndex)
        ).readBytes()

        println("Decompressed size: ${decompressed.size} bytes")
        println("Full decompressed hex:")
        printHexDump(decompressed)

        // Parse binary protocol opcodes manually
        println("\n=== BINARY PROTOCOL OPCODES ANALYSIS ===")
        parseBinaryOpcodes(decompressed, key)

        // File data section (after decompression, before actual file data)
        val estimatedIndexSize = 500
        val fileDataStart = 34 + 50 + estimatedIndexSize
        println("\n=== FILE DATA SECTION (estimated start: byte $fileDataStart) ===")
        if (fileDataStart < allBytes.size) {
            val fileDataPreview = allBytes.sliceArray(fileDataStart until minOf(fileDataStart + 100, allBytes.size))
            println("First 100 bytes of file data section:")
            printHexDump(fileDataPreview, startOffset = fileDataStart)
        }
    }

    private fun printHexDump(bytes: ByteArray, startOffset: Int = 0) {
        bytes.forEachIndexed { index, byte ->
            if (index % 16 == 0) {
                if (index > 0) println()
                print(String.format("%04X: ", startOffset + index))
            }
            print(String.format("%02X ", byte.toInt() and 0xFF))
        }
        println()

        // ASCII representation
        println("\nASCII representation (. = non-printable):")
        bytes.forEachIndexed { index, byte ->
            if (index % 64 == 0) {
                if (index > 0) println()
                print(String.format("%04X: ", startOffset + index))
            }
            val char = byte.toInt() and 0xFF
            print(if (char in 32..126) char.toChar() else '.')
        }
        println()
    }

    private fun parseBinaryOpcodes(decompressed: ByteArray, key: Int) {
        var pos = 0
        var opcodeNum = 0

        while (pos < decompressed.size) {
            val opcode = decompressed[pos].toInt() and 0xFF
            opcodeNum++

            when (opcode) {
                0x80 -> { // PROTO
                    val version = decompressed[pos + 1].toInt() and 0xFF
                    println("[$opcodeNum] PROTO version $version")
                    pos += 2
                }
                0x7D -> { // EMPTY_DICT
                    println("[$opcodeNum] EMPTY_DICT")
                    pos++
                }
                0x8C -> { // SHORT_BINUNICODE
                    val length = decompressed[pos + 1].toInt() and 0xFF
                    val strBytes = decompressed.sliceArray(pos + 2 until pos + 2 + length)
                    val str = String(strBytes, Charsets.UTF_8)
                    println("[$opcodeNum] SHORT_BINUNICODE (len=$length): \"$str\"")
                    pos += 2 + length
                }
                0x5D -> { // EMPTY_LIST
                    println("[$opcodeNum] EMPTY_LIST")
                    pos++
                }
                0x28 -> { // MARK
                    println("[$opcodeNum] MARK")
                    pos++
                }
                0x4A -> { // BININT (little-endian 4 bytes)
                    val b1 = decompressed[pos + 1].toInt() and 0xFF
                    val b2 = decompressed[pos + 2].toInt() and 0xFF
                    val b3 = decompressed[pos + 3].toInt() and 0xFF
                    val b4 = decompressed[pos + 4].toInt() and 0xFF
                    val value = b1 or (b2 shl 8) or (b3 shl 16) or (b4 shl 24)
                    val deobfuscated = value xor key
                    println("[$opcodeNum] BININT: $value (XOR $key = $deobfuscated)")
                    pos += 5
                }
                0x6C -> { // LIST
                    println("[$opcodeNum] LIST")
                    pos++
                }
                0x61 -> { // APPEND
                    println("[$opcodeNum] APPEND")
                    pos++
                }
                0x75 -> { // SETITEM
                    println("[$opcodeNum] SETITEM")
                    pos++
                }
                0x2E -> { // STOP
                    println("[$opcodeNum] STOP")
                    pos++
                    break
                }
                else -> {
                    println("[$opcodeNum] UNKNOWN OPCODE: 0x${String.format("%02X", opcode)}")
                    pos++
                }
            }
        }
    }
}
