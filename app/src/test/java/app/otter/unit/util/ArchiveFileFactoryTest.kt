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
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for ArchiveFileFactory.
 */
class ArchiveFileFactoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var mimeTypeUtil: MimeTypeUtil
    private lateinit var factory: ArchiveFileFactory

    @Before
    fun setup() {
        mockkStatic(Uri::class)
        every { Uri.parse(any<String>()) } answers { mockUri(firstArg()) }
        context = mockk(relaxed = true)
        mimeTypeUtil = MimeTypeUtil()
        factory = ArchiveFileFactory(context, mimeTypeUtil)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun mockUri(uriString: String): Uri {
        val uriScheme = if (uriString.contains("://")) uriString.substringBefore("://") else ""
        val uriPath = when {
            uriString.startsWith("file:///") -> uriString.removePrefix("file://")
            uriString.startsWith("file://") -> null
            uriString.startsWith("content://") -> "/${uriString.substringAfter("://").substringAfter("/")}"
            else -> null
        }
        return mockk<Uri>(relaxed = true).also { mock ->
            every { mock.scheme } returns uriScheme
            every { mock.toString() } returns uriString
            every { mock.path } returns uriPath
            every { mock.lastPathSegment } returns uriString.substringAfterLast("/").takeIf { it.isNotBlank() }
        }
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

    // ========== content:// SIZE column absent — content:// SIZE=0 false rejection fix ==========

    @Test
    fun `createFromContentUri with missing SIZE column should store unknown size sentinel`() {
        // Arrange: cursor without SIZE column (Google Files / some Samsung URIs)
        val cursor = io.mockk.mockk<android.database.Cursor>(relaxed = true)
        io.mockk.every { cursor.moveToFirst() } returns true
        io.mockk.every { cursor.getColumnIndex(android.provider.OpenableColumns.SIZE) } returns -1

        val mockContentResolver = io.mockk.mockk<android.content.ContentResolver>(relaxed = true)
        io.mockk.every { context.contentResolver } returns mockContentResolver
        io.mockk.every { mockContentResolver.query(any(), any(), any(), any(), any()) } returns cursor
        io.mockk.every { mockContentResolver.getType(any()) } returns "application/zip"

        // content:// URIs are stored as ResourcePath.FileSystem with the URI string as path
        val path = app.otter.domain.model.ResourcePath.FileSystem("content://com.example.provider/archive.zip")

        // Act
        val result = factory.createFromPath(path, "archive.zip")

        // Assert: must not be null, and sizeBytes must be -1L (unknown)
        assertNotNull("ArchiveFile should not be null when SIZE is absent", result)
        assertEquals(
            "sizeBytes should be UNKNOWN_SIZE sentinel (-1L) when SIZE column is absent",
            -1L,
            result!!.sizeBytes
        )
    }

    @Test
    fun `createFromContentUri with SIZE column present and non-zero should store actual size`() {
        val cursor = io.mockk.mockk<android.database.Cursor>(relaxed = true)
        io.mockk.every { cursor.moveToFirst() } returns true
        io.mockk.every { cursor.getColumnIndex(android.provider.OpenableColumns.SIZE) } returns 0
        io.mockk.every { cursor.getLong(0) } returns 4096L

        val mockContentResolver = io.mockk.mockk<android.content.ContentResolver>(relaxed = true)
        io.mockk.every { context.contentResolver } returns mockContentResolver
        io.mockk.every { mockContentResolver.query(any(), any(), any(), any(), any()) } returns cursor
        io.mockk.every { mockContentResolver.getType(any()) } returns "application/zip"

        // content:// URIs are stored as ResourcePath.FileSystem with the URI string as path
        val path = app.otter.domain.model.ResourcePath.FileSystem("content://com.example.provider/archive.zip")

        val result = factory.createFromPath(path, "archive.zip")

        assertNotNull(result)
        assertEquals(4096L, result!!.sizeBytes)
    }

    // ========== content:// path fallback (Samsung bug fix) ==========

    @Test
    fun `createFromContentUri with empty cursor (moveToFirst false) returns null`() {
        val cursor = android.database.MatrixCursor(arrayOf(android.provider.OpenableColumns.DISPLAY_NAME))
        // No rows added → moveToFirst() returns false

        val mockCr = io.mockk.mockk<android.content.ContentResolver>(relaxed = true)
        io.mockk.every { context.contentResolver } returns mockCr
        io.mockk.every { mockCr.query(any(), any(), any(), any(), any()) } returns cursor

        val path = app.otter.domain.model.ResourcePath.FileSystem("content://com.example.provider/archive.zip")
        val result = factory.createFromPath(path, "archive.zip")

        assertNull("Empty cursor must return null", result)
    }

    @Test
    fun `createFromContentUri with unknown archive extension returns null`() {
        val cursor = android.database.MatrixCursor(
            arrayOf(android.provider.OpenableColumns.SIZE, android.provider.OpenableColumns.DISPLAY_NAME)
        )
        cursor.addRow(arrayOf(1024L, "file.xyz"))

        val mockCr = io.mockk.mockk<android.content.ContentResolver>(relaxed = true)
        io.mockk.every { context.contentResolver } returns mockCr
        io.mockk.every { mockCr.query(any(), any(), any(), any(), any()) } returns cursor
        io.mockk.every { mockCr.getType(any()) } returns "application/octet-stream"

        val path = app.otter.domain.model.ResourcePath.FileSystem("content://com.example.provider/file.xyz")
        val result = factory.createFromPath(path, "file.xyz")

        assertNull("Unknown archive extension must return null", result)
    }

    @Test
    fun `createFromPath with content URI string falls back to content URI handler`() {
        // Arrange: ResourcePath.FileSystem whose path is a content:// URI string
        // (Samsung My Files stores it this way after ResourcePathConverter.fromUri fallback)
        val contentUriPath = "content://com.sec.android.app.myfiles.FileProvider/sdcard/test.zip"
        val resourcePath = app.otter.domain.model.ResourcePath.FileSystem(contentUriPath)

        // Mock ContentResolver to return size for the content URI
        val mockContentResolver = io.mockk.mockk<android.content.ContentResolver>(relaxed = true)
        io.mockk.every { context.contentResolver } returns mockContentResolver

        // Simulate cursor with file size
        val cursor = io.mockk.mockk<android.database.Cursor>(relaxed = true)
        io.mockk.every { cursor.moveToFirst() } returns true
        io.mockk.every { cursor.getColumnIndex(android.provider.OpenableColumns.SIZE) } returns 0
        io.mockk.every { cursor.getLong(0) } returns 1024L
        io.mockk.every { mockContentResolver.query(any(), any(), any(), any(), any()) } returns cursor
        io.mockk.every { mockContentResolver.getType(any()) } returns "application/zip"

        // Act
        val result = factory.createFromPath(resourcePath, "test.zip")

        // Assert: content:// URI was detected and routed to createFromContentUri
        assertNotNull("createFromPath should handle content:// URI string via content URI fallback", result)
    }
}
