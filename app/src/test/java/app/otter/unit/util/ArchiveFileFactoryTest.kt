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

    /**
     * Creates a Uri using URL encoding that works with ResourcePathConverter.
     *
     * ResourcePathConverter.getFilePathFromUri() expects:
     * - Windows: file://C%3A%5CUsers%5C... (URL-encoded, matching Uri.fromFile() output)
     * - Unix: file:///path/to/file (standard format with 3 slashes)
     *
     * This matches Robolectric's Uri.fromFile() behavior, but works correctly.
     */
    private fun createFileUri(file: File): Uri {
        val absolutePath = file.absolutePath

        // Windows paths need URL encoding: C:\Users\ -> file://C%3A%5CUsers%5C
        // Unix paths are standard: /path/to/file -> file:///path/to/file
        val uriString = if (absolutePath.matches(Regex("^[A-Z]:.*"))) {
            "file://" + java.net.URLEncoder.encode(absolutePath, "UTF-8")
        } else {
            "file://$absolutePath"
        }

        return Uri.parse(uriString)
    }

    @Test
    fun `createFromUri with file URI should create ArchiveFile`() {
        // Given
        val testFile = tempFolder.newFile("test.zip")
        testFile.writeText("fake zip content")
        val uri = createFileUri(testFile)

        // When
        val archiveFile = factory.createFromUri(uri, "test.zip")

        // Then
        assertNotNull("ArchiveFile should not be null for valid ZIP file", archiveFile)
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
        val uri = createFileUri(testFile)

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
        val uri = createFileUri(directory)

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
        val uri = createFileUri(textFile)

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
        val zip = factory.createFromUri(createFileUri(zipFile), "test.zip")
        val rar = factory.createFromUri(createFileUri(rarFile), "test.rar")

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
        val uri = createFileUri(testFile)

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
        val uri = createFileUri(emptyFile)

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

    // ========== content:// path fallback (Samsung bug fix) ==========

    @Test
    fun `createFromPath with content URI string falls back to content URI handler`() {
        // Arrange: ResourcePath.FileSystem whose path is a content:// URI string
        // (Samsung My Files stores it this way after ResourcePathConverter.fromUri fallback)
        val contentUriPath = "content://com.sec.android.app.myfiles.FileProvider/sdcard/test.zip"
        val resourcePath = app.otter.domain.model.ResourcePath.FileSystem(contentUriPath)

        // Mock ContentResolver to return size for the content URI
        val mockContentResolver = io.mockk.mockk<android.content.ContentResolver>(relaxed = true)
        io.mockk.every { context.contentResolver } returns mockContentResolver

        // Simulate cursor with file size and display name
        val cursor = android.database.MatrixCursor(
            arrayOf(android.provider.OpenableColumns.SIZE, android.provider.OpenableColumns.DISPLAY_NAME)
        )
        cursor.addRow(arrayOf(1024L, "test.zip"))
        io.mockk.every { mockContentResolver.query(any(), any(), any(), any(), any()) } returns cursor
        io.mockk.every { mockContentResolver.getType(any()) } returns "application/zip"

        // Act
        val result = factory.createFromPath(resourcePath, "test.zip")

        // Assert: content:// URI was detected and routed to createFromContentUri
        assertNotNull("createFromPath should handle content:// URI string via content URI fallback", result)
    }
}
