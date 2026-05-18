package app.otter.data.util

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import app.otter.domain.model.ResourcePath
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tests for ResourcePathConverter.
 *
 * Covers:
 * - Uri ↔ ResourcePath conversion
 * - Windows malformed URI handling (file://C%3A%5C...)
 * - Standard Unix URI handling (file:///path/to/file)
 * - content:// URI resolution (MediaStore, ExternalStorage, Downloads)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ResourcePathConverterTest {

    private lateinit var mockContext: Context
    private lateinit var mockContentResolver: ContentResolver

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockContentResolver = mockk(relaxed = true)
        every { mockContext.contentResolver } returns mockContentResolver
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ========================================================================
    // fromUri / toUri Tests
    // ========================================================================

    @Test
    fun `fromUri converts Android Uri to ResourcePath`() {
        val uri = Uri.parse("content://com.example/file/123")

        val result = ResourcePathConverter.fromUri(uri)

        assertEquals("content://com.example/file/123", result.value)
    }

    @Test
    fun `toUri converts ResourcePath to Android Uri`() {
        val resourcePath = ResourcePath("content://com.example/file/123")

        val result = ResourcePathConverter.toUri(resourcePath)

        assertEquals("content://com.example/file/123", result.toString())
    }

    // ========================================================================
    // getFilePathFromUri Tests
    // ========================================================================

    @Test
    fun `getFilePathFromUri handles Windows malformed URI`() {
        // Malformed Windows URI: file://C%3A%5CUsers%5CJohn%5Cfile.txt
        val uri = Uri.parse("file://C%3A%5CUsers%5CJohn%5Cfile.txt")

        val result = ResourcePathConverter.getFilePathFromUri(uri)

        assertEquals("C:\\Users\\John\\file.txt", result)
    }

    @Test
    fun `getFilePathFromUri handles standard Unix URI`() {
        val uri = Uri.parse("file:///home/john/file.txt")

        val result = ResourcePathConverter.getFilePathFromUri(uri)

        assertEquals("/home/john/file.txt", result)
    }

    @Test
    fun `getFilePathFromUri returns path for http URI (not file URI)`() {
        // Note: getFilePathFromUri only handles file:// URIs specially
        // For http://, it falls through to uri.path which returns "/file.txt"
        val uri = Uri.parse("http://example.com/file.txt")

        val result = ResourcePathConverter.getFilePathFromUri(uri)

        // Expected behavior: returns path component
        assertEquals("/file.txt", result)
    }

    @Test
    fun `getFilePathFromUri handles URI with special characters`() {
        val uri = Uri.parse("file://C%3A%5CUsers%5CJohn%20Doe%5Cmy%20file.txt")

        val result = ResourcePathConverter.getFilePathFromUri(uri)

        assertEquals("C:\\Users\\John Doe\\my file.txt", result)
    }

    // ========================================================================
    // toFile Tests
    // ========================================================================

    @Test
    fun `toFile creates File from Windows URI`() {
        val uri = Uri.parse("file://C%3A%5CUsers%5CJohn%5Cfile.txt")

        val result = ResourcePathConverter.toFile(uri)

        assertNotNull(result)
        assertEquals("C:\\Users\\John\\file.txt", result!!.path)
    }

    @Test
    fun `toFile creates File from Unix URI`() {
        val uri = Uri.parse("file:///home/john/file.txt")

        val result = ResourcePathConverter.toFile(uri)

        assertNotNull(result)
        // On Windows (Robolectric), File converts / to \
        // Just verify the file name is correct
        assert(result!!.path.endsWith("file.txt"))
        assert(result.path.contains("john"))
    }

    @Test
    fun `toFile returns File for http URI (uses path component)`() {
        // Note: toFile calls getFilePathFromUri which returns uri.path for non-file:// URIs
        val uri = Uri.parse("http://example.com/file.txt")

        val result = ResourcePathConverter.toFile(uri)

        // Expected behavior: creates File from path component
        assertNotNull(result)
        assert(result!!.path.endsWith("file.txt"))
    }

    // ========================================================================
    // getRealPathFromContentUri Tests
    // ========================================================================

    @Test
    fun `getRealPathFromContentUri resolves MediaStore URI with DATA column`() {
        val uri = Uri.parse("content://media/external/images/media/123")

        // Use a real temp file path that exists on Windows (Robolectric)
        val expectedPath = File.createTempFile("test", ".jpg").absolutePath

        // Mock cursor with DATA column
        val cursor = MatrixCursor(arrayOf(MediaStore.MediaColumns.DATA))
        cursor.addRow(arrayOf(expectedPath))

        every {
            mockContentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DATA),
                null,
                null,
                null
            )
        } returns cursor

        val result = ResourcePathConverter.getRealPathFromContentUri(mockContext, uri)

        // File.exists() check will pass because we created the file
        assertEquals(expectedPath, result)

        // Cleanup
        File(expectedPath).delete()
    }

    @Test
    fun `getRealPathFromContentUri returns null when DATA column is null`() {
        val uri = Uri.parse("content://media/external/images/media/123")

        // Mock cursor with null DATA column
        val cursor = MatrixCursor(arrayOf(MediaStore.MediaColumns.DATA))
        cursor.addRow(arrayOf(null))

        every {
            mockContentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DATA),
                null,
                null,
                null
            )
        } returns cursor

        val result = ResourcePathConverter.getRealPathFromContentUri(mockContext, uri)

        assertNull(result)
    }

    @Test
    fun `getRealPathFromContentUri returns null when cursor is empty`() {
        val uri = Uri.parse("content://media/external/images/media/123")

        // Mock empty cursor
        val cursor = MatrixCursor(arrayOf(MediaStore.MediaColumns.DATA))

        every {
            mockContentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DATA),
                null,
                null,
                null
            )
        } returns cursor

        val result = ResourcePathConverter.getRealPathFromContentUri(mockContext, uri)

        assertNull(result)
    }

    @Test
    fun `getRealPathFromContentUri handles ExternalStorageProvider primary`() {
        val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2Ffile.zip")

        mockkStatic(DocumentsContract::class)
        every { DocumentsContract.isDocumentUri(mockContext, uri) } returns true
        every { DocumentsContract.getDocumentId(uri) } returns "primary:Download/file.zip"

        mockkStatic(android.os.Environment::class)
        every { android.os.Environment.getExternalStorageDirectory() } returns File("/storage/emulated/0")

        // Mock query to return null (so it falls through to Document provider logic)
        every {
            mockContentResolver.query(any(), any(), any(), any(), any())
        } returns null

        val result = ResourcePathConverter.getRealPathFromContentUri(mockContext, uri)

        // On Windows (Robolectric), File.separator is \
        // Just verify the key components are present
        assertNotNull(result)
        assert(result!!.contains("storage"))
        assert(result.contains("emulated"))
        assert(result.contains("Download"))
        assert(result.endsWith("file.zip"))
    }

    @Test
    fun `getRealPathFromContentUri returns null for DownloadsProvider with invalid docId`() {
        val uri = Uri.parse("content://com.android.providers.downloads.documents/document/invalid")

        mockkStatic(DocumentsContract::class)
        every { DocumentsContract.isDocumentUri(mockContext, uri) } returns true
        every { DocumentsContract.getDocumentId(uri) } returns "invalid"

        // Mock query to return null
        every {
            mockContentResolver.query(any(), any(), any(), any(), any())
        } returns null

        val result = ResourcePathConverter.getRealPathFromContentUri(mockContext, uri)

        assertNull(result)
    }

    @Test
    fun `getRealPathFromContentUri returns null when exception occurs`() {
        val uri = Uri.parse("content://media/external/images/media/123")

        // Mock query to throw exception
        every {
            mockContentResolver.query(any(), any(), any(), any(), any())
        } throws RuntimeException("Database error")

        val result = ResourcePathConverter.getRealPathFromContentUri(mockContext, uri)

        assertNull(result)
    }

    @Test
    fun `getRealPathFromContentUri handles MediaProvider with image type`() {
        val uri = Uri.parse("content://com.android.providers.media.documents/document/image%3A123")

        mockkStatic(DocumentsContract::class)
        every { DocumentsContract.isDocumentUri(mockContext, uri) } returns true
        every { DocumentsContract.getDocumentId(uri) } returns "image:123"

        // Mock query to return null (so it falls through to Document provider logic)
        every {
            mockContentResolver.query(any(), any(), any(), any(), any())
        } returns null

        // The method will call getRealPathFromContentUri recursively with MediaStore.Images URI
        // which will return null (no DATA column)
        val result = ResourcePathConverter.getRealPathFromContentUri(mockContext, uri)

        // Expected: null because recursive call returns null
        assertNull(result)
    }

    @Test
    fun `getRealPathFromContentUri handles MediaProvider with video type`() {
        val uri = Uri.parse("content://com.android.providers.media.documents/document/video%3A456")

        mockkStatic(DocumentsContract::class)
        every { DocumentsContract.isDocumentUri(mockContext, uri) } returns true
        every { DocumentsContract.getDocumentId(uri) } returns "video:456"

        // Mock query to return null
        every {
            mockContentResolver.query(any(), any(), any(), any(), any())
        } returns null

        val result = ResourcePathConverter.getRealPathFromContentUri(mockContext, uri)

        assertNull(result)
    }

    @Test
    fun `getRealPathFromContentUri handles MediaProvider with audio type`() {
        val uri = Uri.parse("content://com.android.providers.media.documents/document/audio%3A789")

        mockkStatic(DocumentsContract::class)
        every { DocumentsContract.isDocumentUri(mockContext, uri) } returns true
        every { DocumentsContract.getDocumentId(uri) } returns "audio:789"

        // Mock query to return null
        every {
            mockContentResolver.query(any(), any(), any(), any(), any())
        } returns null

        val result = ResourcePathConverter.getRealPathFromContentUri(mockContext, uri)

        assertNull(result)
    }

    @Test
    fun `getRealPathFromContentUri handles MediaProvider with unknown type`() {
        val uri = Uri.parse("content://com.android.providers.media.documents/document/other%3A999")

        mockkStatic(DocumentsContract::class)
        every { DocumentsContract.isDocumentUri(mockContext, uri) } returns true
        every { DocumentsContract.getDocumentId(uri) } returns "other:999"

        // Mock query to return null
        every {
            mockContentResolver.query(any(), any(), any(), any(), any())
        } returns null

        val result = ResourcePathConverter.getRealPathFromContentUri(mockContext, uri)

        // Expected: null because type "other" is not handled
        assertNull(result)
    }

    @Test
    fun `getRealPathFromContentUri handles DownloadsProvider with valid docId`() {
        val uri = Uri.parse("content://com.android.providers.downloads.documents/document/123")

        mockkStatic(DocumentsContract::class)
        every { DocumentsContract.isDocumentUri(mockContext, uri) } returns true
        every { DocumentsContract.getDocumentId(uri) } returns "123"

        mockkStatic(ContentUris::class)
        val downloadsUri = Uri.parse("content://downloads/public_downloads/123")
        every { ContentUris.withAppendedId(any(), 123) } returns downloadsUri

        // Mock query to return null (recursive call returns null)
        every {
            mockContentResolver.query(any(), any(), any(), any(), any())
        } returns null

        val result = ResourcePathConverter.getRealPathFromContentUri(mockContext, uri)

        // Expected: null because recursive call returns null
        assertNull(result)
    }
}
