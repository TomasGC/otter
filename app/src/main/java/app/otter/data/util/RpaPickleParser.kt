package app.otter.data.util

import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.DataInputStream

/**
 * Shared Python pickle parser for RPA-3.0 archives.
 *
 * Parses the binary pickle protocol (protocols 2-4) used in RPA index files.
 *
 * SECURITY: This parser reads pickle binary format manually WITHOUT executing Python code.
 * It only extracts primitive types (strings, integers) from the serialized structure.
 * No code execution, no imports, no class instantiation - completely safe for untrusted data.
 */
object RpaPickleParser {

    private const val TAG = "RpaPickleParser"

    // Python pickle opcodes (binary protocol 2-4)
    private const val PICKLE_PROTO = 0x80
    private const val PICKLE_FRAME = 0x95
    private const val PICKLE_EMPTY_DICT = '}'.code
    private const val PICKLE_EMPTY_LIST = ']'.code
    private const val PICKLE_EMPTY_TUPLE = ')'.code
    private const val PICKLE_EMPTY_SET = 0x8F
    private const val PICKLE_SHORT_BINUNICODE = 0x8C
    private const val PICKLE_BINUNICODE = 'X'.code
    private const val PICKLE_BINUNICODE8 = 0x8D
    private const val PICKLE_SHORT_BINBYTES = 0x43
    private const val PICKLE_BINBYTES = 'B'.code
    private const val PICKLE_BINBYTES8 = 0x8E
    private const val PICKLE_BININT = 'J'.code
    private const val PICKLE_BININT1 = 'K'.code
    private const val PICKLE_BININT2 = 'M'.code
    private const val PICKLE_LONG1 = 0x8A
    private const val PICKLE_LONG4 = 0x8B
    private const val PICKLE_TUPLE1 = 0x85
    private const val PICKLE_TUPLE2 = 0x86
    private const val PICKLE_TUPLE3 = 0x87
    private const val PICKLE_TUPLE = 't'.code
    private const val PICKLE_LIST = 'l'.code
    private const val PICKLE_APPEND = 'a'.code
    private const val PICKLE_APPENDS = 'e'.code
    private const val PICKLE_SETITEM = 's'.code
    private const val PICKLE_SETITEMS = 'u'.code
    private const val PICKLE_ADDITEMS = 0x90
    private const val PICKLE_MEMOIZE = 0x94
    private const val PICKLE_BINPUT = 'q'.code
    private const val PICKLE_LONG_BINPUT = 'r'.code
    private const val PICKLE_BINGET = 'h'.code
    private const val PICKLE_LONG_BINGET = 'j'.code
    private const val PICKLE_MARK = '('.code
    private const val PICKLE_STACK_GLOBAL = 0x93
    private const val PICKLE_NEWOBJ_EX = 0x92
    private const val PICKLE_FROZENSET = 0x91
    private const val PICKLE_POP = 0x30
    private const val PICKLE_POP_MARK = '1'.code
    private const val PICKLE_NONE = 'N'.code
    private const val PICKLE_NEWTRUE = 0x88
    private const val PICKLE_NEWFALSE = 0x89
    private const val PICKLE_STOP = '.'.code

    /**
     * Parse pickle-serialized RPA index and extract file entries.
     *
     * @param data Pickle binary data (already decompressed)
     * @param key XOR obfuscation key for offset/size values
     * @return List of file entries with deobfuscated offsets and sizes
     */
    fun parseIndex(data: ByteArray, key: Long): List<RpaFileEntry> = try {
        PickleParserState(data, key).parse()
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Failed to parse pickle index")
        throw IllegalArgumentException("Failed to parse RPA index: ${e.message}", e)
    }

    private class PickleParserState(data: ByteArray, private val key: Long) {
        val entries = mutableListOf<RpaFileEntry>()
        val stack = mutableListOf<Any?>()
        val memo = mutableMapOf<Int, Any?>()
        var markIndex = -1
        var stop = false
        val input = DataInputStream(ByteArrayInputStream(data))

        private val opcodeHandlers: Map<Int, () -> Unit> by lazy {
            mapOf(
                PICKLE_PROTO to ::handleProto,
                PICKLE_FRAME to ::handleFrame,
                PICKLE_EMPTY_DICT to ::handleEmptyDict,
                PICKLE_EMPTY_LIST to ::handleEmptyList,
                PICKLE_EMPTY_TUPLE to ::handleEmptyTuple,
                PICKLE_EMPTY_SET to ::handleEmptySet,
                PICKLE_SHORT_BINUNICODE to ::handleShortBinUnicode,
                PICKLE_BINUNICODE to ::handleBinUnicode,
                PICKLE_BINUNICODE8 to ::handleBinUnicode8,
                PICKLE_SHORT_BINBYTES to ::handleShortBinBytes,
                PICKLE_BINBYTES to ::handleBinBytes,
                PICKLE_BINBYTES8 to ::handleBinBytes8,
                PICKLE_BININT to ::handleBinInt,
                PICKLE_BININT1 to ::handleBinInt1,
                PICKLE_BININT2 to ::handleBinInt2,
                PICKLE_LONG1 to ::handleLong1,
                PICKLE_LONG4 to ::handleLong4,
                PICKLE_TUPLE1 to ::handleTuple1,
                PICKLE_TUPLE2 to ::handleTuple2,
                PICKLE_TUPLE3 to ::handleTuple3,
                PICKLE_TUPLE to ::handleTuple,
                PICKLE_LIST to ::handleList,
                PICKLE_APPEND to ::handleAppend,
                PICKLE_APPENDS to ::handleAppends,
                PICKLE_SETITEM to ::handleSetitem,
                PICKLE_SETITEMS to ::handleSetitems,
                PICKLE_ADDITEMS to ::handleAdditems,
                PICKLE_MEMOIZE to ::handleMemoize,
                PICKLE_BINPUT to ::handleBinPut,
                PICKLE_LONG_BINPUT to ::handleLongBinPut,
                PICKLE_BINGET to ::handleBinGet,
                PICKLE_LONG_BINGET to ::handleLongBinGet,
                PICKLE_MARK to ::handleMark,
                PICKLE_STACK_GLOBAL to ::handleStackGlobal,
                PICKLE_NEWOBJ_EX to ::handleNewObjEx,
                PICKLE_FROZENSET to ::handleFrozenSet,
                PICKLE_POP to ::handlePop,
                PICKLE_POP_MARK to ::handlePopMark,
                PICKLE_NONE to ::handleNone,
                PICKLE_NEWTRUE to ::handleNewTrue,
                PICKLE_NEWFALSE to ::handleNewFalse,
                PICKLE_STOP to ::handleStop,
            )
        }

        fun parse(): List<RpaFileEntry> {
            var opcodeCount = 0
            while (input.available() > 0 && !stop) {
                val opcode = input.read()
                opcodeCount++
                opcodeHandlers[opcode]?.invoke() ?: handleUnknown(opcode)
            }
            Timber.tag(TAG).d("Parsing complete: opcodeCount=$opcodeCount, entries=${entries.size}, stackSize=${stack.size}")
            extractFromStackIfNeeded()
            return entries
        }

        private fun handleProto() { input.readByte() }
        private fun handleFrame() { readLittleEndianLong(8) }
        private fun handleEmptyDict() { stack.add(mutableMapOf<String, Any?>()) }
        private fun handleEmptyList() { stack.add(mutableListOf<Any?>()) }
        private fun handleEmptyTuple() { stack.add(emptyList<Any?>()) }
        private fun handleEmptySet() { stack.add(mutableSetOf<Any?>()) }

        private fun readStringOfLength(length: Int): String {
            val bytes = ByteArray(length)
            input.readFully(bytes)
            return String(bytes, Charsets.UTF_8)
        }

        private fun handleShortBinUnicode() { stack.add(readStringOfLength(input.readUnsignedByte())) }
        private fun handleBinUnicode() { stack.add(readStringOfLength(readLittleEndianInt())) }
        private fun handleBinUnicode8() { stack.add(readStringOfLength(readLittleEndianLong(8).toInt())) }

        private fun readBytesOfLength(length: Int): ByteArray {
            val bytes = ByteArray(length)
            input.readFully(bytes)
            return bytes
        }

        private fun handleShortBinBytes() { stack.add(readBytesOfLength(input.readUnsignedByte())) }
        private fun handleBinBytes() { stack.add(readBytesOfLength(readLittleEndianInt())) }
        private fun handleBinBytes8() { stack.add(readBytesOfLength(readLittleEndianLong(8).toInt())) }

        private fun handleBinInt() { stack.add(readLittleEndianInt()) }
        private fun handleBinInt1() { stack.add(input.readUnsignedByte()) }
        private fun handleBinInt2() { stack.add(readLittleEndianShort()) }

        private fun handleLong1() { stack.add(readLittleEndianLong(input.readUnsignedByte())) }
        private fun handleLong4() { stack.add(readLittleEndianLong(readLittleEndianInt())) }

        private fun handleTuple1() {
            val a = stack.removeAt(stack.size - 1)
            stack.add(listOf(a))
        }

        private fun handleTuple2() {
            val b = stack.removeAt(stack.size - 1)
            val a = stack.removeAt(stack.size - 1)
            stack.add(listOf(a, b))
        }

        private fun handleTuple3() {
            val c = stack.removeAt(stack.size - 1)
            val b = stack.removeAt(stack.size - 1)
            val a = stack.removeAt(stack.size - 1)
            stack.add(listOf(a, b, c))
        }

        private fun handleTuple() {
            if (markIndex >= 0) {
                val items = stack.subList(markIndex, stack.size).toList()
                repeat(stack.size - markIndex) { stack.removeAt(stack.size - 1) }
                stack.add(items)
                markIndex = -1
            }
        }

        private fun handleList() {
            if (markIndex >= 0 && markIndex <= stack.size) {
                val items = stack.subList(markIndex, stack.size).toMutableList()
                stack.subList(markIndex, stack.size).clear()
                stack.add(items)
                markIndex = -1
            }
        }

        private fun handleAppend() {
            val item = stack.removeAt(stack.size - 1)
            val lastElement = stack.lastOrNull()
            if (lastElement is MutableList<*>) {
                @Suppress("UNCHECKED_CAST")
                (lastElement as MutableList<Any?>).add(item)
            }
        }

        private fun handleAppends() {
            if (markIndex > 0 && stack.size > markIndex) {
                val items = stack.subList(markIndex, stack.size).toList()
                val list = stack[markIndex - 1]
                if (list is MutableList<*>) {
                    @Suppress("UNCHECKED_CAST")
                    (list as MutableList<Any?>).addAll(items)
                }
                repeat(items.size) { stack.removeAt(stack.size - 1) }
                markIndex = -1
            }
        }

        private fun extractEntryFromRpaValue(dictKey: String, value: List<*>) {
            val tuple = value.firstOrNull() as? List<*> ?: return
            if (tuple.size < 2) return
            val obfuscatedOffset = (tuple[0] as? Number)?.toLong() ?: 0L
            val obfuscatedSize = (tuple[1] as? Number)?.toLong() ?: 0L
            entries.add(RpaFileEntry(dictKey, obfuscatedOffset xor key, obfuscatedSize xor key))
        }

        private fun handleSetitem() {
            val value = stack.removeAt(stack.size - 1)
            val dictKey = stack.removeAt(stack.size - 1)
            Timber.tag(TAG).d("SETITEM: key=$dictKey (${dictKey?.javaClass?.simpleName}), value=$value (${value?.javaClass?.simpleName})")
            if (dictKey is String && value is List<*>) {
                Timber.tag(TAG).d("  Tuple: ${value.firstOrNull()} (size=${(value.firstOrNull() as? List<*>)?.size})")
                extractEntryFromRpaValue(dictKey, value)
            }
        }

        private fun handleSetitems() {
            if (markIndex >= 0 && stack.size > markIndex) {
                val items = stack.subList(markIndex, stack.size).toList()
                repeat(items.size) { stack.removeAt(stack.size - 1) }
                Timber.tag(TAG).d("SETITEMS: processing ${items.size} items")
                for (i in 0 until items.size step 2) {
                    if (i + 1 >= items.size) continue
                    val dictKey = items[i]
                    val value = items[i + 1]
                    Timber.tag(TAG).d("  [$i] key=$dictKey (${dictKey?.javaClass?.simpleName}), value type=${value?.javaClass?.simpleName}")
                    if (dictKey is String && value is List<*>) {
                        extractEntryFromRpaValue(dictKey, value)
                    }
                }
                markIndex = -1
            }
        }

        private fun handleAdditems() {
            if (markIndex >= 0) {
                val items = stack.subList(markIndex + 1, stack.size).toList()
                val set = stack[markIndex]
                if (set is MutableSet<*>) {
                    @Suppress("UNCHECKED_CAST")
                    (set as MutableSet<Any?>).addAll(items)
                }
                repeat(items.size) { stack.removeAt(stack.size - 1) }
                markIndex = -1
            }
        }

        private fun storeMemo(index: Int) {
            val obj = stack.lastOrNull()
            if (obj != null) memo[index] = obj
        }

        private fun handleMemoize() { storeMemo(memo.size) }
        private fun handleBinPut() { storeMemo(input.readUnsignedByte()) }
        private fun handleLongBinPut() { storeMemo(readLittleEndianInt()) }

        private fun getMemo(index: Int) {
            val obj = memo[index]
            if (obj != null) stack.add(obj)
        }

        private fun handleBinGet() { getMemo(input.readUnsignedByte()) }
        private fun handleLongBinGet() { getMemo(readLittleEndianInt()) }

        private fun handleMark() { markIndex = stack.size }

        private fun handleStackGlobal() {
            if (stack.size >= 2) {
                stack.removeAt(stack.size - 1)
                stack.removeAt(stack.size - 1)
            }
        }

        private fun handleNewObjEx() {
            if (stack.size >= 3) {
                stack.removeAt(stack.size - 1)
                stack.removeAt(stack.size - 1)
                stack.removeAt(stack.size - 1)
            }
        }

        private fun handleFrozenSet() {
            if (markIndex >= 0) {
                val items = stack.subList(markIndex, stack.size).toList()
                repeat(stack.size - markIndex) { stack.removeAt(stack.size - 1) }
                stack.add(items.toSet())
                markIndex = -1
            }
        }

        private fun handlePop() { if (stack.isNotEmpty()) stack.removeAt(stack.size - 1) }

        private fun handlePopMark() {
            if (markIndex >= 0) {
                repeat(stack.size - markIndex) { stack.removeAt(stack.size - 1) }
                markIndex = -1
            }
        }

        private fun handleNone() { stack.add(null) }
        private fun handleNewTrue() { stack.add(true) }
        private fun handleNewFalse() { stack.add(false) }
        private fun handleStop() { stop = true }

        private fun handleUnknown(opcode: Int) {
            Timber.tag(TAG).w("Unknown pickle opcode: 0x${opcode.toString(16)}")
        }

        private fun extractFromStackIfNeeded() {
            if (entries.isNotEmpty() || stack.size <= 2) return
            Timber.tag(TAG).w("No entries via SETITEM/SETITEMS, extracting from stack pattern")
            var i = 1
            while (i + 1 < stack.size) {
                val filename = stack[i]
                val dataList = stack[i + 1]
                if (filename is String && dataList is List<*> && dataList.size == 1) {
                    val first = dataList[0]
                    if (first is List<*> && first.size >= 2) {
                        val obfuscatedOffset = (first[0] as? Number)?.toLong() ?: 0L
                        val obfuscatedSize = (first[1] as? Number)?.toLong() ?: 0L
                        entries.add(RpaFileEntry(filename, obfuscatedOffset xor key, obfuscatedSize xor key))
                    }
                }
                i += 2
            }
            Timber.tag(TAG).d("Stack pattern extraction complete: ${entries.size} entries recovered")
        }

        private fun readLittleEndianInt(): Int {
            val b1 = input.readUnsignedByte()
            val b2 = input.readUnsignedByte()
            val b3 = input.readUnsignedByte()
            val b4 = input.readUnsignedByte()
            return b1 or (b2 shl 8) or (b3 shl 16) or (b4 shl 24)
        }

        private fun readLittleEndianShort(): Int {
            val b1 = input.readUnsignedByte()
            val b2 = input.readUnsignedByte()
            return b1 or (b2 shl 8)
        }

        private fun readLittleEndianLong(bytes: Int): Long {
            var result = 0L
            for (i in 0 until minOf(bytes, 8)) {
                result = result or (input.readUnsignedByte().toLong() shl (i * 8))
            }
            return result
        }
    }

    /**
     * RPA file entry parsed from index.
     */
    data class RpaFileEntry(
        val name: String,
        val offset: Long,
        val size: Long
    )
}
