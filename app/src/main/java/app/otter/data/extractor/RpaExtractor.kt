package app.otter.data.extractor

import timber.log.Timber
import app.otter.domain.model.ArchiveType
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import app.otter.util.PathValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.InflaterInputStream
import javax.inject.Inject

/**
 * RPA (Ren'Py Archive) extractor for RPA-3.0 format.
 *
 * Format specification:
 * - Header: "RPA-3.0 " (8 bytes)
 * - Index offset: 16 hex digits (16 bytes)
 * - Space: 1 byte
 * - Obfuscation key: 8 hex digits (8 bytes)
 * - Newline: 1 byte
 * - Padding: optional text (e.g., "Made with Ren'Py.")
 * - At offset: Zlib-compressed Python pickle index
 * - File data: stored at offsets specified in index (XOR with key)
 *
 * Security note: We parse the pickle format manually (binary protocol) without executing Python code,
 * so there's no arbitrary code execution risk. This is safe for untrusted RPA archives.
 */
class RpaExtractor @Inject constructor(
    private val pathValidator: PathValidator,
    tempFileManager: ITempFileManager,
    sevenZipHelper: SevenZipExtractorHelper
) : BaseArchiveExtractor(tempFileManager, sevenZipHelper) {

    override fun supports(type: ArchiveType): Boolean = type == ArchiveType.RPA

    override fun getTag(): String = "RPA"

    override suspend fun extractInternal(
        inputStream: InputStream,
        destinationPath: File,
        archiveType: ArchiveType,
        sourceFileName: String,
        onProgress: (ExtractionProgress) -> Unit
    ): ExtractionResult = withContext(Dispatchers.IO) {
        var extractedCount = 0
        var tempFile: File? = null

        try {
            // RPA requires temp file for random access
            tempFile = tempFileManager.createTempFile(inputStream, archiveType, getTag())
            Timber.tag(getTag()).d("Created temp file: ${tempFile.absolutePath}")

            // Parse RPA-3.0 header
            val header = parseRpaHeader(tempFile)
            Timber.tag(getTag()).d("Parsed header - offset: ${header.indexOffset}, key: 0x${header.key.toString(16)}")

            // Read and decompress index
            val index = readRpaIndex(tempFile, header)
            Timber.tag(getTag()).d("Total files in archive: ${index.size}")

            // Pre-allocate large buffer for optimal I/O
            val buffer = ByteArray(BUFFER_SIZE_BYTES)

            // Progress throttler from base class
            val throttler = ProgressThrottler()

            // Extract each file
            tempFile.inputStream().use { archiveStream ->
                index.forEach { entry ->
                    if (!isActive) return@forEach

                    // Decode offset/size with XOR key
                    val realOffset = entry.offset xor header.key.toLong()
                    val realSize = entry.size xor header.key.toLong()

                    Timber.tag(getTag()).d("Extracting: ${entry.name} (offset=$realOffset, size=$realSize)")

                    // Path traversal protection + directory creation
                    val outputFile = pathValidator.createSafeOutputFile(destinationPath, entry.name)

                    // Seek to file position (mark not supported, reopen stream)
                    tempFile.inputStream().use { fileStream ->
                        fileStream.skip(realOffset)

                        // Extract file data with progress updates
                        outputFile.outputStream().buffered(BUFFER_SIZE_BYTES).use { output ->
                            var remaining = realSize
                            var bytesExtracted = 0L

                            while (remaining > 0 && isActive) {
                                val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                                val bytesRead = fileStream.read(buffer, 0, toRead)
                                if (bytesRead == -1) break
                                output.write(buffer, 0, bytesRead)
                                remaining -= bytesRead
                                bytesExtracted += bytesRead

                                // Notify progress during file extraction (for large files)
                                if (throttler.shouldNotify()) {
                                    val fileProgress = bytesExtracted.toFloat() / realSize.toFloat()
                                    val overallProgress = (extractedCount.toFloat() + fileProgress) / index.size.toFloat()
                                    onProgress(
                                        ExtractionProgress.Extracting(
                                            currentFile = entry.name,
                                            extractedCount = extractedCount,
                                            totalCount = index.size,
                                            progress = overallProgress
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Check if cancelled during file extraction
                    if (!isActive) return@forEach

                    extractedCount++

                    // Use base class helper for throttled progress notifications
                    notifyProgress(extractedCount, index.size, entry.name, throttler, onProgress)
                }
            }

            // Check for cancellation
            if (!isActive) {
                Timber.tag(getTag()).w("Extraction cancelled")
                return@withContext ExtractionResult.Failure(
                    errorMessage = "Extraction cancelled",
                    cause = null
                )
            }

            logger.logComplete(extractedCount)

            ExtractionResult.Success(
                outputPath = destinationPath.absolutePath,
                extractedFilesCount = extractedCount
            )
        } catch (e: Exception) {
            Timber.tag(getTag()).e(e, "Failed to extract RPA archive")
            ExtractionResult.Failure(
                errorMessage = e.message ?: "Unknown error",
                cause = e
            )
        } finally {
            // Clean up temp file
            tempFile?.let {
                val deleted = it.delete()
                Timber.tag(getTag()).d("Temp file deleted: $deleted")
            }
        }
    }

    /**
     * Parse RPA-3.0 header format:
     * RPA-3.0 XXXXXXXXXXXXXXXX YYYYYYYY\n
     */
    private fun parseRpaHeader(file: File): RpaHeader {
        file.inputStream().use { input ->
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
            val key = keyHex.toInt(16)

            return RpaHeader(indexOffset, key)
        }
    }

    /**
     * Read and decompress the RPA index at the specified offset.
     * Index format: Zlib-compressed Python pickle dictionary
     */
    private fun readRpaIndex(file: File, header: RpaHeader): List<RpaFileEntry> {
        file.inputStream().use { input ->
            // Seek to index offset
            input.skip(header.indexOffset)

            // Decompress with Zlib (InflaterInputStream handles zlib wrapper)
            val decompressed = InflaterInputStream(input).use { it.readBytes() }
            Timber.tag(getTag()).d("Decompressed index size: ${decompressed.size} bytes")

            // Parse Python pickle format (minimal implementation for RPA index)
            return parsePickleIndex(decompressed)
        }
    }

    /**
     * Minimal Python pickle parser for RPA-3.0 index format.
     *
     * Expected format:
     * Dict { "file/path.ext": [[offset, size], ...], ... }
     *
     * Security: We parse the binary protocol manually without executing Python code.
     * This is safe - we only extract strings and integers from the serialized format.
     */
    private fun parsePickleIndex(data: ByteArray): List<RpaFileEntry> {
        val entries = mutableListOf<RpaFileEntry>()
        val input = DataInputStream(ByteArrayInputStream(data))
        val stack = mutableListOf<Any>()
        val memo = mutableMapOf<Int, Any>()
        var markIndex = -1

        try {
            var opcode: Int
            while (input.available() > 0) {
                opcode = input.read()
                Timber.tag(getTag()).d("Opcode: 0x${opcode.toString(16).padStart(2, '0')} ('${if (opcode in 32..126) opcode.toChar() else '?'}') - stack size: ${stack.size}")

                when (opcode) {
                    PICKLE_PROTO -> {
                        input.readByte() // Protocol version
                    }
                    PICKLE_FRAME -> {
                        // Frame size: 8 bytes little-endian
                        readLittleEndianLong(input, 8)
                    }
                    PICKLE_EMPTY_DICT -> {
                        stack.add(mutableMapOf<String, Any>())
                    }
                    PICKLE_EMPTY_LIST -> {
                        stack.add(mutableListOf<Any>())
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
                    PICKLE_BININT -> {
                        stack.add(readLittleEndianInt(input))
                    }
                    PICKLE_BININT1 -> {
                        stack.add(input.readUnsignedByte())
                    }
                    PICKLE_BININT2 -> {
                        stack.add(readLittleEndianShort(input))
                    }
                    PICKLE_TUPLE3 -> {
                        // Create tuple from top 3 stack items
                        val c = stack.removeAt(stack.size - 1)
                        val b = stack.removeAt(stack.size - 1)
                        val a = stack.removeAt(stack.size - 1)
                        stack.add(listOf(a, b, c))
                    }
                    PICKLE_APPEND -> {
                        val item = stack.removeAt(stack.size - 1)
                        @Suppress("UNCHECKED_CAST")
                        val list = stack.last() as? MutableList<Any>
                        list?.add(item)
                    }
                    PICKLE_SETITEM -> {
                        // Pop value, pop key, set dict[key] = value
                        val value = stack.removeAt(stack.size - 1)
                        val key = stack.removeAt(stack.size - 1)

                        Timber.tag(getTag()).d("SETITEM: key=$key (${key?.javaClass?.simpleName}), value=$value (${value?.javaClass?.simpleName})")

                        if (key is String && value is List<*>) {
                            // RPA format: dict[filename] = [(offset, size, prefix)]
                            val tuple = value.firstOrNull() as? List<*>
                            Timber.tag(getTag()).d("  List size: ${value.size}, first element: $tuple (${tuple?.javaClass?.simpleName})")
                            if (tuple != null && tuple.size >= 2) {
                                val offset = (tuple[0] as? Number)?.toLong() ?: 0L
                                val size = (tuple[1] as? Number)?.toLong() ?: 0L
                                Timber.tag(getTag()).d("  Adding entry: $key (offset=$offset, size=$size)")
                                entries.add(RpaFileEntry(key, offset, size))
                            }
                        }
                    }
                    PICKLE_MARK -> {
                        // Mark current stack position
                        markIndex = stack.size
                        Timber.tag(getTag()).d("MARK at stack index: $markIndex")
                    }
                    PICKLE_BINGET -> {
                        // Get from memo: 1 byte index
                        val index = input.readUnsignedByte()
                        val value = memo[index]
                        if (value != null) {
                            stack.add(value)
                            Timber.tag(getTag()).d("BINGET[$index] -> $value")
                        }
                    }
                    PICKLE_LONG_BINGET -> {
                        // Get from memo: 4 byte little-endian index
                        val index = readLittleEndianInt(input)
                        val value = memo[index]
                        if (value != null) {
                            stack.add(value)
                            Timber.tag(getTag()).d("LONG_BINGET[$index] -> $value")
                        }
                    }
                    PICKLE_SETITEMS -> {
                        // Pop all key/value pairs from MARK to top, add to dict
                        if (markIndex >= 0 && markIndex < stack.size) {
                            val items = stack.subList(markIndex, stack.size).toList()
                            stack.subList(markIndex, stack.size).clear()

                            Timber.tag(getTag()).d("SETITEMS: processing ${items.size} items")

                            // Items are alternating key, value, key, value...
                            for (i in 0 until items.size step 2) {
                                if (i + 1 < items.size) {
                                    val key = items[i]
                                    val value = items[i + 1]

                                    Timber.tag(getTag()).d("  key=$key (${key?.javaClass?.simpleName}), value=$value (${value?.javaClass?.simpleName})")

                                    if (key is String && value is List<*>) {
                                        val tuple = value.firstOrNull() as? List<*>
                                        if (tuple != null && tuple.size >= 2) {
                                            val offset = (tuple[0] as? Number)?.toLong() ?: 0L
                                            val size = (tuple[1] as? Number)?.toLong() ?: 0L
                                            entries.add(RpaFileEntry(key, offset, size))
                                            Timber.tag(getTag()).d("    Added entry: $key (offset=$offset, size=$size)")
                                        }
                                    }
                                }
                            }
                            markIndex = -1
                        }
                    }
                    PICKLE_MEMOIZE -> {
                        // Memoize: store top stack item in memo
                        if (stack.isNotEmpty()) {
                            val item = stack.last()
                            val index = memo.size
                            memo[index] = item
                            Timber.tag(getTag()).d("MEMOIZE[$index] <- $item")
                        }
                    }
                    PICKLE_STOP -> {
                        Timber.tag(getTag()).d("STOP - final stack size: ${stack.size}")
                        break
                    }
                    else -> {
                        Timber.tag(getTag()).w("Unknown opcode: 0x${opcode.toString(16)} - skipping")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag(getTag()).e(e, "Error parsing pickle index")
            throw IllegalArgumentException("Failed to parse RPA index", e)
        }

        Timber.tag(getTag()).d("Parsed ${entries.size} entries from pickle index")
        return entries
    }

    /**
     * Read a string from pickle format based on opcode
     */
    private fun readPickleString(input: DataInputStream, opcode: Int): String {
        return when (opcode) {
            PICKLE_STRING -> {
                // STRING 'text'\n format
                val line = readPickleLine(input)
                line.trim('\'', '"', '\n')
            }
            PICKLE_BINSTRING -> {
                // BINSTRING length(4 bytes) + data
                val length = input.readInt()
                val bytes = ByteArray(length)
                input.readFully(bytes)
                String(bytes, Charsets.UTF_8)
            }
            PICKLE_SHORT_BINSTRING -> {
                // SHORT_BINSTRING length(1 byte) + data
                val length = input.readUnsignedByte()
                val bytes = ByteArray(length)
                input.readFully(bytes)
                String(bytes, Charsets.UTF_8)
            }
            else -> ""
        }
    }

    /**
     * Read file data tuple: [[offset, size]]
     */
    private fun readPickleFileData(input: DataInputStream): Pair<Long, Long> {
        // Simplified: read until we find two LONG/INT values
        var offset = 0L
        var size = 0L
        var foundOffset = false

        var opcode: Int
        while (input.available() > 0) {
            opcode = input.read()

            when (opcode) {
                PICKLE_LONG, PICKLE_LONG1, PICKLE_LONG4 -> {
                    val value = readPickleLong(input, opcode)
                    if (!foundOffset) {
                        offset = value
                        foundOffset = true
                    } else {
                        size = value
                        break
                    }
                }
                PICKLE_BININT, PICKLE_BININT1, PICKLE_BININT2 -> {
                    val value = readPickleInt(input, opcode).toLong()
                    if (!foundOffset) {
                        offset = value
                        foundOffset = true
                    } else {
                        size = value
                        break
                    }
                }
                PICKLE_LIST, PICKLE_TUPLE, PICKLE_MARK, PICKLE_EMPTY_LIST -> {
                    // Continue reading nested structures
                    continue
                }
                PICKLE_APPEND, PICKLE_APPENDS, PICKLE_SETITEMS -> {
                    // End of list/tuple
                    break
                }
            }
        }

        return Pair(offset, size)
    }

    /**
     * Read integer from pickle format
     */
    private fun readPickleInt(input: DataInputStream, opcode: Int): Int {
        return when (opcode) {
            PICKLE_BININT -> input.readInt()
            PICKLE_BININT1 -> input.readUnsignedByte()
            PICKLE_BININT2 -> input.readUnsignedShort()
            else -> 0
        }
    }

    /**
     * Read long from pickle format
     */
    private fun readPickleLong(input: DataInputStream, opcode: Int): Long {
        return when (opcode) {
            PICKLE_LONG -> {
                // LONG 'digits'L\n format
                val line = readPickleLine(input)
                line.trim('L', '\n').toLongOrNull() ?: 0L
            }
            PICKLE_LONG1 -> {
                // LONG1 length(1 byte) + little-endian data
                val length = input.readUnsignedByte()
                readLittleEndianLong(input, length)
            }
            PICKLE_LONG4 -> {
                // LONG4 length(4 bytes) + little-endian data
                val length = input.readInt()
                readLittleEndianLong(input, length)
            }
            else -> 0L
        }
    }

    /**
     * Read little-endian long
     */
    private fun readLittleEndianInt(input: DataInputStream): Int {
        val bytes = ByteArray(4)
        input.readFully(bytes)
        return (bytes[0].toInt() and 0xFF) or
                ((bytes[1].toInt() and 0xFF) shl 8) or
                ((bytes[2].toInt() and 0xFF) shl 16) or
                ((bytes[3].toInt() and 0xFF) shl 24)
    }

    private fun readLittleEndianShort(input: DataInputStream): Int {
        val bytes = ByteArray(2)
        input.readFully(bytes)
        return (bytes[0].toInt() and 0xFF) or
                ((bytes[1].toInt() and 0xFF) shl 8)
    }

    private fun readLittleEndianLong(input: DataInputStream, byteCount: Int): Long {
        val bytes = ByteArray(byteCount)
        input.readFully(bytes)

        var result = 0L
        for (i in 0 until minOf(byteCount, 8)) {
            result = result or ((bytes[i].toLong() and 0xFF) shl (i * 8))
        }
        return result
    }

    /**
     * Read line until newline
     */
    private fun readPickleLine(input: DataInputStream): String {
        val builder = StringBuilder()
        var c: Char
        while (input.available() > 0) {
            c = input.readByte().toInt().toChar()
            if (c == '\n') break
            builder.append(c)
        }
        return builder.toString()
    }

    /**
     * RPA header data
     */
    private data class RpaHeader(
        val indexOffset: Long,
        val key: Int
    )

    /**
     * RPA file entry in index
     */
    private data class RpaFileEntry(
        val name: String,
        val offset: Long,
        val size: Long
    )

    companion object {
        private const val BUFFER_SIZE_BYTES = 256 * 1024 // 256 KB
        private const val HEADER_SIZE = 34 // "RPA-3.0 " + 16 hex + " " + 8 hex + "\n"
        private const val MAGIC_RPA3 = "RPA-3.0 "

        // Python pickle opcodes (protocol 4/5 - subset needed for RPA)
        private const val PICKLE_MARK = 0x28        // '('
        private const val PICKLE_STOP = 0x2E        // '.'
        private const val PICKLE_BININT = 0x4A      // 'J'
        private const val PICKLE_BININT1 = 0x4B     // 'K'
        private const val PICKLE_BININT2 = 0x4D     // 'M'
        private const val PICKLE_LONG = 0x4C        // 'L'
        private const val PICKLE_LONG1 = 0x8A       // '\x8a'
        private const val PICKLE_LONG4 = 0x8B       // '\x8b'
        private const val PICKLE_STRING = 0x53      // 'S'
        private const val PICKLE_BINSTRING = 0x54   // 'T'
        private const val PICKLE_SHORT_BINSTRING = 0x55 // 'U'
        private const val PICKLE_EMPTY_LIST = 0x5D  // ']'
        private const val PICKLE_APPEND = 0x61      // 'a'
        private const val PICKLE_APPENDS = 0x65     // 'e'
        private const val PICKLE_DICT = 0x64        // 'd'
        private const val PICKLE_EMPTY_DICT = 0x7D  // '}'
        private const val PICKLE_BINGET = 0x68      // 'h'
        private const val PICKLE_LONG_BINGET = 0x6A // 'j'
        private const val PICKLE_SETITEMS = 0x75    // 'u'
        private const val PICKLE_SETITEM = 0x73     // 's'
        private const val PICKLE_LIST = 0x6C        // 'l'
        private const val PICKLE_TUPLE = 0x74       // 't'
        private const val PICKLE_TUPLE3 = 0x87      // '\x87'
        private const val PICKLE_PROTO = 0x80       // '\x80'
        private const val PICKLE_FRAME = 0x95       // '\x95'
        private const val PICKLE_MEMOIZE = 0x94     // '\x94'
        private const val PICKLE_SHORT_BINUNICODE = 0x8C // '\x8c'
        private const val PICKLE_BINUNICODE = 0x8D  // '\x8d'
        private const val PICKLE_SHORT_BINBYTES = 0x43 // 'C'
        private const val PICKLE_BINBYTES = 0x42    // 'B'
        private const val PICKLE_BINBYTES8 = 0x8E   // '\x8e'
    }
}
