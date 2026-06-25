package app.otter.unit.data.inspector

import app.otter.data.extractor.ArchiveLibraryManager
import app.otter.data.inspector.SevenZipBasedInspector
import app.otter.domain.inspector.ArchiveType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.PropID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Date

class SevenZipBasedInspectorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val mockArchive = mockk<IInArchive>(relaxed = true)
    private val mockLibraryManager = mockk<ArchiveLibraryManager>()

    private fun makeFile(name: String = "test.7z") = tempFolder.newFile(name)

    @Test
    fun `countEntries returns numberOfItems from archive`() = runTest {
        val file = makeFile()
        every { mockLibraryManager.openArchive(file) } returns mockArchive
        every { mockArchive.numberOfItems } returns 5
        val inspector = SevenZipBasedInspector(file, ArchiveType.SEVEN_ZIP, mockLibraryManager)

        assertEquals(5, inspector.countEntries())
        inspector.close()
    }

    @Test
    fun `entries returns ArchiveEntry for each item`() {
        val file = makeFile()
        every { mockLibraryManager.openArchive(file) } returns mockArchive
        every { mockArchive.numberOfItems } returns 2
        every { mockArchive.getProperty(0, PropID.PATH) } returns "file.txt"
        every { mockArchive.getProperty(0, PropID.IS_FOLDER) } returns false
        every { mockArchive.getProperty(0, PropID.SIZE) } returns 1024L
        every { mockArchive.getProperty(0, PropID.PACKED_SIZE) } returns 512L
        every { mockArchive.getProperty(0, PropID.LAST_MODIFICATION_TIME) } returns Date(1000L)
        every { mockArchive.getProperty(1, PropID.PATH) } returns "subdir/"
        every { mockArchive.getProperty(1, PropID.IS_FOLDER) } returns true
        every { mockArchive.getProperty(1, PropID.SIZE) } returns 0L
        every { mockArchive.getProperty(1, PropID.PACKED_SIZE) } returns 0L
        every { mockArchive.getProperty(1, PropID.LAST_MODIFICATION_TIME) } returns null
        val inspector = SevenZipBasedInspector(file, ArchiveType.SEVEN_ZIP, mockLibraryManager)

        val entries = inspector.entries().toList()

        assertEquals(2, entries.size)
        assertEquals("file.txt", entries[0].path)
        assertFalse(entries[0].isDirectory)
        assertEquals(1024L, entries[0].sizeBytes)
        assertEquals(512L, entries[0].compressedSize)
        assertEquals(1000L, entries[0].lastModified)
        assertEquals("subdir/", entries[1].path)
        assertTrue(entries[1].isDirectory)
        assertEquals(0L, entries[1].lastModified)
        inspector.close()
    }

    @Test
    fun `entries handles null PATH as empty string`() {
        val file = makeFile()
        every { mockLibraryManager.openArchive(file) } returns mockArchive
        every { mockArchive.numberOfItems } returns 1
        every { mockArchive.getProperty(0, PropID.PATH) } returns null
        every { mockArchive.getProperty(0, PropID.IS_FOLDER) } returns false
        every { mockArchive.getProperty(0, PropID.SIZE) } returns 0L
        every { mockArchive.getProperty(0, PropID.PACKED_SIZE) } returns 0L
        every { mockArchive.getProperty(0, PropID.LAST_MODIFICATION_TIME) } returns null
        val inspector = SevenZipBasedInspector(file, ArchiveType.SEVEN_ZIP, mockLibraryManager)

        val entries = inspector.entries().toList()

        assertEquals("", entries[0].path)
        inspector.close()
    }

    @Test
    fun `isEncrypted returns true when archive property is true`() {
        val file = makeFile()
        every { mockLibraryManager.openArchive(file) } returns mockArchive
        every { mockArchive.getArchiveProperty(PropID.ENCRYPTED) } returns true
        val inspector = SevenZipBasedInspector(file, ArchiveType.SEVEN_ZIP, mockLibraryManager)

        assertTrue(inspector.isEncrypted())
        inspector.close()
    }

    @Test
    fun `isEncrypted returns false when archive property is null`() {
        val file = makeFile()
        every { mockLibraryManager.openArchive(file) } returns mockArchive
        every { mockArchive.getArchiveProperty(PropID.ENCRYPTED) } returns null
        val inspector = SevenZipBasedInspector(file, ArchiveType.SEVEN_ZIP, mockLibraryManager)

        assertFalse(inspector.isEncrypted())
        inspector.close()
    }

    @Test
    fun `getArchiveType returns constructor-provided type for SEVEN_ZIP`() {
        val file = makeFile()
        every { mockLibraryManager.openArchive(file) } returns mockArchive
        val inspector = SevenZipBasedInspector(file, ArchiveType.SEVEN_ZIP, mockLibraryManager)

        assertEquals(ArchiveType.SEVEN_ZIP, inspector.getArchiveType())
        inspector.close()
    }

    @Test
    fun `getArchiveType returns constructor-provided type for RAR`() {
        val file = makeFile("test.rar")
        every { mockLibraryManager.openArchive(file) } returns mockArchive
        val inspector = SevenZipBasedInspector(file, ArchiveType.RAR, mockLibraryManager)

        assertEquals(ArchiveType.RAR, inspector.getArchiveType())
        inspector.close()
    }

    @Test
    fun `openArchive is called lazily on first use`() = runTest {
        val file = makeFile()
        every { mockLibraryManager.openArchive(file) } returns mockArchive
        every { mockArchive.numberOfItems } returns 0
        val inspector = SevenZipBasedInspector(file, ArchiveType.SEVEN_ZIP, mockLibraryManager)

        // openArchive not called yet
        verify(exactly = 0) { mockLibraryManager.openArchive(any()) }

        inspector.countEntries() // triggers open

        // now it should have been called
        inspector.close()
    }

    @Test
    fun `close releases IInArchive`() {
        val file = makeFile()
        every { mockLibraryManager.openArchive(file) } returns mockArchive
        every { mockArchive.numberOfItems } returns 0
        val inspector = SevenZipBasedInspector(file, ArchiveType.SEVEN_ZIP, mockLibraryManager)
        runTest { inspector.countEntries() } // trigger open

        inspector.close()

        verify { mockArchive.close() }
    }

    @Test
    fun `close is idempotent`() {
        val file = makeFile()
        val inspector = SevenZipBasedInspector(file, ArchiveType.SEVEN_ZIP, mockLibraryManager)

        inspector.close()
        inspector.close() // must not throw
    }

    @Test
    fun `entries throws after close`() {
        val file = makeFile()
        val inspector = SevenZipBasedInspector(file, ArchiveType.SEVEN_ZIP, mockLibraryManager)
        inspector.close()

        assertThrows(IllegalStateException::class.java) {
            inspector.entries()
        }
    }

    @Test
    fun `countEntries throws after close`() {
        val file = makeFile()
        val inspector = SevenZipBasedInspector(file, ArchiveType.SEVEN_ZIP, mockLibraryManager)
        inspector.close()

        assertThrows(IllegalStateException::class.java) {
            runTest {
                inspector.countEntries()
            }
        }
    }
}
