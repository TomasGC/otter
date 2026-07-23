package app.otter.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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

    // ===== Edge cases: malformed / adversarial input =====

    @Test
    fun `parseIndex throws IllegalArgumentException on truncated pickle stream`() {
        // SHORT_BINUNICODE declares length=10 but only 2 bytes follow -> readFully hits EOF
        val truncated = byteArrayOf(
            0x80.toByte(), 0x02,           // PROTO 2
            0x8C.toByte(), 10,             // SHORT_BINUNICODE length=10
            'a'.code.toByte(), 'b'.code.toByte() // only 2 bytes present, not 10
        )

        val ex = assertThrows(IllegalArgumentException::class.java) {
            RpaPickleParser.parseIndex(truncated, key = 0L)
        }
        assertTrue(
            "Exception message should explain the failure",
            ex.message?.contains("Failed to parse RPA index") == true
        )
    }

    @Test
    fun `parseIndex ignores unrecognized opcode and continues parsing`() {
        // Unknown opcode 0xFF sits between PROTO and STOP; parser must skip it, not crash.
        val withUnknownOpcode = byteArrayOf(
            0x80.toByte(), 0x02,     // PROTO 2
            0xFF.toByte(),          // unrecognized opcode
            '}'.code.toByte(),      // EMPTY_DICT
            '.'.code.toByte()       // STOP
        )

        val entries = RpaPickleParser.parseIndex(withUnknownOpcode, key = 0L)

        assertEquals("Unknown opcode must not produce entries, but must not throw either", 0, entries.size)
    }

    @Test
    fun `parseIndex returns empty list for a pickle with no dict entries`() {
        val emptyDict = byteArrayOf(
            0x80.toByte(), 0x02,     // PROTO 2
            '}'.code.toByte(),      // EMPTY_DICT
            '.'.code.toByte()       // STOP
        )

        val entries = RpaPickleParser.parseIndex(emptyDict, key = 0L)

        assertEquals(0, entries.size)
    }

    @Test
    fun `parseIndex with wrong key silently returns incorrect offset and size instead of failing`() {
        // Single entry "file.txt" -> tuple(obfuscatedOffset=0x55, obfuscatedSize=0xAA), built via SETITEM.
        val pickle = byteArrayOf(
            0x80.toByte(), 0x02,                            // PROTO 2
            0x8C.toByte(), 0x08,                             // SHORT_BINUNICODE length=8
            'f'.code.toByte(), 'i'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            '.'.code.toByte(), 't'.code.toByte(), 'x'.code.toByte(), 't'.code.toByte(),
            'K'.code.toByte(), 0x55,                         // BININT1 0x55 (obfuscated offset)
            'K'.code.toByte(), 0xAA.toByte(),                // BININT1 0xAA (obfuscated size)
            0x86.toByte(),                                   // TUPLE2 -> [0x55, 0xAA]
            0x85.toByte(),                                   // TUPLE1 -> [[0x55, 0xAA]]
            's'.code.toByte(),                               // SETITEM
            '.'.code.toByte()                                // STOP
        )

        val correctKey = 0x1234L
        val entriesWithCorrectKey = RpaPickleParser.parseIndex(pickle, key = correctKey)
        assertEquals(1, entriesWithCorrectKey.size)
        assertEquals(0x55L xor correctKey, entriesWithCorrectKey[0].offset)
        assertEquals(0xAAL xor correctKey, entriesWithCorrectKey[0].size)

        val wrongKey = 0x9999L
        val entriesWithWrongKey = RpaPickleParser.parseIndex(pickle, key = wrongKey)
        assertEquals(
            "Parsing must still succeed with a wrong key (parser does not validate the key)",
            1, entriesWithWrongKey.size
        )
        assertNotEquals(
            "A wrong key must silently produce a different offset, not an exception",
            entriesWithCorrectKey[0].offset, entriesWithWrongKey[0].offset
        )
    }

    // ===== Fallback stack-pattern extraction (extractFromStackIfNeeded) =====
    // Real Ren'Py archives normally build entries via SETITEM/SETITEMS, but some index
    // layouts leave filename/tuple pairs directly on the stack without ever consolidating
    // them into a dict. This fallback path had zero coverage despite being the exact area
    // (RPA index parsing) that produced two real bugs in this project before (#16, #46).

    @Test
    fun `parseIndex recovers entries via stack-pattern fallback when SETITEM SETITEMS are never used`() {
        val key = 0x1234L
        val pickle = PickleBuilder()
            .proto()
            .frame(0L)
            .emptyDict()
            .shortUnicode("name1")
            .mark().binInt1(0x55).binInt1(0xAA).tuple2().list()
            .shortUnicode("name2")
            .mark().binInt1(0x10).binInt1(0x20).tuple2().list()
            .stop()
            .build()

        val entries = RpaPickleParser.parseIndex(pickle, key)

        assertEquals(2, entries.size)
        val e1 = entries.first { it.name == "name1" }
        assertEquals(0x55L xor key, e1.offset)
        assertEquals(0xAAL xor key, e1.size)
        val e2 = entries.first { it.name == "name2" }
        assertEquals(0x10L xor key, e2.offset)
        assertEquals(0x20L xor key, e2.size)
    }

    @Test
    fun `parseIndex fallback extraction skips malformed stack-pattern pairs but keeps valid ones`() {
        val pickle = PickleBuilder()
            .proto()
            .emptyDict()
            // Bad pair: filename is not a String
            .binInt1(42)
            .mark().binInt1(1).binInt1(2).tuple2().list()
            // Bad pair: dataList has more than one item (shape mismatch)
            .shortUnicode("badshape")
            .mark().binInt1(1).binInt1(2).tuple2().binInt1(3).binInt1(4).tuple2().list()
            // Bad pair: inner tuple has fewer than 2 elements
            .shortUnicode("shorttuple")
            .mark().binInt1(9).tuple1().list()
            // Good pair
            .shortUnicode("valid")
            .mark().binInt1(5).binInt1(9).tuple2().list()
            .stop()
            .build()

        val entries = RpaPickleParser.parseIndex(pickle, key = 0L)

        assertEquals(
            "Only the well-shaped pair should produce an entry",
            1, entries.size
        )
        assertEquals("valid", entries[0].name)
        assertEquals(5L, entries[0].offset)
        assertEquals(9L, entries[0].size)
    }

    @Test
    fun `parseIndex does not attempt fallback extraction when stack is too small`() {
        val pickle = PickleBuilder()
            .proto()
            .emptyDict()
            .shortUnicode("solo")
            .stop()
            .build()

        val entries = RpaPickleParser.parseIndex(pickle, key = 0L)

        assertEquals(0, entries.size)
    }

    // ===== Numeric and tuple opcodes not exercised by the SETITEMS-batch test =====

    @Test
    fun `parseIndex supports BININT2 LONG1 and LONG4 opcodes for large offset and size values`() {
        val key = 0x9999L
        val pickle = PickleBuilder()
            .proto()
            .shortUnicode("bigentry")
            .binInt2(40000)
            .long4(100000L)
            .none()
            .tuple3()
            .tuple1()
            .setitem()
            .shortUnicode("longentry")
            .long1(200L)
            .binInt(123456)
            .tuple2()
            .tuple1()
            .setitem()
            .stop()
            .build()

        val entries = RpaPickleParser.parseIndex(pickle, key)

        assertEquals(2, entries.size)
        val big = entries.first { it.name == "bigentry" }
        assertEquals(40000L xor key, big.offset)
        assertEquals(100000L xor key, big.size)
        val long = entries.first { it.name == "longentry" }
        assertEquals(200L xor key, long.offset)
        assertEquals(123456L xor key, long.size)
    }

    @Test
    fun `parseIndex supports mark-based TUPLE opcode grouping via fallback extraction`() {
        val key = 0x42L
        val pickle = PickleBuilder()
            .proto()
            .emptyDict()
            .shortUnicode("viaMarkTuple")
            .mark().binInt1(11).binInt1(22).tuple().tuple1()
            .stop()
            .build()

        val entries = RpaPickleParser.parseIndex(pickle, key)

        assertEquals(1, entries.size)
        assertEquals("viaMarkTuple", entries[0].name)
        assertEquals(11L xor key, entries[0].offset)
        assertEquals(22L xor key, entries[0].size)
    }

    @Test
    fun `parseIndex supports APPENDS batch list construction via fallback extraction`() {
        val key = 0x55L
        val pickle = PickleBuilder()
            .proto()
            .emptyDict()
            .shortUnicode("appendsName")
            .emptyList()
            .mark().binInt1(77).binInt1(88).tuple2().appends()
            .stop()
            .build()

        val entries = RpaPickleParser.parseIndex(pickle, key)

        assertEquals(1, entries.size)
        assertEquals("appendsName", entries[0].name)
        assertEquals(77L xor key, entries[0].offset)
        assertEquals(88L xor key, entries[0].size)
    }

    // ===== Security-critical: opcodes that would instantiate a class in real pickle =====
    // The class doc guarantees "no code execution, no class instantiation" for untrusted
    // archive data. STACK_GLOBAL + NEWOBJ_EX are the exact opcode pair used by classic
    // pickle deserialization exploits, so this parser must render them permanently inert.

    @Test
    fun `parseIndex safely discards STACK_GLOBAL and NEWOBJ_EX without instantiating any class`() {
        val pickle = PickleBuilder()
            .proto()
            .shortUnicode("module")
            .shortUnicode("ClassName")
            .stackGlobal()
            .none()
            .emptyTuple()
            .emptyDict()
            .newObjEx()
            .stop()
            .build()

        val entries = RpaPickleParser.parseIndex(pickle, key = 0L)

        assertEquals(
            "STACK_GLOBAL/NEWOBJ_EX must be discarded, never produce entries or crash",
            0, entries.size
        )
    }

    // ===== Set construction and stack-cleanup opcodes =====

    @Test
    fun `parseIndex handles set construction and stack-cleanup opcodes without corrupting subsequent state`() {
        val key = 0x77L
        val pickle = PickleBuilder()
            .proto()
            // ADDITEMS: mark, then the set itself, then the items to add into it
            .mark().emptySet().binInt1(1).binInt1(2).additems().pop()
            // FROZENSET: mark, then raw items (no pre-existing container needed)
            .mark().binInt1(3).binInt1(4).frozenset().pop()
            // POP_MARK: mark, push junk, discard everything back to the mark
            .mark().binInt1(9).binInt1(10).binInt1(11).popMark()
            // Prove the stack is left clean and correctly typed after all of the above
            .shortUnicode("afterChurn")
            .binInt1(50).binInt1(60).tuple2().tuple1()
            .setitem()
            .stop()
            .build()

        val entries = RpaPickleParser.parseIndex(pickle, key)

        assertEquals(1, entries.size)
        assertEquals("afterChurn", entries[0].name)
        assertEquals(50L xor key, entries[0].offset)
        assertEquals(60L xor key, entries[0].size)
    }

    // ===== String/byte opcodes for lengths SHORT_BINUNICODE cannot encode =====

    @Test
    fun `parseIndex supports BINUNICODE for filenames longer than 255 bytes`() {
        val longName = "a".repeat(300) + ".png"
        val key = 0x11L
        val pickle = PickleBuilder()
            .proto()
            .binUnicode(longName)
            .binInt1(1).binInt1(2).tuple2().tuple1()
            .setitem()
            .stop()
            .build()

        val entries = RpaPickleParser.parseIndex(pickle, key)

        assertEquals(1, entries.size)
        assertEquals(longName, entries[0].name)
        assertEquals(1L xor key, entries[0].offset)
        assertEquals(2L xor key, entries[0].size)
    }

    @Test
    fun `parseIndex safely handles pickle opcodes never produced by real RPA archives without corrupting state`() {
        val pickle = PickleBuilder()
            .proto()
            .emptyTuple().pop()
            .binUnicode8("weird").pop()
            .shortBinBytes(byteArrayOf(1, 2, 3)).pop()
            .binBytes(byteArrayOf(4, 5, 6, 7)).pop()
            .binBytes8(byteArrayOf(8, 9)).pop()
            .newTrue().pop()
            .newFalse().pop()
            // Prove the stack is still valid after all of the above
            .shortUnicode("stillWorks")
            .binInt1(3).binInt1(4).tuple2().tuple1()
            .setitem()
            .stop()
            .build()

        val entries = RpaPickleParser.parseIndex(pickle, key = 0L)

        assertEquals(1, entries.size)
        assertEquals("stillWorks", entries[0].name)
        assertEquals(3L, entries[0].offset)
        assertEquals(4L, entries[0].size)
    }

    // ===== Memoization opcodes (BINPUT/BINGET and their 4-byte-index variants) =====

    @Test
    fun `parseIndex correctly stores and retrieves values via BINPUT BINGET and their long-index variants`() {
        val key = 0x21L
        val pickle = PickleBuilder()
            .proto()
            // BINPUT/BINGET: store, remove from stack, retrieve back from memo (not from stack top)
            .shortUnicode("sharedname")
            .binPut(5)
            .pop()
            .binGet(5)
            .binInt1(7).binInt1(8).tuple2().tuple1()
            .setitem()
            // LONG_BINPUT/LONG_BINGET: same, but with a 4-byte memo index
            .shortUnicode("sharedname2")
            .longBinPut(300)
            .pop()
            .longBinGet(300)
            .binInt1(1).binInt1(2).tuple2().tuple1()
            .setitem()
            .stop()
            .build()

        val entries = RpaPickleParser.parseIndex(pickle, key)

        assertEquals(2, entries.size)
        val e1 = entries.first { it.name == "sharedname" }
        assertEquals(7L xor key, e1.offset)
        assertEquals(8L xor key, e1.size)
        val e2 = entries.first { it.name == "sharedname2" }
        assertEquals(1L xor key, e2.offset)
        assertEquals(2L xor key, e2.size)
    }

    // ===== Real-world regression: 3-element (offset, size, prefix) index tuples =====
    // Newer Ren'Py archivers encode each index value as [(offset, size, prefix)] instead of
    // [(offset, size)] — a 3-tuple whose extra element is built via the legacy Python-2
    // SHORT_BINSTRING opcode ('U'), typically an empty string memoized once via BINPUT and
    // reused for every subsequent entry via BINGET. Reproduces a real 341 MB archive that
    // parsed to zero entries because SHORT_BINSTRING had no handler.

    @Test
    fun `parseIndex extracts entries when index tuples carry a SHORT_BINSTRING prefix element`() {
        val key = 0x42424242L
        val pickle = PickleBuilder()
            .proto()
            .emptyDict()
            .binPut(0)
            .mark()
            // Entry 1: builds the shared empty-string prefix via SHORT_BINSTRING + memoizes it
            .shortUnicode("images/first.png")
            .binPut(1)
            .emptyList()
            .binPut(2)
            .long1(100L)
            .binInt(200)
            .shortBinString("")
            .binPut(3)
            .tuple3()
            .binPut(4)
            .append()
            // Entry 2: reuses the memoized empty-string prefix via BINGET instead of re-encoding it
            .shortUnicode("images/second.png")
            .binPut(5)
            .emptyList()
            .binPut(6)
            .binInt(300)
            .binInt(400)
            .binGet(3)
            .tuple3()
            .binPut(7)
            .append()
            .setitems()
            .stop()
            .build()

        val entries = RpaPickleParser.parseIndex(pickle, key)

        assertEquals(2, entries.size)
        val e1 = entries.first { it.name == "images/first.png" }
        assertEquals(100L xor key, e1.offset)
        assertEquals(200L xor key, e1.size)
        val e2 = entries.first { it.name == "images/second.png" }
        assertEquals(300L xor key, e2.offset)
        assertEquals(400L xor key, e2.size)
    }

    /**
     * Minimal builder for hand-crafted pickle binary protocol 2-4 byte sequences,
     * mirroring the opcode set implemented by [RpaPickleParser].
     */
    private class PickleBuilder {
        private val out = java.io.ByteArrayOutputStream()

        private fun op(b: Int): PickleBuilder { out.write(b); return this }
        private fun byteVal(v: Int): PickleBuilder { out.write(v and 0xFF); return this }
        private fun bytesLE(v: Long, count: Int): PickleBuilder {
            for (i in 0 until count) out.write(((v ushr (i * 8)) and 0xFFL).toInt())
            return this
        }
        private fun rawBytes(b: ByteArray): PickleBuilder { out.write(b); return this }

        fun proto(): PickleBuilder = op(0x80).byteVal(2)
        fun frame(length: Long = 0L): PickleBuilder = op(0x95).bytesLE(length, 8)
        fun emptyDict(): PickleBuilder = op('}'.code)
        fun emptyList(): PickleBuilder = op(']'.code)
        fun emptyTuple(): PickleBuilder = op(')'.code)
        fun emptySet(): PickleBuilder = op(0x8F)
        fun mark(): PickleBuilder = op('('.code)

        fun shortUnicode(s: String): PickleBuilder {
            val bytes = s.toByteArray(Charsets.UTF_8)
            return op(0x8C).byteVal(bytes.size).rawBytes(bytes)
        }
        fun binUnicode(s: String): PickleBuilder {
            val bytes = s.toByteArray(Charsets.UTF_8)
            return op('X'.code).bytesLE(bytes.size.toLong(), 4).rawBytes(bytes)
        }
        fun binUnicode8(s: String): PickleBuilder {
            val bytes = s.toByteArray(Charsets.UTF_8)
            return op(0x8D).bytesLE(bytes.size.toLong(), 8).rawBytes(bytes)
        }
        fun shortBinString(s: String): PickleBuilder {
            val bytes = s.toByteArray(Charsets.ISO_8859_1)
            return op('U'.code).byteVal(bytes.size).rawBytes(bytes)
        }
        fun shortBinBytes(b: ByteArray): PickleBuilder = op(0x43).byteVal(b.size).rawBytes(b)
        fun binBytes(b: ByteArray): PickleBuilder = op('B'.code).bytesLE(b.size.toLong(), 4).rawBytes(b)
        fun binBytes8(b: ByteArray): PickleBuilder = op(0x8E).bytesLE(b.size.toLong(), 8).rawBytes(b)

        fun binInt(v: Int): PickleBuilder = op('J'.code).bytesLE(v.toLong() and 0xFFFFFFFFL, 4)
        fun binInt1(v: Int): PickleBuilder = op('K'.code).byteVal(v)
        fun binInt2(v: Int): PickleBuilder = op('M'.code).bytesLE(v.toLong(), 2)
        fun long1(v: Long): PickleBuilder = op(0x8A).byteVal(1).bytesLE(v, 1)
        fun long4(v: Long, numBytes: Int = 4): PickleBuilder =
            op(0x8B).bytesLE(numBytes.toLong(), 4).bytesLE(v, numBytes)

        fun tuple1(): PickleBuilder = op(0x85)
        fun tuple2(): PickleBuilder = op(0x86)
        fun tuple3(): PickleBuilder = op(0x87)
        fun tuple(): PickleBuilder = op('t'.code)
        fun list(): PickleBuilder = op('l'.code)
        fun append(): PickleBuilder = op('a'.code)
        fun appends(): PickleBuilder = op('e'.code)
        fun setitem(): PickleBuilder = op('s'.code)
        fun setitems(): PickleBuilder = op('u'.code)
        fun additems(): PickleBuilder = op(0x90)
        fun binPut(i: Int): PickleBuilder = op('q'.code).byteVal(i)
        fun longBinPut(i: Int): PickleBuilder = op('r'.code).bytesLE(i.toLong(), 4)
        fun binGet(i: Int): PickleBuilder = op('h'.code).byteVal(i)
        fun longBinGet(i: Int): PickleBuilder = op('j'.code).bytesLE(i.toLong(), 4)
        fun stackGlobal(): PickleBuilder = op(0x93)
        fun newObjEx(): PickleBuilder = op(0x92)
        fun frozenset(): PickleBuilder = op(0x91)
        fun pop(): PickleBuilder = op(0x30)
        fun popMark(): PickleBuilder = op('1'.code)
        fun none(): PickleBuilder = op('N'.code)
        fun newTrue(): PickleBuilder = op(0x88)
        fun newFalse(): PickleBuilder = op(0x89)
        fun stop(): PickleBuilder = op('.'.code)

        fun build(): ByteArray = out.toByteArray()
    }
}
