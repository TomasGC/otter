package app.otter.data.inspector

import app.otter.domain.inspector.ArchiveEntry
import app.otter.domain.inspector.ArchiveInspector
import app.otter.domain.inspector.ArchiveType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream

class TarInspector(
    private val file: File,
    private val archiveType: ArchiveType
) : ArchiveInspector {

    private var closed = false
    private val cachedEntries: List<ArchiveEntry> by lazy { readAllEntries() }

    override suspend fun countEntries(): Int = withContext(Dispatchers.IO) {
        checkNotClosed()
        cachedEntries.size
    }

    override fun entries(): Sequence<ArchiveEntry> {
        checkNotClosed()
        return cachedEntries.asSequence()
    }

    override fun isEncrypted(): Boolean = false

    override fun getArchiveType(): ArchiveType = archiveType

    override fun close() {
        closed = true
    }

    private fun checkNotClosed() {
        check(!closed) { "TarInspector is closed" }
    }

    private fun readAllEntries(): List<ArchiveEntry> {
        return openTarStream().use { tar ->
            generateSequence { tar.nextTarEntry }
                .map { entry ->
                    ArchiveEntry(
                        path = entry.name,
                        isDirectory = entry.isDirectory,
                        sizeBytes = if (entry.isDirectory) 0L else entry.size,
                        compressedSize = 0L,
                        lastModified = entry.lastModifiedDate?.time ?: 0L
                    )
                }
                .toList()
        }
    }

    private fun openTarStream(): TarArchiveInputStream {
        val buffered = BufferedInputStream(FileInputStream(file))
        return when (archiveType) {
            ArchiveType.TAR_GZ -> TarArchiveInputStream(GzipCompressorInputStream(buffered))
            ArchiveType.TAR_BZ2 -> TarArchiveInputStream(BZip2CompressorInputStream(buffered))
            else -> TarArchiveInputStream(buffered)
        }
    }
}
