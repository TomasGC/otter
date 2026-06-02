package app.otter.data.inspector

import app.otter.domain.inspector.ArchiveType
import org.junit.Assert.assertEquals
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

    @Test
    fun `create should detect ZIP by extension`() {
        // Arrange
        val zipFile = createTestZip("test.zip")
        val factory = ArchiveInspectorFactory()

        // Act
        val result = factory.create(zipFile)

        // Assert
        assertTrue(result.isSuccess)
        result.onSuccess { inspector ->
            assertEquals(ArchiveType.ZIP, inspector.getArchiveType())
            inspector.close()
        }
    }

    @Test
    fun `create should detect ZIP by magic bytes even with wrong extension`() {
        // Arrange
        val zipFile = createTestZip("fake.txt") // Wrong extension
        val factory = ArchiveInspectorFactory()

        // Act
        val result = factory.create(zipFile)

        // Assert
        assertTrue(result.isSuccess)
        result.onSuccess { inspector ->
            assertEquals(ArchiveType.ZIP, inspector.getArchiveType())
            inspector.close()
        }
    }

    @Test
    fun `create should throw UnsupportedOperationException for RAR format`() {
        // Arrange
        val rarFile = tempFolder.newFile("test.rar")
        // Write RAR magic bytes (Rar!\x1A\x07)
        rarFile.writeBytes(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00))
        val factory = ArchiveInspectorFactory()

        // Act
        val result = factory.create(rarFile)

        // Assert
        assertTrue(result.isFailure)
        result.onFailure { exception ->
            assertTrue(exception is UnsupportedOperationException)
            assertTrue(exception.message?.contains("not yet supported") == true)
            assertTrue(exception.message?.contains("RAR") == true)
        }
    }

    @Test
    fun `create should throw UnsupportedOperationException for 7z format`() {
        // Arrange
        val sevenZipFile = tempFolder.newFile("test.7z")
        // Write 7z magic bytes (7z\xBC\xAF\x27\x1C)
        sevenZipFile.writeBytes(byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C))
        val factory = ArchiveInspectorFactory()

        // Act
        val result = factory.create(sevenZipFile)

        // Assert
        assertTrue(result.isFailure)
        result.onFailure { exception ->
            assertTrue(exception is UnsupportedOperationException)
            assertTrue(exception.message?.contains("not yet supported") == true)
            assertTrue(exception.message?.contains("SEVEN_ZIP") == true)
        }
    }

    @Test
    fun `create should throw IllegalArgumentException for unknown format`() {
        // Arrange
        val unknownFile = tempFolder.newFile("test.unknown")
        unknownFile.writeText("This is just plain text")
        val factory = ArchiveInspectorFactory()

        // Act
        val result = factory.create(unknownFile)

        // Assert
        assertTrue(result.isFailure)
        result.onFailure { exception ->
            assertTrue(exception is IllegalArgumentException)
            assertTrue(exception.message?.contains("Could not detect archive format") == true)
        }
    }

    @Test
    fun `create should throw IllegalArgumentException for non-existent file`() {
        // Arrange
        val nonExistentFile = File(tempFolder.root, "does-not-exist.zip")
        val factory = ArchiveInspectorFactory()

        // Act
        val result = factory.create(nonExistentFile)

        // Assert
        assertTrue(result.isFailure)
        result.onFailure { exception ->
            assertTrue(exception is IllegalArgumentException)
            assertTrue(exception.message?.contains("File does not exist") == true)
        }
    }

    @Test
    fun `create should throw IllegalArgumentException for directory`() {
        // Arrange
        val directory = tempFolder.newFolder("test-dir")
        val factory = ArchiveInspectorFactory()

        // Act
        val result = factory.create(directory)

        // Assert
        assertTrue(result.isFailure)
        result.onFailure { exception ->
            assertTrue(exception is IllegalArgumentException)
            assertTrue(exception.message?.contains("not a file") == true)
        }
    }

    @Test
    fun `create should return new inspector instance each time`() {
        // Arrange
        val zipFile = createTestZip("test.zip")
        val factory = ArchiveInspectorFactory()

        // Act
        val result1 = factory.create(zipFile)
        val result2 = factory.create(zipFile)

        // Assert
        assertTrue(result1.isSuccess)
        assertTrue(result2.isSuccess)

        result1.onSuccess { inspector1 ->
            result2.onSuccess { inspector2 ->
                // Verify they are different instances
                assertTrue(inspector1 !== inspector2)
                inspector1.close()
                inspector2.close()
            }
        }
    }

    @Test
    fun `create should detect TAR format by extension`() {
        // Arrange
        val tarFile = tempFolder.newFile("test.tar")
        // TAR has no magic bytes at start, use ustar signature at offset 257
        val header = ByteArray(512)
        "ustar".toByteArray().copyInto(header, 257)
        tarFile.writeBytes(header)
        val factory = ArchiveInspectorFactory()

        // Act
        val result = factory.create(tarFile)

        // Assert
        assertTrue(result.isFailure)
        result.onFailure { exception ->
            assertTrue(exception is UnsupportedOperationException)
            assertTrue(exception.message?.contains("TAR") == true)
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
}
