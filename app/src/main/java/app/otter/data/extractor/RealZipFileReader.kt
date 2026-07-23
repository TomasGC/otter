package app.otter.data.extractor

import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Real implementation of IZipFileReader using java.util.zip.ZipFile.
 * Used in production code and real integration tests.
 */
class RealZipFileReader(
    private val file: File
) : IZipFileReader {

    private val zipFile: ZipFile = ZipFile(file)

    override fun getEntries(): Sequence<ZipEntry> {
        return zipFile.entries().asSequence().filter { !it.isDirectory }
    }

    override fun countFiles(): Int {
        return zipFile.entries().asSequence().count { !it.isDirectory }
    }

    override fun getInputStream(entry: ZipEntry): InputStream {
        return zipFile.getInputStream(entry)
    }

    override fun close() {
        zipFile.close()
    }
}
