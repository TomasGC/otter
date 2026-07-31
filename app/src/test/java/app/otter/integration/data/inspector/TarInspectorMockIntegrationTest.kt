package app.otter.integration.data.inspector

import app.otter.data.inspector.TarInspector
import app.otter.domain.inspector.ArchiveType
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

class TarInspectorMockIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // --- TAR ---

    @Test
    fun `TAR - entries count matches actual file count`() = runTest {
        val file = createTar(
            "archive.tar",
            listOf("alpha.txt" to "aaa", "beta.txt" to "bb", "gamma.txt" to "c")
        )
        val inspector = TarInspector(file, ArchiveType.TAR)

        assertEquals(3, inspector.countEntries())
        inspector.close()
    }

    @Test
    fun `TAR - entries preserve exact paths including nested directories`() {
        val file = createTar(
            "nested.tar",
            listOf("dir/" to null, "dir/nested.txt" to "content")
        )
        val inspector = TarInspector(file, ArchiveType.TAR)

        val entries = inspector.entries().toList()

        assertEquals(2, entries.size)
        val dirEntry = entries.first { it.path == "dir/" }
        val fileEntry = entries.first { it.path == "dir/nested.txt" }
        assertTrue(dirEntry.isDirectory)
        assertFalse(fileEntry.isDirectory)
        inspector.close()
    }

    @Test
    fun `TAR - entries include correct sizeBytes`() {
        val content = "known content"
        val file = createTar("sized.tar", listOf("file.txt" to content))
        val inspector = TarInspector(file, ArchiveType.TAR)

        val entries = inspector.entries().toList()

        assertEquals(content.toByteArray().size.toLong(), entries[0].sizeBytes)
        inspector.close()
    }

    @Test
    fun `TAR - multiple open-close cycles are independent`() = runTest {
        val file = createTar("multi.tar", listOf("a.txt" to "1", "b.txt" to "2"))

        val inspector1 = TarInspector(file, ArchiveType.TAR)
        assertEquals(2, inspector1.countEntries())
        inspector1.close()

        val inspector2 = TarInspector(file, ArchiveType.TAR)
        assertEquals(2, inspector2.countEntries())
        inspector2.close()
    }

    @Test
    fun `TAR - countEntries consistent across multiple calls`() = runTest {
        val file = createTar("consistent.tar", listOf("x.txt" to "x", "y.txt" to "y"))
        val inspector = TarInspector(file, ArchiveType.TAR)

        val first = inspector.countEntries()
        val second = inspector.countEntries()

        assertEquals(first, second)
        inspector.close()
    }

    // --- TAR_GZ ---

    @Test
    fun `TAR_GZ - entries count matches`() = runTest {
        val file = createTarGz("archive.tar.gz", listOf("one.txt" to "1", "two.txt" to "22"))
        val inspector = TarInspector(file, ArchiveType.TAR_GZ)

        assertEquals(2, inspector.countEntries())
        inspector.close()
    }

    @Test
    fun `TAR_GZ - entries correctly indicate directories`() {
        val file = createTarGz("dirs.tar.gz", listOf("mydir/" to null, "mydir/file.txt" to "hi"))
        val inspector = TarInspector(file, ArchiveType.TAR_GZ)

        val entries = inspector.entries().toList()
        val dirEntry = entries.first { it.path == "mydir/" }

        assertTrue(dirEntry.isDirectory)
        inspector.close()
    }

    // --- TAR_BZ2 ---

    @Test
    fun `TAR_BZ2 - entries count matches`() = runTest {
        val file = createTarBz2("archive.tar.bz2", listOf("p.txt" to "pp", "q.txt" to "qq"))
        val inspector = TarInspector(file, ArchiveType.TAR_BZ2)

        assertEquals(2, inspector.countEntries())
        inspector.close()
    }

    @Test
    fun `TAR_BZ2 - entries preserve file content metadata`() {
        val content = "bzip2 payload"
        val file = createTarBz2("meta.tar.bz2", listOf("data/report.txt" to content))
        val inspector = TarInspector(file, ArchiveType.TAR_BZ2)

        val entries = inspector.entries().toList()

        assertEquals(1, entries.size)
        assertEquals("data/report.txt", entries[0].path)
        assertEquals(content.toByteArray().size.toLong(), entries[0].sizeBytes)
        inspector.close()
    }

    // --- Helpers ---

    private fun createTar(name: String, entries: List<Pair<String, String?>>): File {
        val file = tempFolder.newFile(name)
        TarArchiveOutputStream(FileOutputStream(file)).use { tar ->
            writeTarEntries(tar, entries)
        }
        return file
    }

    private fun createTarGz(name: String, entries: List<Pair<String, String?>>): File {
        val file = tempFolder.newFile(name)
        TarArchiveOutputStream(GzipCompressorOutputStream(FileOutputStream(file))).use { tar ->
            writeTarEntries(tar, entries)
        }
        return file
    }

    private fun createTarBz2(name: String, entries: List<Pair<String, String?>>): File {
        val file = tempFolder.newFile(name)
        TarArchiveOutputStream(BZip2CompressorOutputStream(FileOutputStream(file))).use { tar ->
            writeTarEntries(tar, entries)
        }
        return file
    }

    private fun writeTarEntries(tar: TarArchiveOutputStream, entries: List<Pair<String, String?>>) {
        entries.forEach { (entryName, content) ->
            val entry = TarArchiveEntry(entryName)
            val data = content?.toByteArray() ?: ByteArray(0)
            entry.size = data.size.toLong()
            tar.putArchiveEntry(entry)
            tar.write(data)
            tar.closeArchiveEntry()
        }
    }
}
