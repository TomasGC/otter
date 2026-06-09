package app.otter.data.extractor

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Helper object to create test archives programmatically.
 *
 * Temporary copy for JVM unit tests (RpaArchiveCreationTest).
 * Original is in androidTest directory.
 */
object TestArchiveHelper {

    /**
     * Creates a RPA-3.0 archive (Ren'Py Archive) with multiple files.
     *
     * Pattern: test.rpa → testRpa/file.txt → "Rpa"
     *
     * RPA-3.0 format (Ren'Py visual novel engine):
     * - Header: "RPA-3.0 " + offset(hex 16 chars) + " " + key(hex 8 chars) + "\n"
     * - Padding: "Made with Ren'Py." (optional text until offset)
     * - At offset: Zlib-compressed Python binary index (safe - we create it ourselves, no code execution)
     * - File data: stored at offsets (offset/size XOR-ed with key for obfuscation)
     *
     * Security: We manually create the binary format - no Python code execution involved.
     */
    fun createRpaArchive(outputFile: File) {
        val key = 0x42424242 // Obfuscation key
        val files = listOf(
            RpaFileEntry("testRpa/file.txt", "Rpa".toByteArray()),
            RpaFileEntry("testRpa/readme.md", "# RPA Test\nThis is a test RPA archive.".toByteArray()),
            RpaFileEntry("testRpa/sub/data.bin", "Binary content".toByteArray())
        )

        // Calculate structure sizes
        val headerSize = 34 // "RPA-3.0 " + 16 hex + " " + 8 hex + "\n"
        val paddingSize = 50 // Extra padding for "Made with Ren'Py."

        // Estimate compressed index size (will be recalculated after compression)
        // This is a bootstrap: we need to know data offset to create index, but need index size to know data offset
        // Solution: create index with estimated offsets, compress, recalculate if size changed
        var estimatedIndexSize = 200 // Start with rough estimate
        var actualIndexSize = 0
        var finalCompressedIndex: ByteArray
        var fileEntries: List<RpaIndexEntry>

        // Iterate until we get correct offsets
        // Bootstrap problem: need index size to compute offsets, need offsets to create index
        var iteration = 0
        do {
            val dataStartOffset = headerSize + paddingSize + estimatedIndexSize
            var currentOffset = dataStartOffset
            val tempEntries = mutableListOf<RpaIndexEntry>()

            files.forEach { file ->
                val obfuscatedOffset = currentOffset.toLong() xor key.toLong()
                val obfuscatedSize = file.content.size.toLong() xor key.toLong()
                tempEntries.add(RpaIndexEntry(file.name, obfuscatedOffset, obfuscatedSize, file.content))
                currentOffset += file.content.size
            }

            // Create and compress index
            val binaryIndex = createSimpleBinaryIndex(tempEntries)
            finalCompressedIndex = ByteArrayOutputStream().use { baos ->
                val deflater = java.util.zip.Deflater(java.util.zip.Deflater.DEFAULT_COMPRESSION, false)
                java.util.zip.DeflaterOutputStream(baos, deflater).use { zlibOut ->
                    zlibOut.write(binaryIndex)
                }
                baos.toByteArray()
            }

            actualIndexSize = finalCompressedIndex.size
            fileEntries = tempEntries
            iteration++

            // Check if converged (index size matches estimate)
            if (actualIndexSize == estimatedIndexSize || iteration > 10) {
                break // Converged or max iterations
            }
            estimatedIndexSize = actualIndexSize
        } while (true)

        // Final index offset in file
        val indexOffset = headerSize + paddingSize

        // Write RPA file
        outputFile.outputStream().use { out ->
            // Header: "RPA-3.0 XXXXXXXXXXXXXXXX YYYYYYYY\n"
            val header = String.format(
                "RPA-3.0 %016x %08x\n",
                indexOffset,
                key
            )
            out.write(header.toByteArray(Charsets.US_ASCII))

            // Padding (optional text) - must be exactly paddingSize bytes
            val padding = "Made with Ren'Py.".padEnd(paddingSize, ' ')
            out.write(padding.toByteArray(Charsets.US_ASCII))

            // Compressed binary index
            out.write(finalCompressedIndex)

            // Write file data immediately after index (no extra padding needed)
            fileEntries.forEach { entry ->
                out.write(entry.content)
            }
        }
    }

    /**
     * Creates a minimal binary index for RPA format.
     *
     * Format: { "filename": [[offset, size]], ... }
     *
     * We use binary protocol 2 for simplicity - this is just a data format,
     * no Python code execution involved.
     */
    private fun createSimpleBinaryIndex(entries: List<RpaIndexEntry>): ByteArray {
        val baos = ByteArrayOutputStream()

        // Binary protocol 2 marker
        baos.write(0x80) // PROTO
        baos.write(0x02) // version 2

        // Start dict
        baos.write(0x7D) // EMPTY_DICT

        entries.forEach { entry ->
            // Key: filename (SHORT_BINUNICODE - UTF-8 string)
            val filenameBytes = entry.name.toByteArray(Charsets.UTF_8)
            baos.write(0x8C) // SHORT_BINUNICODE
            baos.write(filenameBytes.size) // length (1 byte)
            baos.write(filenameBytes)

            // Value: outer list (will contain one nested list)
            baos.write(0x5D) // EMPTY_LIST

            // Start building nested list [offset, size]
            baos.write(0x28) // MARK (marks start of list construction)

            // Push offset
            baos.write(0x4A) // BININT
            writeInt32LE(baos, entry.offset.toInt())

            // Push size
            baos.write(0x4A) // BININT
            writeInt32LE(baos, entry.size.toInt())

            // Create list from MARK to top of stack
            baos.write(0x6C) // LIST (pops items from MARK, creates list)

            // Append nested list to outer list
            baos.write(0x61) // APPEND

            // Add dict[key] = value
            baos.write(0x73) // SETITEM (singular - adds one key/value pair to dict)
        }

        // End
        baos.write(0x2E) // STOP

        return baos.toByteArray()
    }

    /**
     * Write int32 in little-endian format
     */
    private fun writeInt32LE(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
        out.write((value shr 16) and 0xFF)
        out.write((value shr 24) and 0xFF)
    }

    /**
     * RPA file entry with name and content
     */
    private data class RpaFileEntry(
        val name: String,
        val content: ByteArray
    )

    /**
     * RPA index entry (obfuscated offset/size)
     */
    private data class RpaIndexEntry(
        val name: String,
        val offset: Long,
        val size: Long,
        val content: ByteArray
    )
}
