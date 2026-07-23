package app.otter.data.inspector

import app.otter.domain.inspector.ArchiveType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class InspectorRealIntegrationTest {

    private val archivesDir: File = File(
        System.getProperty("archives.dir")
            ?: error("archives.dir system property not set (should be set by Gradle)")
    )

    // --- ZipInspector ---

    @Test
    fun `ZipInspector entries from real archive returns non-empty list`() = runTest {
        val inspector = ZipInspector(archivesDir.resolve("test_archive.zip"))
        val entries = inspector.entries().toList()
        assertTrue(entries.isNotEmpty())
        inspector.close()
    }

    @Test
    fun `ZipInspector countEntries matches entries list size`() = runTest {
        val inspector = ZipInspector(archivesDir.resolve("test_archive.zip"))
        assertEquals(inspector.countEntries(), inspector.entries().count())
        inspector.close()
    }

    @Test
    fun `ZipInspector entries have non-empty paths`() = runTest {
        val inspector = ZipInspector(archivesDir.resolve("test_archive.zip"))
        val entries = inspector.entries().toList()
        assertTrue(entries.all { it.path.isNotEmpty() })
        inspector.close()
    }

    // --- RpaInspector ---

    @Test
    fun `RpaInspector entries from real archive returns non-empty list`() = runTest {
        val inspector = RpaInspector(FileRpaSource(archivesDir.resolve("test_archive.rpa")))
        val entries = inspector.entries().toList()
        assertTrue(entries.isNotEmpty())
        inspector.close()
    }

    @Test
    fun `RpaInspector countEntries matches entries list size`() = runTest {
        val inspector = RpaInspector(FileRpaSource(archivesDir.resolve("test_archive.rpa")))
        assertEquals(inspector.countEntries(), inspector.entries().count())
        inspector.close()
    }

    @Test
    fun `RpaInspector entries have non-empty paths and no directories`() {
        val inspector = RpaInspector(FileRpaSource(archivesDir.resolve("test_archive.rpa")))
        val entries = inspector.entries().toList()
        assertTrue(entries.all { it.path.isNotEmpty() })
        assertTrue(entries.none { it.isDirectory })
        inspector.close()
    }

    // --- TarInspector TAR_BZ2 ---

    @Test
    fun `TarInspector TAR_BZ2 entries from real archive returns non-empty list`() = runTest {
        val inspector = TarInspector(archivesDir.resolve("test_archive.tar.bz2"), ArchiveType.TAR_BZ2)
        val entries = inspector.entries().toList()
        assertTrue(entries.isNotEmpty())
        inspector.close()
    }

    @Test
    fun `TarInspector TAR_BZ2 countEntries matches entries list size`() = runTest {
        val inspector = TarInspector(archivesDir.resolve("test_archive.tar.bz2"), ArchiveType.TAR_BZ2)
        assertEquals(inspector.countEntries(), inspector.entries().count())
        inspector.close()
    }

    // --- GzipInspector ---

    @Test
    fun `GzipInspector entries from real gz returns single entry`() = runTest {
        val file = archivesDir.resolve("test_archive.gz")
        val inspector = GzipInspector(file)
        val entries = inspector.entries().toList()
        assertEquals(1, entries.size)
        assertFalse(entries[0].isDirectory)
        assertEquals("test_archive", entries[0].path)
        inspector.close()
    }

    @Test
    fun `GzipInspector countEntries returns 1 for real gz`() = runTest {
        val inspector = GzipInspector(archivesDir.resolve("test_archive.gz"))
        assertEquals(1, inspector.countEntries())
        inspector.close()
    }

    // --- Corrupted real archives (no mocks — the real parser against real corrupted bytes) ---

    @Test
    fun `ZipInspector on real corrupted archive does not crash the process`() = runTest {
        val inspector = ZipInspector(archivesDir.resolve("corrupted_test_archive.zip"))
        runCatching { inspector.entries().toList() }
        inspector.close()
    }

    @Test
    fun `TarInspector TAR_BZ2 on real corrupted archive does not crash the process`() = runTest {
        val inspector = TarInspector(archivesDir.resolve("corrupted_test_archive.tar.bz2"), ArchiveType.TAR_BZ2)
        runCatching { inspector.entries().toList() }
        inspector.close()
    }

    @Test
    fun `GzipInspector on real corrupted archive does not crash the process`() = runTest {
        val inspector = GzipInspector(archivesDir.resolve("corrupted_test_archive.gz"))
        runCatching { inspector.entries().toList() }
        inspector.close()
    }
}
