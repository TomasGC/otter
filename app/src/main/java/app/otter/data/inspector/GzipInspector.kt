package app.otter.data.inspector

import app.otter.domain.inspector.ArchiveEntry
import app.otter.domain.inspector.ArchiveInspector
import app.otter.domain.inspector.ArchiveType
import java.io.File

class GzipInspector(private val file: File) : ArchiveInspector {

    private var closed = false

    private val innerFileName: String by lazy { deriveInnerFileName(file.name) }

    override suspend fun countEntries(): Int {
        checkNotClosed()
        return 1
    }

    override fun entries(): Sequence<ArchiveEntry> {
        checkNotClosed()
        return sequenceOf(
            ArchiveEntry(
                path = innerFileName,
                isDirectory = false,
                sizeBytes = 0L,
                compressedSize = file.length(),
                lastModified = file.lastModified()
            )
        )
    }

    override fun isEncrypted(): Boolean = false

    override fun getArchiveType(): ArchiveType = ArchiveType.GZIP

    override fun close() {
        closed = true
    }

    private fun checkNotClosed() {
        check(!closed) { "GzipInspector is closed" }
    }

    private fun deriveInnerFileName(fileName: String): String {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".gz") -> fileName.dropLast(3)
            lower.endsWith(".gzip") -> fileName.dropLast(5)
            else -> fileName
        }
    }
}
