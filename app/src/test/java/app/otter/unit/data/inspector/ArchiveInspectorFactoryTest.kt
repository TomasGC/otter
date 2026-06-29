package app.otter.data.inspector

import app.otter.data.extractor.ArchiveLibraryManager
import app.otter.domain.inspector.ArchiveType
import io.mockk.every
import io.mockk.mockk
import net.sf.sevenzipjbinding.IInArchive
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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveInspectorFactoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val mockArchive = mockk<IInArchive>(relaxed = true)
    private val mockLibraryManager = mockk<ArchiveLibraryManager>(relaxed = true)

    @Test
    fun `create should detect ZIP by extension`() {
        val zipFile = createTestZip("test.zip")
        val factory = ArchiveInspectorFactory(mockLibraryManager)

        val result = factory.create(zipFile)

        assertTrue(result.isSuccess)
        result.onSuccess { inspector ->
            assertEquals(ArchiveType.ZIP, inspector.getArchiveType())
            inspector.close()
        }
    }

    @Test
    fun `create should detect ZIP by magic bytes even with wrong extension`() {
        val zipFile = createTestZip("fake.txt")
        val factory = ArchiveInspectorFactory(mockLibraryManager)

        val result = factory.create(zipFile)

        assertTrue(result.isSuccess)
        result.onSuccess { inspector ->
            assertEquals(ArchiveType.ZIP, inspector.getArchiveType())
            inspector.close()
        }
    }

    @Test
    fun `create should detect RAR format and return SevenZipBasedInspector`() {
        val rarFile = tempFolder.newFile("test.rar")
        rarFile.writeBytes(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00))
        every { mockLibraryManager.openArchive(rarFile) } returns mockArchive
        val factory = ArchiveInspectorFactory(mockLibraryManager)

        val result = factory.create(rarFile)

        assertTrue(result.isSuccess)
        result.onSuccess { inspector ->
            assertEquals(ArchiveType.RAR, inspector.getArchiveType())
            inspector.close()
        }
    }

    @Test
    fun `create should detect 7z format and return SevenZipBasedInspector`() {
        val sevenZipFile = tempFolder.newFile("test.7z")
        sevenZipFile.writeBytes(byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C))
        every { mockLibraryManager.openArchive(sevenZipFile) } returns mockArchive
        val factory = ArchiveInspectorFactory(mockLibraryManager)

        val result = factory.create(sevenZipFile)

        assertTrue(result.isSuccess)
        result.onSuccess { inspector ->
            assertEquals(ArchiveType.SEVEN_ZIP, inspector.getArchiveType())
            inspector.close()
        }
    }

    @Test
    fun `create should detect TAR format by extension and return TarInspector`() {
        val tarFile = createTestTar("test.tar")
        val factory = ArchiveInspectorFactory(mockLibraryManager)

        val result = factory.create(tarFile)

        assertTrue(result.isSuccess)
        result.onSuccess { inspector ->
            assertEquals(ArchiveType.TAR, inspector.getArchiveType())
            inspector.close()
        }
    }

    @Test
    fun `create should detect TAR_GZ format and return TarInspector`() {
        val tarGzFile = createTestTarGz("test.tar.gz")
        val factory = ArchiveInspectorFactory(mockLibraryManager)

        val result = factory.create(tarGzFile)

        assertTrue(result.isSuccess)
        result.onSuccess { inspector ->
            assertEquals(ArchiveType.TAR_GZ, inspector.getArchiveType())
            inspector.close()
        }
    }

    @Test
    fun `create should detect GZIP by extension and return GzipInspector`() {
        val gzFile = tempFolder.newFile("test.gz")
        gzFile.writeBytes(byteArrayOf(0x1F, 0x8B.toByte(), 0x08, 0x00))
        val factory = ArchiveInspectorFactory(mockLibraryManager)

        val result = factory.create(gzFile)

        assertTrue(result.isSuccess)
        result.onSuccess { inspector ->
            assertEquals(ArchiveType.GZIP, inspector.getArchiveType())
            inspector.close()
        }
    }

    @Test
    fun `create should detect GZIP by magic bytes when no extension`() {
        val gzFile = tempFolder.newFile("data.bin")
        gzFile.writeBytes(byteArrayOf(0x1F, 0x8B.toByte(), 0x08, 0x00))
        val factory = ArchiveInspectorFactory(mockLibraryManager)

        val result = factory.create(gzFile)

        assertTrue(result.isSuccess)
        result.onSuccess { inspector ->
            assertEquals(ArchiveType.GZIP, inspector.getArchiveType())
            inspector.close()
        }
    }

    @Test
    fun `create should throw IllegalArgumentException for unknown format`() {
        val unknownFile = tempFolder.newFile("test.unknown")
        unknownFile.writeText("plain text")
        val factory = ArchiveInspectorFactory(mockLibraryManager)

        val result = factory.create(unknownFile)

        assertTrue(result.isFailure)
        result.onFailure { exception ->
            assertTrue(exception is IllegalArgumentException)
            assertTrue(exception.message?.contains("Could not detect archive format") == true)
        }
    }

    @Test
    fun `create should throw IllegalArgumentException for non-existent file`() {
        val nonExistentFile = File(tempFolder.root, "does-not-exist.zip")
        val factory = ArchiveInspectorFactory(mockLibraryManager)

        val result = factory.create(nonExistentFile)

        assertTrue(result.isFailure)
        result.onFailure { exception ->
            assertTrue(exception is IllegalArgumentException)
            assertTrue(exception.message?.contains("File does not exist") == true)
        }
    }

    @Test
    fun `create should throw IllegalArgumentException for directory`() {
        val directory = tempFolder.newFolder("test-dir")
        val factory = ArchiveInspectorFactory(mockLibraryManager)

        val result = factory.create(directory)

        assertTrue(result.isFailure)
        result.onFailure { exception ->
            assertTrue(exception is IllegalArgumentException)
            assertTrue(exception.message?.contains("not a file") == true)
        }
    }

    @Test
    fun `create should detect TAR_BZ2 by tar dot bz2 extension`() {
        val tarBz2File = createTestTarBz2("test.tar.bz2")
        val factory = ArchiveInspectorFactory(mockLibraryManager)

        val result = factory.create(tarBz2File)

        assertTrue(result.isSuccess)
        result.onSuccess { inspector ->
            assertEquals(ArchiveType.TAR_BZ2, inspector.getArchiveType())
            inspector.close()
        }
    }

    @Test
    fun `create should detect TAR_BZ2 by tbz2 extension`() {
        val tarBz2File = createTestTarBz2("archive.tbz2")
        val factory = ArchiveInspectorFactory(mockLibraryManager)

        val result = factory.create(tarBz2File)

        assertTrue(result.isSuccess)
        result.onSuccess { inspector ->
            assertEquals(ArchiveType.TAR_BZ2, inspector.getArchiveType())
            inspector.close()
        }
    }

    @Test
    fun `create should detect TAR_GZ by tgz extension`() {
        val tgzFile = createTestTarGz("archive.tgz")
        val factory = ArchiveInspectorFactory(mockLibraryManager)

        val result = factory.create(tgzFile)

        assertTrue(result.isSuccess)
        result.onSuccess { inspector ->
            assertEquals(ArchiveType.TAR_GZ, inspector.getArchiveType())
            inspector.close()
        }
    }

    @Test
    fun `create should return new inspector instance each time`() {
        val zipFile = createTestZip("test.zip")
        val factory = ArchiveInspectorFactory(mockLibraryManager)

        val result1 = factory.create(zipFile)
        val result2 = factory.create(zipFile)

        assertTrue(result1.isSuccess)
        assertTrue(result2.isSuccess)
        result1.onSuccess { inspector1 ->
            result2.onSuccess { inspector2 ->
                assertTrue(inspector1 !== inspector2)
                inspector1.close()
                inspector2.close()
            }
        }
    }

    private fun createTestZip(filename: String): File {
        val zipFile = tempFolder.newFile(filename)
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            zip.putNextEntry(ZipEntry("test.txt"))
            zip.write("test content".toByteArray())
            zip.closeEntry()
        }
        return zipFile
    }

    private fun createTestTar(filename: String): File {
        val file = tempFolder.newFile(filename)
        TarArchiveOutputStream(FileOutputStream(file)).use { tar ->
            val entry = TarArchiveEntry("test.txt")
            entry.size = 4L
            tar.putArchiveEntry(entry)
            tar.write("test".toByteArray())
            tar.closeArchiveEntry()
        }
        return file
    }

    private fun createTestTarGz(filename: String): File {
        val file = tempFolder.newFile(filename)
        TarArchiveOutputStream(GzipCompressorOutputStream(FileOutputStream(file))).use { tar ->
            val entry = TarArchiveEntry("test.txt")
            entry.size = 4L
            tar.putArchiveEntry(entry)
            tar.write("test".toByteArray())
            tar.closeArchiveEntry()
        }
        return file
    }

    private fun createTestTarBz2(filename: String): File {
        val file = tempFolder.newFile(filename)
        TarArchiveOutputStream(BZip2CompressorOutputStream(FileOutputStream(file))).use { tar ->
            val entry = TarArchiveEntry("test.txt")
            entry.size = 4L
            tar.putArchiveEntry(entry)
            tar.write("test".toByteArray())
            tar.closeArchiveEntry()
        }
        return file
    }
}
