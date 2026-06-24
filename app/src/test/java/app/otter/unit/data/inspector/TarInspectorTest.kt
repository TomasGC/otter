package app.otter.unit.data.inspector

import app.otter.data.inspector.TarInspector
import app.otter.domain.inspector.ArchiveType
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

class TarInspectorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // --- TAR ---

    @Test
    fun `countEntries returns correct count for TAR`() = runTest {
        val file = createTar("test.tar", listOf("a.txt" to "hello", "b.txt" to "world"))
        val inspector = TarInspector(file, ArchiveType.TAR)

        assertEquals(2, inspector.countEntries())
        inspector.close()
    }

    @Test
    fun `entries returns all entries for TAR`() {
        val file = createTar("test.tar", listOf("a.txt" to "hello", "dir/" to null))
        val inspector = TarInspector(file, ArchiveType.TAR)

        val entries = inspector.entries().toList()

        assertEquals(2, entries.size)
        assertEquals("a.txt", entries[0].path)
        assertFalse(entries[0].isDirectory)
        assertEquals(5L, entries[0].sizeBytes)
        assertEquals("dir/", entries[1].path)
        assertTrue(entries[1].isDirectory)
        inspector.close()
    }

    @Test
    fun `countEntries returns zero for empty TAR`() = runTest {
        val file = createTar("empty.tar", emptyList())
        val inspector = TarInspector(file, ArchiveType.TAR)

        assertEquals(0, inspector.countEntries())
        inspector.close()
    }

    @Test
    fun `isEncrypted always returns false for TAR`() {
        val file = createTar("test.tar", listOf("a.txt" to "x"))
        val inspector = TarInspector(file, ArchiveType.TAR)

        assertFalse(inspector.isEncrypted())
        inspector.close()
    }

    @Test
    fun `getArchiveType returns TAR`() {
        val file = createTar("test.tar", listOf("a.txt" to "x"))
        val inspector = TarInspector(file, ArchiveType.TAR)

        assertEquals(ArchiveType.TAR, inspector.getArchiveType())
        inspector.close()
    }

    @Test
    fun `close is idempotent`() {
        val file = createTar("test.tar", listOf("a.txt" to "x"))
        val inspector = TarInspector(file, ArchiveType.TAR)

        inspector.close()
        inspector.close() // must not throw
    }

    @Test
    fun `entries throws after close`() {
        val file = createTar("test.tar", listOf("a.txt" to "x"))
        val inspector = TarInspector(file, ArchiveType.TAR)
        inspector.close()

        assertThrows(IllegalStateException::class.java) {
            inspector.entries()
        }
    }

    @Test
    fun `countEntries throws after close`() {
        val file = createTar("test.tar", listOf("a.txt" to "x"))
        val inspector = TarInspector(file, ArchiveType.TAR)
        inspector.close()

        assertThrows(IllegalStateException::class.java) {
            runTest {
                inspector.countEntries()
            }
        }
    }

    // --- TAR_GZ ---

    @Test
    fun `countEntries returns correct count for TAR_GZ`() = runTest {
        val file = createTarGz("test.tar.gz", listOf("a.txt" to "hello"))
        val inspector = TarInspector(file, ArchiveType.TAR_GZ)

        assertEquals(1, inspector.countEntries())
        inspector.close()
    }

    @Test
    fun `getArchiveType returns TAR_GZ`() {
        val file = createTarGz("test.tar.gz", listOf("a.txt" to "x"))
        val inspector = TarInspector(file, ArchiveType.TAR_GZ)

        assertEquals(ArchiveType.TAR_GZ, inspector.getArchiveType())
        inspector.close()
    }

    // --- TAR_BZ2 ---

    @Test
    fun `countEntries returns correct count for TAR_BZ2`() = runTest {
        val file = createTarBz2("test.tar.bz2", listOf("a.txt" to "hello"))
        val inspector = TarInspector(file, ArchiveType.TAR_BZ2)

        assertEquals(1, inspector.countEntries())
        inspector.close()
    }

    @Test
    fun `getArchiveType returns TAR_BZ2`() {
        val file = createTarBz2("test.tar.bz2", listOf("a.txt" to "x"))
        val inspector = TarInspector(file, ArchiveType.TAR_BZ2)

        assertEquals(ArchiveType.TAR_BZ2, inspector.getArchiveType())
        inspector.close()
    }

    // --- Helpers ---

    private fun createTar(name: String, entries: List<Pair<String, String?>>): File {
        val file = tempFolder.newFile(name)
        TarArchiveOutputStream(FileOutputStream(file)).use { tar ->
            entries.forEach { (entryName, content) ->
                val entry = TarArchiveEntry(entryName)
                val data = content?.toByteArray() ?: ByteArray(0)
                entry.size = data.size.toLong()
                tar.putArchiveEntry(entry)
                tar.write(data)
                tar.closeArchiveEntry()
            }
        }
        return file
    }

    private fun createTarGz(name: String, entries: List<Pair<String, String?>>): File {
        val file = tempFolder.newFile(name)
        TarArchiveOutputStream(GzipCompressorOutputStream(FileOutputStream(file))).use { tar ->
            entries.forEach { (entryName, content) ->
                val entry = TarArchiveEntry(entryName)
                val data = content?.toByteArray() ?: ByteArray(0)
                entry.size = data.size.toLong()
                tar.putArchiveEntry(entry)
                tar.write(data)
                tar.closeArchiveEntry()
            }
        }
        return file
    }

    private fun createTarBz2(name: String, entries: List<Pair<String, String?>>): File {
        val file = tempFolder.newFile(name)
        TarArchiveOutputStream(BZip2CompressorOutputStream(FileOutputStream(file))).use { tar ->
            entries.forEach { (entryName, content) ->
                val entry = TarArchiveEntry(entryName)
                val data = content?.toByteArray() ?: ByteArray(0)
                entry.size = data.size.toLong()
                tar.putArchiveEntry(entry)
                tar.write(data)
                tar.closeArchiveEntry()
            }
        }
        return file
    }
}
