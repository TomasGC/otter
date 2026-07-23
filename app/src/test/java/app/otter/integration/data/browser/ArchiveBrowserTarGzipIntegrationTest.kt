package app.otter.integration.data.browser

import app.otter.data.browser.ArchiveBrowser
import app.otter.data.inspector.GzipInspector
import app.otter.data.inspector.TarInspector
import app.otter.domain.inspector.ArchiveType
import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.BrowseResult
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

class ArchiveBrowserTarGzipIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `TAR archive browse root returns all root entries as Complete`() = runTest {
        val tar = createTar("archive.tar", listOf(
            "readme.txt" to "hello".toByteArray(),
            "data.bin" to byteArrayOf(0x01, 0x02),
            "image.png" to byteArrayOf(0xFF.toByte())
        ))
        val browser = ArchiveBrowser(TarInspector(tar, ArchiveType.TAR), tar.absolutePath)

        val result = browser.browse(entryPath = "", offset = 0, limit = 100)

        assertTrue(result is BrowseResult.Complete)
        assertEquals(3, result.items.size)
        val names = result.items.map { it.name }
        assertTrue(names.contains("readme.txt"))
        assertTrue(names.contains("data.bin"))
        assertTrue(names.contains("image.png"))
    }

    @Test
    fun `TAR archive browse root returns directories before files`() = runTest {
        val tar = createTar("sorted.tar", listOf(
            "readme.txt" to "root file".toByteArray(),
            "images/" to byteArrayOf(),
            "images/photo.jpg" to byteArrayOf(0xFF.toByte())
        ))
        val browser = ArchiveBrowser(TarInspector(tar, ArchiveType.TAR), tar.absolutePath)

        val result = browser.browse(entryPath = "", offset = 0, limit = 100)

        assertTrue(result is BrowseResult.Complete)
        val items = result.items
        val dirIndex = items.indexOfFirst { it is BrowsableItem.ArchiveDirectory }
        val fileIndex = items.indexOfFirst { it is BrowsableItem.ArchiveFileEntry }
        assertTrue("directories must appear before files", dirIndex < fileIndex)
    }

    @Test
    fun `TAR archive browse subdirectory returns only direct children`() = runTest {
        val tar = createTar("nested.tar", listOf(
            "readme.txt" to "root file".toByteArray(),
            "images/" to byteArrayOf(),
            "images/photo.jpg" to "image data".toByteArray(),
            "images/thumbs/" to byteArrayOf(),
            "images/thumbs/small.jpg" to "thumb".toByteArray()
        ))
        val browser = ArchiveBrowser(TarInspector(tar, ArchiveType.TAR), tar.absolutePath)

        val result = browser.browse(entryPath = "images", offset = 0, limit = 100)

        assertTrue(result is BrowseResult.Complete)
        val names = result.items.map { it.name }
        assertTrue("photo.jpg is a direct child", names.contains("photo.jpg"))
        assertTrue("thumbs/ is a direct child dir", names.contains("thumbs"))
        assertFalse("small.jpg is nested, not a direct child", names.contains("small.jpg"))
        assertFalse("readme.txt is at root, not in images/", names.contains("readme.txt"))
    }

    @Test
    fun `TAR archive without explicit directory entry synthesizes implicit directory`() = runTest {
        // Only "images/photo.jpg" is stored — no explicit "images/" entry, unlike the other
        // tests in this file which always add one. ArchiveBrowser must still surface "images"
        // as a navigable directory at root.
        val tar = createTar("implicit-dir.tar", listOf(
            "images/photo.jpg" to "image data".toByteArray()
        ))
        val browser = ArchiveBrowser(TarInspector(tar, ArchiveType.TAR), tar.absolutePath)

        val result = browser.browse(entryPath = "", offset = 0, limit = 100)

        assertTrue(result is BrowseResult.Complete)
        val images = result.items.firstOrNull { it.name == "images" }
        assertTrue("images must be synthesized as a directory", images is BrowsableItem.ArchiveDirectory)

        val subResult = browser.browse(entryPath = "images", offset = 0, limit = 100)
        assertTrue(subResult is BrowseResult.Complete)
        assertTrue(subResult.items.any { it.name == "photo.jpg" })
    }

    @Test
    fun `TAR_GZ archive browse root returns all root entries as Complete`() = runTest {
        val tarGz = createTarGz("archive.tar.gz", listOf(
            "readme.txt" to "hello gz".toByteArray(),
            "data.bin" to byteArrayOf(0x01, 0x02)
        ))
        val browser = ArchiveBrowser(TarInspector(tarGz, ArchiveType.TAR_GZ), tarGz.absolutePath)

        val result = browser.browse(entryPath = "", offset = 0, limit = 100)

        assertTrue(result is BrowseResult.Complete)
        assertEquals(2, result.items.size)
        val names = result.items.map { it.name }
        assertTrue(names.contains("readme.txt"))
        assertTrue(names.contains("data.bin"))
    }

    @Test
    fun `TAR_BZ2 archive browse root returns all root entries as Complete`() = runTest {
        val tarBz2 = createTarBz2("archive.tar.bz2", listOf(
            "readme.txt" to "hello bz2".toByteArray(),
            "doc.pdf" to byteArrayOf(0x25, 0x50, 0x44, 0x46)
        ))
        val browser = ArchiveBrowser(TarInspector(tarBz2, ArchiveType.TAR_BZ2), tarBz2.absolutePath)

        val result = browser.browse(entryPath = "", offset = 0, limit = 100)

        assertTrue(result is BrowseResult.Complete)
        assertEquals(2, result.items.size)
        val names = result.items.map { it.name }
        assertTrue(names.contains("readme.txt"))
        assertTrue(names.contains("doc.pdf"))
    }

    @Test
    fun `GZIP archive browse root returns single entry as Complete`() = runTest {
        val gz = createGzip("readme.txt.gz", "hello gzip".toByteArray())
        val browser = ArchiveBrowser(GzipInspector(gz), gz.absolutePath)

        val result = browser.browse(entryPath = "", offset = 0, limit = 100)

        assertTrue(result is BrowseResult.Complete)
        assertEquals(1, result.items.size)
        val item = result.items.first()
        assertTrue(item is BrowsableItem.ArchiveFileEntry)
        assertEquals("readme.txt", item.name)
    }

    private fun createTar(name: String, entries: List<Pair<String, ByteArray>>): File {
        val file = tempFolder.newFile(name)
        TarArchiveOutputStream(FileOutputStream(file)).use { tar ->
            for ((path, data) in entries) {
                val entry = TarArchiveEntry(path)
                entry.size = data.size.toLong()
                tar.putArchiveEntry(entry)
                tar.write(data)
                tar.closeArchiveEntry()
            }
        }
        return file
    }

    private fun createTarGz(name: String, entries: List<Pair<String, ByteArray>>): File {
        val file = tempFolder.newFile(name)
        TarArchiveOutputStream(GzipCompressorOutputStream(FileOutputStream(file))).use { tar ->
            for ((path, data) in entries) {
                val entry = TarArchiveEntry(path)
                entry.size = data.size.toLong()
                tar.putArchiveEntry(entry)
                tar.write(data)
                tar.closeArchiveEntry()
            }
        }
        return file
    }

    private fun createTarBz2(name: String, entries: List<Pair<String, ByteArray>>): File {
        val file = tempFolder.newFile(name)
        TarArchiveOutputStream(BZip2CompressorOutputStream(FileOutputStream(file))).use { tar ->
            for ((path, data) in entries) {
                val entry = TarArchiveEntry(path)
                entry.size = data.size.toLong()
                tar.putArchiveEntry(entry)
                tar.write(data)
                tar.closeArchiveEntry()
            }
        }
        return file
    }

    private fun createGzip(name: String, content: ByteArray): File {
        val file = tempFolder.newFile(name)
        GzipCompressorOutputStream(FileOutputStream(file)).use { gz ->
            gz.write(content)
        }
        return file
    }
}
