package app.otter.data.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RealZipFileReaderUnitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createZip(vararg entryNames: String): java.io.File {
        val file = tempFolder.newFile("test.zip")
        ZipOutputStream(file.outputStream()).use { zos ->
            entryNames.forEach { name ->
                zos.putNextEntry(ZipEntry(name))
                zos.write("content".toByteArray())
                zos.closeEntry()
            }
        }
        return file
    }

    // --- Bug regression: countFiles() must NOT close the ZipFile ---

    @Test
    fun `countFiles then getEntries on same instance should not throw`() {
        val file = createZip("a.txt", "b.txt", "sub/c.txt")
        val reader = RealZipFileReader(file)
        reader.use {
            val count = reader.countFiles()
            assertEquals(3, count)
            // Before fix: throws IllegalStateException("zip file closed")
            val entries = reader.getEntries().toList()
            assertEquals(3, entries.size)
        }
    }

    @Test
    fun `countFiles then getInputStream on same instance should not throw`() {
        val file = createZip("hello.txt")
        val reader = RealZipFileReader(file)
        reader.use {
            reader.countFiles()
            val entry = reader.getEntries().first()
            val bytes = reader.getInputStream(entry).readBytes()
            assertFalse(bytes.isEmpty())
        }
    }

    // --- Correctness: countFiles excludes directories ---

    @Test
    fun `countFiles should exclude directory entries`() {
        val file = tempFolder.newFile("dirs.zip")
        ZipOutputStream(file.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("folder/"))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("folder/file.txt"))
            zos.write("x".toByteArray())
            zos.closeEntry()
        }
        RealZipFileReader(file).use { reader ->
            assertEquals(1, reader.countFiles())
        }
    }

    @Test
    fun `countFiles on empty zip should return zero`() {
        val file = tempFolder.newFile("empty.zip")
        ZipOutputStream(file.outputStream()).use { /* no entries */ }
        RealZipFileReader(file).use { reader ->
            assertEquals(0, reader.countFiles())
        }
    }

    @Test
    fun `getEntries should match countFiles result`() {
        val file = createZip("x.txt", "y.txt")
        RealZipFileReader(file).use { reader ->
            val count = reader.countFiles()
            val entries = reader.getEntries().toList()
            assertEquals(count, entries.size)
        }
    }
}
