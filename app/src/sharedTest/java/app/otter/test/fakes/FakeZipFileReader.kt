package app.otter.test.fakes

import app.otter.data.extractor.IZipFileReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipEntry

/**
 * Fake implementation of IZipFileReader for testing.
 * Allows testing ZIP extraction logic without real file I/O.
 */
class FakeZipFileReader(
    private val entries: Map<String, String>  // fileName -> content
) : IZipFileReader {

    override fun getEntries(): Sequence<ZipEntry> {
        return entries.keys.asSequence().map { name ->
            ZipEntry(name)
        }
    }

    override fun countFiles(): Int {
        return entries.size
    }

    override fun getInputStream(entry: ZipEntry): InputStream {
        val content = entries[entry.name] ?: ""
        return ByteArrayInputStream(content.toByteArray())
    }

    override fun close() {
        // No-op for fake
    }
}
