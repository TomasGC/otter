package app.otter.unit.data.inspector

import app.otter.data.inspector.GzipInspector
import app.otter.domain.inspector.ArchiveType
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.FileOutputStream

class GzipInspectorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `countEntries always returns 1`() = runTest {
        val file = createGzipFile("archive.gz", "hello world")
        val inspector = GzipInspector(file)

        assertEquals(1, inspector.countEntries())
        inspector.close()
    }

    @Test
    fun `entries returns single entry with inner filename derived from gz extension`() {
        val file = createGzipFile("document.txt.gz", "content")
        val inspector = GzipInspector(file)

        val entries = inspector.entries().toList()

        assertEquals(1, entries.size)
        assertEquals("document.txt", entries[0].path)
        assertFalse(entries[0].isDirectory)
        inspector.close()
    }

    @Test
    fun `entries returns single entry with inner filename derived from gzip extension`() {
        val file = createGzipFile("data.csv.gzip", "a,b,c")
        val inspector = GzipInspector(file)

        val entries = inspector.entries().toList()

        assertEquals(1, entries.size)
        assertEquals("data.csv", entries[0].path)
        inspector.close()
    }

    @Test
    fun `entries uses file name as path when no gz extension`() {
        val file = createGzipFile("noextension", "data")
        val inspector = GzipInspector(file)

        val entries = inspector.entries().toList()

        assertEquals("noextension", entries[0].path)
        inspector.close()
    }

    @Test
    fun `entries compressedSize equals file length`() {
        val file = createGzipFile("archive.gz", "hello world content here")
        val inspector = GzipInspector(file)

        val entries = inspector.entries().toList()

        assertEquals(file.length(), entries[0].compressedSize)
        inspector.close()
    }

    @Test
    fun `isEncrypted always returns false`() {
        val file = createGzipFile("archive.gz", "x")
        val inspector = GzipInspector(file)

        assertFalse(inspector.isEncrypted())
        inspector.close()
    }

    @Test
    fun `getArchiveType returns GZIP`() {
        val file = createGzipFile("archive.gz", "x")
        val inspector = GzipInspector(file)

        assertEquals(ArchiveType.GZIP, inspector.getArchiveType())
        inspector.close()
    }

    @Test
    fun `close is idempotent`() {
        val file = createGzipFile("archive.gz", "x")
        val inspector = GzipInspector(file)

        inspector.close()
        inspector.close() // must not throw
    }

    @Test
    fun `entries throws after close`() {
        val file = createGzipFile("archive.gz", "x")
        val inspector = GzipInspector(file)
        inspector.close()

        assertThrows(IllegalStateException::class.java) {
            inspector.entries()
        }
    }

    @Test
    fun `countEntries throws after close`() = runTest {
        val file = createGzipFile("archive.gz", "x")
        val inspector = GzipInspector(file)
        inspector.close()

        var exceptionThrown = false
        try {
            inspector.countEntries()
        } catch (e: IllegalStateException) {
            exceptionThrown = true
        }
        assertTrue(exceptionThrown)
    }

    @Test
    fun `entries lastModified equals file lastModified`() {
        val file = createGzipFile("archive.gz", "content")
        val expectedLastModified = file.lastModified()
        val inspector = GzipInspector(file)

        val entries = inspector.entries().toList()

        assertEquals(expectedLastModified, entries[0].lastModified)
        inspector.close()
    }

    @Test
    fun `entries handles uppercase GZ extension`() {
        val file = createGzipFile("ARCHIVE.GZ", "content")
        val inspector = GzipInspector(file)

        val entries = inspector.entries().toList()

        assertEquals("ARCHIVE", entries[0].path)
        inspector.close()
    }

    @Test
    fun `entries sizeBytes is zero`() {
        val file = createGzipFile("data.gz", "some content here")
        val inspector = GzipInspector(file)

        val entries = inspector.entries().toList()

        assertEquals(0L, entries[0].sizeBytes)
        inspector.close()
    }

    private fun createGzipFile(name: String, content: String): java.io.File {
        val file = tempFolder.newFile(name)
        GzipCompressorOutputStream(FileOutputStream(file)).use { gz ->
            gz.write(content.toByteArray())
        }
        return file
    }
}
