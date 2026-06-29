package app.otter.integration.data.inspector

import app.otter.data.inspector.GzipInspector
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

class GzipInspectorIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `countEntries returns 1 for any gz file`() = runTest {
        val file = createGzipFile("anything.gz", "some random content")
        val inspector = GzipInspector(file)

        assertEquals(1, inspector.countEntries())
        inspector.close()
    }

    @Test
    fun `entries returns single entry with inner name`() {
        val file = createGzipFile("data.gz", "hello world")
        val inspector = GzipInspector(file)

        val entries = inspector.entries().toList()

        assertEquals(1, entries.size)
        assertEquals("data", entries[0].path)
        assertFalse(entries[0].isDirectory)
        inspector.close()
    }

    @Test
    fun `entries compressedSize equals gz file size`() {
        val file = createGzipFile("report.gz", "compressed payload data")
        val inspector = GzipInspector(file)

        val entries = inspector.entries().toList()

        assertEquals(file.length(), entries[0].compressedSize)
        inspector.close()
    }

    @Test
    fun `entries sizeBytes is 0`() {
        val file = createGzipFile("output.gz", "uncompressed size is not tracked in gzip header")
        val inspector = GzipInspector(file)

        val entries = inspector.entries().toList()

        assertEquals(0L, entries[0].sizeBytes)
        inspector.close()
    }

    @Test
    fun `countEntries consistent on repeated calls`() = runTest {
        val file = createGzipFile("stable.gz", "content")
        val inspector = GzipInspector(file)

        val first = inspector.countEntries()
        val second = inspector.countEntries()

        assertEquals(first, second)
        inspector.close()
    }

    @Test
    fun `entries on empty content gz`() {
        val file = createGzipFile("empty.gz", "")
        val inspector = GzipInspector(file)

        val entries = inspector.entries().toList()

        assertEquals(1, entries.size)
        assertEquals("empty", entries[0].path)
        inspector.close()
    }

    @Test
    fun `entries with deep filename`() {
        val file = createGzipFile("archive.log.gz", "log content here")
        val inspector = GzipInspector(file)

        val entries = inspector.entries().toList()

        assertEquals("archive.log", entries[0].path)
        inspector.close()
    }

    @Test
    fun `close then reopen returns correct result`() = runTest {
        val file = createGzipFile("reopen.gz", "data")

        val inspector1 = GzipInspector(file)
        assertEquals(1, inspector1.countEntries())
        inspector1.close()

        val inspector2 = GzipInspector(file)
        val entries = inspector2.entries().toList()
        assertEquals(1, entries.size)
        assertEquals("reopen", entries[0].path)
        inspector2.close()
    }

    // --- Helper ---

    private fun createGzipFile(name: String, content: String): File {
        val file = tempFolder.newFile(name)
        GzipCompressorOutputStream(FileOutputStream(file)).use { gz ->
            gz.write(content.toByteArray())
        }
        return file
    }
}
