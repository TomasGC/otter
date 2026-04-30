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
 * Pattern conventions:
 *
 * For ARCHIVE formats (TAR, ZIP):
 * - Archive name: test.<extension>
 * - Contains folder: test<Extension>/ (camelCase)
 * - Contains file: file.txt with content "<Extension>"
 * - Example: test.tar.gz → testTarGz/file.txt → "TarGz"
 *
 * For COMPRESSION formats (GZIP):
 * - Compressed file: file.txt.<extension>
 * - Decompresses to: file.txt containing "<Extension>"
 * - Example: file.txt.gz → file.txt → "Gz"
 *
 * Note: RAR and 7Z archives are kept as assets since Apache Commons Compress
 * doesn't support creating these formats, and 7-Zip-JBinding creation API is complex.
 */
object TestArchiveHelper {

    /**
     * Creates a GZIP compressed file (file.txt.gz).
     *
     * Pattern: file.txt.gz decompresses to file.txt containing "Gz"
     */
    fun createGzFile(outputFile: File) {
        val content = "Gz".toByteArray()
        outputFile.outputStream().use { fileOut ->
            GzipCompressorOutputStream(fileOut).use { gzipOut ->
                gzipOut.write(content)
            }
        }
    }

    /**
     * Creates a GZIP compressed file (file.txt.gzip).
     *
     * Pattern: file.txt.gzip decompresses to file.txt containing "Gzip"
     */
    fun createGzipFile(outputFile: File) {
        val content = "Gzip".toByteArray()
        outputFile.outputStream().use { fileOut ->
            GzipCompressorOutputStream(fileOut).use { gzipOut ->
                gzipOut.write(content)
            }
        }
    }

    /**
     * Creates a TAR archive (test-plain.tar).
     *
     * Pattern: test-plain.tar → testTar/file.txt → "Tar"
     */
    fun createTarFile(outputFile: File) {
        val baos = ByteArrayOutputStream()
        TarArchiveOutputStream(baos).use { tarOut ->
            // Create testTar/file.txt entry
            val fileContent = "Tar".toByteArray()
            val entry = TarArchiveEntry("testTar/file.txt")
            entry.size = fileContent.size.toLong()

            tarOut.putArchiveEntry(entry)
            tarOut.write(fileContent)
            tarOut.closeArchiveEntry()
        }

        outputFile.writeBytes(baos.toByteArray())
    }

    /**
     * Creates a TAR.GZ archive (test.tar.gz).
     *
     * Pattern: test.tar.gz → testTarGz/file.txt → "TarGz"
     */
    fun createTarGzFile(outputFile: File) {
        // Create TAR archive first
        val tarBytes = ByteArrayOutputStream()
        TarArchiveOutputStream(tarBytes).use { tarOut ->
            // Entry: testTarGz/file.txt
            addTarEntry(tarOut, "testTarGz/file.txt", "TarGz")
        }

        // Compress with GZIP
        outputFile.outputStream().use { fileOut ->
            GzipCompressorOutputStream(fileOut).use { gzipOut ->
                gzipOut.write(tarBytes.toByteArray())
            }
        }
    }

    /**
     * Creates a TGZ archive (test.tgz).
     *
     * Pattern: test.tgz → testTgz/file.txt → "Tgz"
     */
    fun createTgzFile(outputFile: File) {
        // Create TAR archive first
        val tarBytes = ByteArrayOutputStream()
        TarArchiveOutputStream(tarBytes).use { tarOut ->
            // Entry: testTgz/file.txt
            addTarEntry(tarOut, "testTgz/file.txt", "Tgz")
        }

        // Compress with GZIP
        outputFile.outputStream().use { fileOut ->
            GzipCompressorOutputStream(fileOut).use { gzipOut ->
                gzipOut.write(tarBytes.toByteArray())
            }
        }
    }

    /**
     * Creates a ZIP archive (test.zip).
     *
     * Pattern: test.zip → testZip/file.txt → "Zip"
     */
    fun createZipFile(outputFile: File) {
        val baos = ByteArrayOutputStream()
        ZipArchiveOutputStream(baos).use { zipOut ->
            // Create testZip/file.txt entry
            val fileContent = "Zip".toByteArray()
            val entry = ZipArchiveEntry("testZip/file.txt")

            zipOut.putArchiveEntry(entry)
            zipOut.write(fileContent)
            zipOut.closeArchiveEntry()
        }

        outputFile.writeBytes(baos.toByteArray())
    }

    private fun addTarEntry(tarOut: TarArchiveOutputStream, entryName: String, content: String) {
        val bytes = content.toByteArray()
        val entry = TarArchiveEntry(entryName)
        entry.size = bytes.size.toLong()

        tarOut.putArchiveEntry(entry)
        tarOut.write(bytes)
        tarOut.closeArchiveEntry()
    }
}
