package app.otter.util

import android.content.Context
import android.net.Uri
import app.otter.domain.model.ArchiveType
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Unit tests for ArchiveFileFactory.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ArchiveFileFactoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var mimeTypeUtil: MimeTypeUtil
    private lateinit var factory: ArchiveFileFactory

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        mimeTypeUtil = MimeTypeUtil()
        factory = ArchiveFileFactory(context, mimeTypeUtil)
    }

    @Test
    fun `createFromUri with file URI should create ArchiveFile`() {
        // Given
        val testFile = tempFolder.newFile("test.zip")
        testFile.writeText("fake zip content")
        val uri = Uri.fromFile(testFile)

        // When
        val archiveFile = factory.createFromUri(uri, "test.zip")

        // Then
        assertNotNull(archiveFile)
        assertEquals("test.zip", archiveFile!!.name)
        assertEquals(ArchiveType.ZIP, archiveFile.type)
        assertEquals("application/zip", archiveFile.mimeType)
        assertEquals(testFile.length(), archiveFile.sizeBytes)
    }

    @Test
    fun `createFromUri with RAR file should detect RAR type`() {
        // Given
        val testFile = tempFolder.newFile("archive.rar")
        testFile.writeText("fake rar content")
        val uri = Uri.fromFile(testFile)

        // When
        val archiveFile = factory.createFromUri(uri, "archive.rar")

        // Then
        assertNotNull(archiveFile)
        assertEquals(ArchiveType.RAR, archiveFile!!.type)
        assertEquals("application/x-rar-compressed", archiveFile.mimeType)
    }

    @Test
    fun `createFromUri with nonexistent file should return null`() {
        // Given
        val nonexistentUri = Uri.parse("file:///nonexistent/test.zip")

        // When
        val archiveFile = factory.createFromUri(nonexistentUri, "test.zip")

        // Then
        assertNull(archiveFile)
    }

    @Test
    fun `createFromUri with directory should return null`() {
        // Given
        val directory = tempFolder.newFolder("testdir")
        val uri = Uri.fromFile(directory)

        // When
        val archiveFile = factory.createFromUri(uri, "testdir")

        // Then
        assertNull(archiveFile)
    }

    @Test
    fun `createFromUri with non-archive file should return null`() {
        // Given
        val textFile = tempFolder.newFile("document.txt")
        textFile.writeText("not an archive")
        val uri = Uri.fromFile(textFile)

        // When
        val archiveFile = factory.createFromUri(uri, "document.txt")

        // Then
        assertNull(archiveFile) // No ArchiveType for .txt
    }

    @Test
    fun `createFromUri with null file path should return null`() {
        // Given
        val uriWithNullPath = Uri.parse("file://")

        // When
        val archiveFile = factory.createFromUri(uriWithNullPath, "test.zip")

        // Then
        assertNull(archiveFile)
    }

    @Test
    fun `createFromUri with unsupported URI scheme should return null`() {
        // Given
        val unsupportedUri = Uri.parse("http://example.com/archive.zip")

        // When
        val archiveFile = factory.createFromUri(unsupportedUri, "archive.zip")

        // Then
        assertNull(archiveFile)
    }

    @Test
    fun `createFromUri should handle different archive extensions`() {
        // Given
        val zipFile = tempFolder.newFile("test.zip")
        val rarFile = tempFolder.newFile("test.rar")
        zipFile.writeText("zip")
        rarFile.writeText("rar")

        // When
        val zip = factory.createFromUri(Uri.fromFile(zipFile), "test.zip")
        val rar = factory.createFromUri(Uri.fromFile(rarFile), "test.rar")

        // Then
        assertNotNull(zip)
        assertNotNull(rar)
        assertEquals(ArchiveType.ZIP, zip!!.type)
        assertEquals(ArchiveType.RAR, rar!!.type)
    }

    @Test
    fun `createFromUri should set correct file size`() {
        // Given
        val testFile = tempFolder.newFile("test.zip")
        val content = "This is test content with some length"
        testFile.writeText(content)
        val uri = Uri.fromFile(testFile)

        // When
        val archiveFile = factory.createFromUri(uri, "test.zip")

        // Then
        assertNotNull(archiveFile)
        assertEquals(content.length.toLong(), archiveFile!!.sizeBytes)
    }

    @Test
    fun `createFromUri with empty file should create ArchiveFile with zero size`() {
        // Given
        val emptyFile = tempFolder.newFile("empty.zip")
        val uri = Uri.fromFile(emptyFile)

        // When
        val archiveFile = factory.createFromUri(uri, "empty.zip")

        // Then
        assertNotNull(archiveFile)
        assertEquals(0L, archiveFile!!.sizeBytes)
    }

    @Test
    fun `createFromUri with content URI should return null without ContentResolver setup`() {
        // Given
        val contentUri = Uri.parse("content://downloads/test.zip")

        // When
        val archiveFile = factory.createFromUri(contentUri, "test.zip")

        // Then
        // Without proper ContentResolver mock, this returns null
        assertNull(archiveFile)
    }
}
