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

    /**
     * Parse pickle-serialized RPA index and extract file entries.
     *
     * @param data Pickle binary data (already decompressed)
     * @param key XOR obfuscation key for offset/size values
     * @return List of file entries with deobfuscated offsets and sizes
     */
    fun parseIndex(data: ByteArray, key: Long): List<RpaFileEntry> {
        val entries = mutableListOf<RpaFileEntry>()
        val input = DataInputStream(ByteArrayInputStream(data))
        val stack = mutableListOf<Any?>()
        val memo = mutableMapOf<Int, Any?>()
        var markIndex = -1

        try {
            var opcode: Int
            var opcodeCount = 0
            while (input.available() > 0) {
                opcode = input.read()
                opcodeCount++

                // Opcode logging disabled for performance (2.9M opcodes per archive)

                when (opcode) {
                    PICKLE_PROTO -> {
                        input.readByte() // Protocol version
                    }
                    PICKLE_FRAME -> {
                        readLittleEndianLong(input, 8)
                    }
                    PICKLE_EMPTY_DICT -> {
                        stack.add(mutableMapOf<String, Any?>())
                    }
                    PICKLE_EMPTY_LIST -> {
                        stack.add(mutableListOf<Any?>())
                    }
                    PICKLE_EMPTY_TUPLE -> {
                        stack.add(emptyList<Any?>())
                    }
                    PICKLE_EMPTY_SET -> {
                        stack.add(mutableSetOf<Any?>())
                    }
                    PICKLE_SHORT_BINUNICODE -> {
                        val length = input.readUnsignedByte()
                        val bytes = ByteArray(length)
                        input.readFully(bytes)
                        stack.add(String(bytes, Charsets.UTF_8))
                    }
                    PICKLE_BINUNICODE -> {
                        val length = readLittleEndianInt(input)
                        val bytes = ByteArray(length)
                        input.readFully(bytes)
                        stack.add(String(bytes, Charsets.UTF_8))
                    }
                    PICKLE_BINUNICODE8 -> {
                        val length = readLittleEndianLong(input, 8).toInt()
                        val bytes = ByteArray(length)
                        input.readFully(bytes)
                        stack.add(String(bytes, Charsets.UTF_8))
                    }
                    PICKLE_SHORT_BINBYTES -> {
                        val length = input.readUnsignedByte()
                        val bytes = ByteArray(length)
                        input.readFully(bytes)
                        stack.add(bytes)
                    }
                    PICKLE_BINBYTES -> {
                        val length = readLittleEndianInt(input)
                        val bytes = ByteArray(length)
                        input.readFully(bytes)
                        stack.add(bytes)
                    }
                    PICKLE_BINBYTES8 -> {
                        val length = readLittleEndianLong(input, 8).toInt()
                        val bytes = ByteArray(length)
                        input.readFully(bytes)
                        stack.add(bytes)
                    }
                    PICKLE_BININT -> {
                        stack.add(readLittleEndianInt(input))
                    }
                    PICKLE_BININT1 -> {
                        stack.add(input.readUnsignedByte())
                    }
                    PICKLE_BININT2 -> {
                        stack.add(readLittleEndianShort(input))
                    }
                    PICKLE_LONG1 -> {
                        val length = input.readUnsignedByte()
                        stack.add(readLittleEndianLong(input, length))
                    }
                    PICKLE_LONG4 -> {
                        val length = readLittleEndianInt(input)
                        stack.add(readLittleEndianLong(input, length))
                    }
                    PICKLE_TUPLE1 -> {
                        val a = stack.removeAt(stack.size - 1)
                        stack.add(listOf(a))
                    }
                    PICKLE_TUPLE2 -> {
                        val b = stack.removeAt(stack.size - 1)
                        val a = stack.removeAt(stack.size - 1)
                        stack.add(listOf(a, b))
                    }
                    PICKLE_TUPLE3 -> {
                        val c = stack.removeAt(stack.size - 1)
                        val b = stack.removeAt(stack.size - 1)
                        val a = stack.removeAt(stack.size - 1)
                        stack.add(listOf(a, b, c))
                    }
                    PICKLE_TUPLE -> {
                        if (markIndex >= 0) {
                            val items = stack.subList(markIndex, stack.size).toList()
                            repeat(stack.size - markIndex) {
                                stack.removeAt(stack.size - 1)
                            }
                            stack.add(items)
                            markIndex = -1
                        }
                    }
                    PICKLE_LIST -> {
                        if (markIndex >= 0 && markIndex <= stack.size) {
                            val items = stack.subList(markIndex, stack.size).toMutableList()
                            stack.subList(markIndex, stack.size).clear()
                            stack.add(items)
                            markIndex = -1
                        }
                    }
                    PICKLE_APPEND -> {
                        val item = stack.removeAt(stack.size - 1)
                        val lastElement = stack.lastOrNull()
                        when (lastElement) {
                            is MutableList<*> -> {
                                @Suppress("UNCHECKED_CAST")
                                (lastElement as MutableList<Any?>).add(item)
                            }
                        }
                    }
                    PICKLE_APPENDS -> {
                        if (markIndex > 0 && stack.size > markIndex) {
                            val items = stack.subList(markIndex, stack.size).toList()
                            val list = stack[markIndex - 1] // List is BEFORE the mark
                            if (list is MutableList<*>) {
                                @Suppress("UNCHECKED_CAST")
                                (list as MutableList<Any?>).addAll(items)
                            }
                            repeat(items.size) {
                                stack.removeAt(stack.size - 1)
                            }
                            markIndex = -1
                        }
                    }
                    PICKLE_SETITEM -> {
                        val value = stack.removeAt(stack.size - 1)
                        val dictKey = stack.removeAt(stack.size - 1)

                        Timber.tag(TAG).d("SETITEM: key=$dictKey (${dictKey?.javaClass?.simpleName}), value=$value (${value?.javaClass?.simpleName})")

                        if (dictKey is String && value is List<*>) {
                            // RPA format: dict[filename] = [(offset, size, ...)]
                            val tuple = value.firstOrNull() as? List<*>
                            Timber.tag(TAG).d("  Tuple: $tuple (size=${tuple?.size})")
                            if (tuple != null && tuple.size >= 2) {
                                val obfuscatedOffset = (tuple[0] as? Number)?.toLong() ?: 0L
                                val obfuscatedSize = (tuple[1] as? Number)?.toLong() ?: 0L
                                val offset = obfuscatedOffset xor key
                                val size = obfuscatedSize xor key
                                Timber.tag(TAG).d("  Adding: $dictKey offset=$offset size=$size")
                                entries.add(RpaFileEntry(dictKey, offset, size))
                            }
                        }
                    }
                    PICKLE_SETITEMS -> {
                        // RPA format doesn't use MARK before SETITEMS, so this opcode never works
                        // Items stay on stack and are extracted manually after parsing completes
                        if (markIndex >= 0 && stack.size > markIndex + 1) {
                            val items = stack.subList(markIndex + 1, stack.size).toList()
                            repeat(items.size) {
                                stack.removeAt(stack.size - 1)
                            }

                            Timber.tag(TAG).d("SETITEMS: processing ${items.size} items")

                            // Standard pickle SETITEMS: alternating key, value pairs
                            for (i in 0 until items.size step 2) {
                                if (i + 1 < items.size) {
                                    val dictKey = items[i]
                                    val value = items[i + 1]

                                    Timber.tag(TAG).d("  [$i] key=$dictKey (${dictKey?.javaClass?.simpleName}), value type=${value?.javaClass?.simpleName}")

                                    if (dictKey is String && value is List<*>) {
                                        val tuple = value.firstOrNull() as? List<*>
                                        Timber.tag(TAG).d("    Tuple: $tuple (size=${tuple?.size})")
                                        if (tuple != null && tuple.size >= 2) {
                                            val obfuscatedOffset = (tuple[0] as? Number)?.toLong() ?: 0L
                                            val obfuscatedSize = (tuple[1] as? Number)?.toLong() ?: 0L
                                            val offset = obfuscatedOffset xor key
                                            val size = obfuscatedSize xor key
                                            Timber.tag(TAG).d("    Adding: $dictKey offset=$offset size=$size")
                                            entries.add(RpaFileEntry(dictKey, offset, size))
                                        }
                                    }
                                }
                            }
                            markIndex = -1
                        }
                    }
                    PICKLE_ADDITEMS -> {
                        if (markIndex >= 0) {
                            val items = stack.subList(markIndex + 1, stack.size).toList()
                            val set = stack[markIndex]
                            if (set is MutableSet<*>) {
                                @Suppress("UNCHECKED_CAST")
                                (set as MutableSet<Any?>).addAll(items)
                            }
                            repeat(items.size) {
                                stack.removeAt(stack.size - 1)
                            }
                            markIndex = -1
                        }
                    }
                    PICKLE_MEMOIZE -> {
                        val obj = stack.lastOrNull()
                        if (obj != null) {
                            memo[memo.size] = obj
                        }
                    }
                    PICKLE_BINPUT -> {
                        val index = input.readUnsignedByte()
                        val obj = stack.lastOrNull()
                        if (obj != null) {
                            memo[index] = obj
                        }
                    }
                    PICKLE_LONG_BINPUT -> {
                        val index = readLittleEndianInt(input)
                        val obj = stack.lastOrNull()
                        if (obj != null) {
                            memo[index] = obj
                        }
                    }
                    PICKLE_BINGET -> {
                        val index = input.readUnsignedByte()
                        val obj = memo[index]
                        if (obj != null) {
                            stack.add(obj)
                        }
                    }
                    PICKLE_LONG_BINGET -> {
                        val index = readLittleEndianInt(input)
                        val obj = memo[index]
                        if (obj != null) {
                            stack.add(obj)
                        }
                    }
                    PICKLE_MARK -> {
                        markIndex = stack.size
                    }
                    PICKLE_STACK_GLOBAL -> {
                        // Global object from stack (module, name) - ignore for RPA
                        if (stack.size >= 2) {
                            stack.removeAt(stack.size - 1)
                            stack.removeAt(stack.size - 1)
                        }
                    }
                    PICKLE_NEWOBJ_EX -> {
                        // Create object with kwargs - ignore for RPA
                        if (stack.size >= 3) {
                            stack.removeAt(stack.size - 1)
                            stack.removeAt(stack.size - 1)
                            stack.removeAt(stack.size - 1)
                        }
                    }
                    PICKLE_FROZENSET -> {
                        if (markIndex >= 0) {
                            val items = stack.subList(markIndex, stack.size).toList()
                            repeat(stack.size - markIndex) {
                                stack.removeAt(stack.size - 1)
                            }
                            stack.add(items.toSet())
                            markIndex = -1
                        }
                    }
                    PICKLE_POP -> {
                        if (stack.isNotEmpty()) {
                            stack.removeAt(stack.size - 1)
                        }
                    }
                    PICKLE_POP_MARK -> {
                        if (markIndex >= 0) {
                            repeat(stack.size - markIndex) {
                                stack.removeAt(stack.size - 1)
                            }
                            markIndex = -1
                        }
                    }
                    PICKLE_NONE -> {
                        stack.add(null)
                    }
                    PICKLE_NEWTRUE -> {
                        stack.add(true)
                    }
                    PICKLE_NEWFALSE -> {
                        stack.add(false)
                    }
                    PICKLE_STOP -> {
                        break
                    }
                    else -> {
                        Timber.tag(TAG).w("Unknown pickle opcode: 0x${opcode.toString(16)}")
                    }
                }
            }

            Timber.tag(TAG).d("Parsing complete: opcodeCount=$opcodeCount, entries=${entries.size}, stackSize=${stack.size}")

            // CRITICAL FIX: RPA format doesn't populate dict via SETITEMS correctly
            // Extract from stack pattern after parsing: dict, filename, dataList, filename, dataList, ...
            if (entries.isEmpty() && stack.size > 2) {
                Timber.tag(TAG).w("No entries via SETITEM/SETITEMS, extracting from stack pattern")

                // Skip stack[0] (empty dict), process rest in pairs: (filename, dataList)
                var i = 1
                while (i + 1 < stack.size) {
                    val filename = stack[i]
                    val dataList = stack[i + 1]

                    if (filename is String && dataList is List<*> && dataList.size == 1 && dataList[0] is List<*>) {
                        // RPA format: [[offset, size]] - nested list
                        val innerList = dataList[0] as List<*>
                        if (innerList.size >= 2) {
                            val obfuscatedOffset = (innerList[0] as? Number)?.toLong() ?: 0L
                            val obfuscatedSize = (innerList[1] as? Number)?.toLong() ?: 0L
                            val offset = obfuscatedOffset xor key
                            val size = obfuscatedSize xor key
                            entries.add(RpaFileEntry(filename, offset, size))
                        }
                    }

                    i += 2 // Move to next filename (pairs, not triples)
                }

                Timber.tag(TAG).d("Stack pattern extraction complete: ${entries.size} entries recovered")
            }

            return entries
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to parse pickle index")
            throw IllegalArgumentException("Failed to parse RPA index: ${e.message}", e)
        }
    }

    private fun readLittleEndianInt(input: DataInputStream): Int {
        val b1 = input.readUnsignedByte()
        val b2 = input.readUnsignedByte()
        val b3 = input.readUnsignedByte()
        val b4 = input.readUnsignedByte()
        return b1 or (b2 shl 8) or (b3 shl 16) or (b4 shl 24)
    }

    private fun readLittleEndianShort(input: DataInputStream): Int {
        val b1 = input.readUnsignedByte()
        val b2 = input.readUnsignedByte()
        return b1 or (b2 shl 8)
    }

    private fun readLittleEndianLong(input: DataInputStream, bytes: Int): Long {
        var result = 0L
        for (i in 0 until minOf(bytes, 8)) {
            result = result or (input.readUnsignedByte().toLong() shl (i * 8))
        }
        return result
    }

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
     * RPA file entry parsed from index.
     */
    data class RpaFileEntry(
        val name: String,
        val offset: Long,
        val size: Long
    )
}
