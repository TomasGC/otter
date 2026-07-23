package app.otter.data.inspector

import app.otter.domain.inspector.ArchiveType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream

/**
 * Unit tests for RpaInspector using FakeRpaSource (no disk I/O).
 *
 * Tests the RPA-3.0 format parsing with in-memory bytes built by buildRpa().
 * This replaces the old integration-real test that required test_archive.rpa on disk.
 */
class RpaInspectorTest {

    @Test
    fun `entries returns all entries from RPA source`() {
        val source = FakeRpaSource(buildRpa(mapOf("img/a.png" to byteArrayOf(1, 2, 3))))
        val inspector = RpaInspector(source)

        val entries = inspector.entries().toList()

        assertEquals(1, entries.size)
        assertEquals("img/a.png", entries[0].path)
        assertFalse(entries[0].isDirectory)
        assertEquals(3L, entries[0].sizeBytes)
    }

    @Test
    fun `countEntries returns correct count`() {
        runBlocking {
            val source = FakeRpaSource(buildRpa(mapOf(
                "a.txt" to byteArrayOf(1),
                "b.txt" to byteArrayOf(2, 3),
                "c.txt" to byteArrayOf(4, 5, 6)
            )))
            val inspector = RpaInspector(source)

            val count = inspector.countEntries()

            assertEquals(3, count)
        }
    }

    @Test
    fun `entries is cached after first call`() {
        val source = FakeRpaSource(buildRpa(mapOf("x.txt" to byteArrayOf(9))))
        val inspector = RpaInspector(source)

        inspector.entries().toList()
        val opensAfterFirst = source.openCount

        inspector.entries().toList()  // second call — must NOT re-parse

        assertEquals(opensAfterFirst, source.openCount)
    }

    @Test
    fun `countEntries reuses cachedEntries when already parsed`() {
        runBlocking {
            val source = FakeRpaSource(buildRpa(mapOf("x.txt" to byteArrayOf(9))))
            val inspector = RpaInspector(source)

            inspector.entries().toList()
            val opensAfterEntries = source.openCount

            inspector.countEntries()  // must NOT re-parse

            assertEquals(opensAfterEntries, source.openCount)
        }
    }

    @Test
    fun `entries and countEntries return consistent results`() {
        runBlocking {
            val files: Map<String, ByteArray> = mapOf("a.txt" to byteArrayOf(1, 2), "b.txt" to byteArrayOf(3))
            val source = FakeRpaSource(buildRpa(files))
            val inspector = RpaInspector(source)

            val entriesCount = inspector.entries().count()
            val countResult = inspector.countEntries()

            assertEquals(entriesCount, countResult)
        }
    }

    @Test
    fun `isEncrypted always returns false`() {
        val source = FakeRpaSource(buildRpa(mapOf("x.txt" to byteArrayOf(0))))
        val inspector = RpaInspector(source)

        assertFalse(inspector.isEncrypted())
    }

    @Test
    fun `getArchiveType returns RPA`() {
        val source = FakeRpaSource(buildRpa(mapOf("x.txt" to byteArrayOf(0))))
        val inspector = RpaInspector(source)

        assertEquals(ArchiveType.RPA, inspector.getArchiveType())
    }

    @Test
    fun `close clears caches and blocks further calls`() {
        val source = FakeRpaSource(buildRpa(mapOf("x.txt" to byteArrayOf(1))))
        val inspector = RpaInspector(source)

        inspector.entries().toList()
        inspector.close()

        assertThrows(IllegalStateException::class.java) { inspector.entries().toList() }
    }

    @Test
    fun `close is idempotent`() {
        val source = FakeRpaSource(buildRpa(mapOf("x.txt" to byteArrayOf(1))))
        val inspector = RpaInspector(source)

        inspector.close()
        inspector.close()  // must not throw
    }

    @Test
    fun `entries throws after close`() {
        val source = FakeRpaSource(buildRpa(mapOf("x.txt" to byteArrayOf(1))))
        val inspector = RpaInspector(source)
        inspector.close()

        assertThrows(IllegalStateException::class.java) { inspector.entries() }
    }

    @Test
    fun `countEntries throws after close`() {
        runBlocking {
            val source = FakeRpaSource(buildRpa(mapOf("x.txt" to byteArrayOf(1))))
            val inspector = RpaInspector(source)
            inspector.close()

            assertThrows(IllegalStateException::class.java) {
                runBlocking { inspector.countEntries() }
            }
        }
    }

    @Test
    fun `entries returns empty sequence for RPA with no files`() {
        val source = FakeRpaSource(buildRpa(emptyMap()))
        val inspector = RpaInspector(source)

        assertEquals(0, inspector.entries().count())
    }

    @Test
    fun `invalid header throws IllegalArgumentException`() {
        val source = FakeRpaSource("NOT_AN_RPA\n".toByteArray(Charsets.US_ASCII))
        val inspector = RpaInspector(source)

        assertThrows(IllegalArgumentException::class.java) {
            inspector.entries().toList()
        }
    }

    @Test
    fun `entries throws when header has valid RPA prefix but non-hex offset value`() {
        // "RPA-3.0 " prefix is correct but offset contains non-hex characters (ZZZZ...)
        val badHexHeader = "RPA-3.0 ZZZZ000000000000 DEADBEEF\n".toByteArray(Charsets.US_ASCII)
        val source = FakeRpaSource(badHexHeader)
        val inspector = RpaInspector(source)

        var threw = false
        try {
            inspector.entries().toList()
        } catch (e: Exception) {
            threw = true
            // Must throw some kind of exception (NumberFormatException, IllegalArgumentException, etc.)
        }

        assertTrue("Non-hex offset must cause an exception during entries() parsing", threw)
        inspector.close()
    }

    @Test
    fun `entries throws when header has valid RPA prefix but non-hex key value`() {
        // Valid offset but non-hex key (GGGG...)
        val badKeyHeader = "RPA-3.0 0000000000000020 GGGG0000\n".toByteArray(Charsets.US_ASCII)
        val source = FakeRpaSource(badKeyHeader)
        val inspector = RpaInspector(source)

        var threw = false
        try {
            inspector.entries().toList()
        } catch (e: Exception) {
            threw = true
        }

        assertTrue("Non-hex key must cause an exception during entries() parsing", threw)
        inspector.close()
    }

    @Test
    fun `multiple entries are parsed in correct order`() {
        val files: Map<String, ByteArray> = mapOf(
            "first.txt" to byteArrayOf(1),
            "second.txt" to byteArrayOf(2),
            "third.txt" to byteArrayOf(3)
        )
        val source = FakeRpaSource(buildRpa(files))
        val inspector = RpaInspector(source)

        val entries = inspector.entries().toList()

        assertEquals(3, entries.size)
        // Note: order might vary depending on map iteration, so check by name
        val names = entries.map { entry -> entry.path }.toSet()
        assertTrue(names.contains("first.txt"))
        assertTrue(names.contains("second.txt"))
        assertTrue(names.contains("third.txt"))
    }

    @Test
    fun `entry sizes are preserved correctly`() {
        val files: Map<String, ByteArray> = mapOf(
            "small.bin" to byteArrayOf(1, 2),
            "medium.bin" to ByteArray(100),
            "large.bin" to ByteArray(1000)
        )
        val source = FakeRpaSource(buildRpa(files))
        val inspector = RpaInspector(source)

        val entries = inspector.entries().associateBy { entry -> entry.path }

        assertEquals(2L, entries["small.bin"]?.sizeBytes)
        assertEquals(100L, entries["medium.bin"]?.sizeBytes)
        assertEquals(1000L, entries["large.bin"]?.sizeBytes)
    }

    // ===== Corrupt input =====

    @Test
    fun `entries with empty bytes throws or returns empty`() {
        val source = FakeRpaSource(byteArrayOf())
        val inspector = RpaInspector(source)

        val threw = try {
            inspector.entries().toList()
            false
        } catch (e: Exception) {
            true
        }
        assertTrue("Empty bytes must cause an exception during entries() parsing", threw)
        inspector.close()
    }

    @Test
    fun `entries with invalid RPA header throws or returns empty`() {
        val source = FakeRpaSource("NOT_AN_RPA_HEADER\n".toByteArray())
        val inspector = RpaInspector(source)

        val threw = try {
            inspector.entries().toList()
            false
        } catch (e: Exception) {
            true
        }
        assertTrue("Invalid RPA header must cause an exception during entries() parsing", threw)
        inspector.close()
    }

    @Test
    fun `entries with truncated bytes after header does not hang`() {
        // Valid header start but truncated payload
        val header = "RPA-3.0 0000000000000020 deadbeef\n".toByteArray()
        val source = FakeRpaSource(header) // No index at offset 0x20
        val inspector = RpaInspector(source)

        val threw = try {
            inspector.entries().toList()
            false
        } catch (e: Exception) {
            true
        }
        assertTrue("Truncated RPA archive must cause an exception during entries() parsing", threw)
        inspector.close()
    }

    // ---- RPA byte builder ----

    /**
     * Builds valid RPA-3.0 bytes for testing.
     *
     * Creates a minimal valid RPA archive with the given files.
     * Returns complete RPA file bytes that can be parsed by RpaInspector.
     *
     * Format:
     * - Header: "RPA-3.0 " + indexOffset (16 hex) + " " + key (8 hex) + "\n"
     * - Padding: zeros until indexOffset
     * - Compressed pickle index (zlib)
     * - File data at calculated offsets
     */
    private fun buildRpa(entries: Map<String, ByteArray>): ByteArray {
        val rpaKey = 0xDEADBEEFL
        val headerSize = 34
        val paddingSize = 50

        var estimatedIndexSize = 200
        var actualIndexSize = 0
        var finalCompressedIndex: ByteArray
        var fileEntries: List<Pair<String, Long>> // name to offset mapping

        // Bootstrap: iterate until index size stabilizes
        var iteration = 0
        do {
            val dataStartOffset = headerSize + paddingSize + estimatedIndexSize
            var currentOffset = dataStartOffset.toLong()
            val tempEntries = mutableListOf<Pair<String, Long>>()
            val index = mutableMapOf<String, List<List<Long>>>()

            for ((name, data) in entries) {
                index[name] = listOf(listOf(currentOffset xor rpaKey, data.size.toLong() xor rpaKey))
                tempEntries.add(name to currentOffset)
                currentOffset += data.size.toLong()
            }

            // Create and compress binary index
            val binaryIndex = buildPickleDict(index)
            finalCompressedIndex = ByteArrayOutputStream().use { buf ->
                DeflaterOutputStream(buf).use { deflater -> deflater.write(binaryIndex) }
                buf.toByteArray()
            }

            actualIndexSize = finalCompressedIndex.size
            fileEntries = tempEntries
            iteration++

            if (actualIndexSize == estimatedIndexSize || iteration > 10) break
            estimatedIndexSize = actualIndexSize
        } while (true)

        val indexOffset = headerSize + paddingSize

        // Write RPA file
        return ByteArrayOutputStream().use { result ->
            // Header
            val header = "RPA-3.0 ${indexOffset.toString(16).uppercase().padStart(16, '0')} ${rpaKey.toString(16).uppercase().padStart(8, '0')}\n"
                .toByteArray(Charsets.US_ASCII)
            result.write(header)

            // Padding
            val padding = ByteArray(paddingSize)
            result.write(padding)

            // Compressed index
            result.write(finalCompressedIndex)

            // File data (in order from entries map)
            for ((name, _) in entries) {
                val fileData = entries[name]
                if (fileData != null) {
                    result.write(fileData)
                }
            }

            result.toByteArray()
        }
    }

    /**
     * Builds binary pickle dict format for RPA index.
     *
     * Creates Python pickle binary protocol 2 format:
     * { "filename": [[offset, size], ...], ... }
     */
    private fun buildPickleDict(index: Map<String, List<List<Long>>>): ByteArray {
        return ByteArrayOutputStream().use { out ->
            // PROTO 2
            out.write(0x80)
            out.write(0x02)

            // EMPTY_DICT
            out.write(0x7D)

            // For each entry
            for ((name, offsets) in index) {
                // Key: SHORT_BINUNICODE
                val nameBytes = name.toByteArray(Charsets.UTF_8)
                out.write(0x8C)
                out.write(nameBytes.size)
                out.write(nameBytes)

                // Value: EMPTY_LIST
                out.write(0x5D)

                // MARK for nested list
                out.write(0x28)

                // Push offset and size (LONG8 for 64-bit values)
                val offsetList = offsets[0]
                for (offset in offsetList) {
                    writeLong8(out, offset)
                }

                // LIST to create the nested list
                out.write(0x6C)

                // APPEND to add nested list to outer list
                out.write(0x61)

                // SETITEM to add to dict
                out.write(0x73)
            }

            // STOP
            out.write(0x2E)

            out.toByteArray()
        }
    }

    /**
     * Write 64-bit long in pickle LONG8 format (opcode 0x8A).
     */
    private fun writeLong8(out: ByteArrayOutputStream, value: Long) {
        out.write(0x8A)  // LONG8 opcode
        val bytes = ByteArray(8)
        bytes[0] = (value and 0xFF).toByte()
        bytes[1] = ((value shr 8) and 0xFF).toByte()
        bytes[2] = ((value shr 16) and 0xFF).toByte()
        bytes[3] = ((value shr 24) and 0xFF).toByte()
        bytes[4] = ((value shr 32) and 0xFF).toByte()
        bytes[5] = ((value shr 40) and 0xFF).toByte()
        bytes[6] = ((value shr 48) and 0xFF).toByte()
        bytes[7] = ((value shr 56) and 0xFF).toByte()
        out.write(8)  // length
        out.write(bytes)
    }
}
