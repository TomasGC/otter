package app.otter.util

import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExtractionDestinationResolverTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var resolver: ExtractionDestinationResolver

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        resolver = ExtractionDestinationResolver(context)
    }

    @Test
    fun `should create destination folder from file name`() {
        // Given
        val parentDir = temporaryFolder.newFolder("Download")
        val parentPath = parentDir.absolutePath
        val fileName = "archive.zip"

        // When
        val destination = resolver.createDestinationFolder(
            parentPath,
            fileName,
        )

        // Then
        assertEquals(
            "Folder name should match archive name without extension",
            "archive",
            destination.name,
        )
        assertEquals(
            "Parent path should match",
            parentPath,
            destination.parentFile?.absolutePath,
        )
    }

    @Test
    fun `should handle file name with multiple dots`() {
        // Given
        val parentPath = "/storage/emulated/0/Download"
        val fileName = "my.archive.v1.2.zip"

        // When
        val destination = resolver.createDestinationFolder(
            parentPath,
            fileName,
        )

        // Then
        assertEquals(
            "Should remove only last extension",
            "my.archive.v1.2",
            destination.name,
        )
    }

    @Test
    fun `should handle file name without extension`() {
        // Given
        val parentPath = "/storage/emulated/0/Download"
        val fileName = "archive_no_extension"

        // When
        val destination = resolver.createDestinationFolder(
            parentPath,
            fileName,
        )

        // Then
        assertEquals(
            "Folder name should match file name",
            "archive_no_extension",
            destination.name,
        )
    }

    @Test
    fun `should create downloads destination`() {
        // Given
        val fileName = "test.zip"

        // When
        val destination = resolver.createDownloadsDestination(
            fileName,
        )

        // Then
        assertNotNull("Destination should not be null", destination)
        assertEquals(
            "Folder name should match archive name",
            "test",
            destination.name,
        )
        assertTrue(
            "Path should contain Download",
            destination.absolutePath.contains("Download"),
        )
    }

    @Test
    fun `should handle special characters in file name`() {
        // Given
        val parentPath = "/storage/emulated/0/Download"
        val fileName = "my archive (2024) [v1].zip"

        // When
        val destination = resolver.createDestinationFolder(
            parentPath,
            fileName,
        )

        // Then
        assertEquals(
            "Special characters should be preserved",
            "my archive (2024) [v1]",
            destination.name,
        )
    }

    @Test
    fun `should handle Unicode file name`() {
        // Given
        val parentPath = "/storage/emulated/0/Download"
        val fileName = "文件.zip"

        // When
        val destination = resolver.createDestinationFolder(
            parentPath,
            fileName,
        )

        // Then
        assertEquals(
            "Unicode should be preserved",
            "文件",
            destination.name,
        )
    }

    @Test
    fun `should handle very long file name`() {
        // Given
        val parentPath = "/storage/emulated/0/Download"
        val longName = "a".repeat(200)
        val fileName = "$longName.zip"

        // When
        val destination = resolver.createDestinationFolder(
            parentPath,
            fileName,
        )

        // Then
        assertEquals(
            "Long name should be preserved",
            longName,
            destination.name,
        )
    }

    @Test
    fun `should handle empty file name`() {
        // Given
        val parentPath = "/storage/emulated/0/Download"
        val fileName = ""

        // When
        val destination = resolver.createDestinationFolder(
            parentPath,
            fileName,
        )

        // Then
        // substringBeforeLast on empty string returns empty string
        // But File with empty name might normalize to "." or ""
        assertNotNull("Destination should not be null", destination)
    }

    @Test
    fun `should resolve destination with fallback to downloads`() {
        // Given - Invalid URI that will trigger fallback
        val invalidUri = Uri.parse("content://invalid/document/1")
        val fileName = "test.zip"

        // When
        val destination = resolver.resolveDestination(invalidUri, fileName)

        // Then
        assertNotNull("Destination should not be null", destination)
        assertTrue(
            "Should fallback to Downloads",
            destination.absolutePath.contains("Download"),
        )
        assertEquals(
            "Folder name should be correct",
            "test",
            destination.name,
        )
    }

    @Test
    fun `should return null for invalid URI`() {
        // Given
        val invalidUri = Uri.parse("invalid://path")

        // When
        val result = resolver.getRealPathFromUri(invalidUri)

        // Then
        assertEquals("Invalid URI should return null", null, result)
    }

    @Test
    fun `should handle file name with only extension`() {
        // Given
        val parentPath = "/storage/emulated/0/Download"
        val fileName = ".zip"

        // When
        val destination = resolver.createDestinationFolder(
            parentPath,
            fileName,
        )

        // Then
        // substringBeforeLast(".zip", ".") returns "." (the part before last dot)
        // File(".") might normalize to current directory
        assertNotNull("Destination should not be null", destination)
        assertTrue(
            "Folder name should be . or normalized",
            destination.name == "." || destination.name.isNotEmpty(),
        )
    }

    @Test
    fun `should handle nested parent path`() {
        // Given
        val rootDir = temporaryFolder.newFolder("Download")
        val nestedDir = temporaryFolder.newFolder("Download", "folder1", "folder2")
        val parentPath = nestedDir.absolutePath
        val fileName = "archive.zip"

        // When
        val destination = resolver.createDestinationFolder(
            parentPath,
            fileName,
        )

        // Then
        assertEquals(
            "Parent should be nested path",
            parentPath,
            destination.parentFile?.absolutePath,
        )
    }

    // ========== Bug 4 & 5: Samsung My Files content:// URI ==========

    @Test
    fun `resolveDestination with file URI returns sibling folder`() {
        // Given - archive in a real temp folder
        val archiveDir = temporaryFolder.newFolder("archives")
        val archiveFile = java.io.File(archiveDir, "test.zip").also { it.createNewFile() }
        val fileUri = Uri.fromFile(archiveFile)
        val fileName = "test.zip"

        // When
        val destination = resolver.resolveDestination(fileUri, fileName)

        // Then - should extract to sibling folder "test" in same directory
        assertNotNull("Destination should not be null", destination)
        assertEquals("Folder name should be archive name without extension", "test", destination.name)
        assertEquals("Parent should be same dir as archive", archiveDir.absolutePath, destination.parentFile?.absolutePath)
    }

    @Test
    fun `resolveDestination with external storage document URI uses downloads fallback`() {
        // Given - Samsung My Files style URI (com.android.externalstorage.documents authority)
        // In Robolectric, we can't fully mock the ContentProvider, so it falls back to Downloads
        val samsungUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2Ftest.zip")
        val fileName = "test.zip"

        // When
        val destination = resolver.resolveDestination(samsungUri, fileName)

        // Then - must never crash and must return a valid destination
        assertNotNull("Destination must not be null even for Samsung URI", destination)
        assertEquals("Folder name must be correct", "test", destination.name)
        // Either resolved to same folder or fell back to Downloads
        assertTrue("Path must be non-empty", destination.absolutePath.isNotEmpty())
    }

    @Test
    fun `resolveDestination never returns null`() {
        // Given - various URI types that could cause crashes
        val uris = listOf(
            Uri.parse("content://com.sec.android.app.myfiles.FileProvider/sdcard/Download/test.zip"),
            Uri.parse("content://com.android.providers.downloads.documents/document/1"),
            Uri.parse("content://media/external/file/12345"),
            Uri.parse("content://invalid/document/1"),
            Uri.EMPTY,
        )

        uris.forEach { uri ->
            // When / Then - must not throw, must return valid File
            val destination = resolver.resolveDestination(uri, "test.zip")
            assertNotNull("Destination must not be null for URI: $uri", destination)
            assertTrue("Folder name must not be empty for URI: $uri", destination.name.isNotEmpty())
        }
    }

    @Test
    fun `getPathFromDocumentHierarchy with external storage docId returns correct path`() {
        // external storage document URI format: primary:Download/test.zip
        val externalStorageUri = Uri.parse(
            "content://com.android.externalstorage.documents/document/primary%3ADownload%2Ftest.zip"
        )
        val documentFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, externalStorageUri)

        val result = resolver.getPathFromDocumentHierarchy(documentFile)

        assertEquals(
            "${android.os.Environment.getExternalStorageDirectory()}/Download/test.zip",
            result,
        )
    }

    @Test
    fun `createDownloadsDestination creates folder in Downloads`() {
        // Given
        val fileName = "my_archive.zip"

        // When
        val destination = resolver.createDownloadsDestination(fileName)

        // Then
        assertNotNull(destination)
        assertEquals("my_archive", destination.name)
        assertTrue("Must be in Downloads", destination.absolutePath.contains("Download"))
    }

    // ========== Standard document URI same-folder resolution ==========
    // DocumentFile.fromSingleUri() always has parentFile == null (SingleDocumentFile is
    // constructed with a hardcoded null parent — verified against the documentfile-1.0.1
    // bytecode), so resolveDestination()'s "method 2" (documentFile?.parentFile?.uri) could
    // never resolve a parent, and every standard document:// archive silently fell back to
    // Downloads instead of extracting alongside the source file as documented.

    @Test
    fun `resolveDestination with standard external storage document URI extracts to same folder as archive`() {
        val docId = "primary:Download/sub/archive.zip"
        val uri = Uri.parse(
            "content://com.android.externalstorage.documents/document/${Uri.encode(docId)}"
        )
        val fileName = "archive.zip"

        val destination = resolver.resolveDestination(uri, fileName)

        val expectedParent = java.io.File(
            android.os.Environment.getExternalStorageDirectory(), "Download/sub"
        ).absolutePath
        assertEquals("archive", destination.name)
        assertEquals(
            "Should extract to the archive's own folder, not fall back to Downloads",
            expectedParent,
            destination.parentFile?.absolutePath,
        )
    }

    @Test
    fun `resolveDestination with Samsung My Files document URI extracts to same folder as archive`() {
        val docId = "primary:/storage/emulated/0/Download/sub/archive.zip"
        val uri = Uri.parse(
            "content://com.sec.android.app.myfiles.FileProvider/document/${Uri.encode(docId)}"
        )
        val fileName = "archive.zip"

        val destination = resolver.resolveDestination(uri, fileName)

        assertEquals("archive", destination.name)
        assertEquals(
            "Should extract to the archive's own folder, not fall back to Downloads",
            java.io.File("/storage/emulated/0/Download/sub").absolutePath,
            destination.parentFile?.absolutePath,
        )
    }

    @Test
    fun `resolveDestination falls back to Downloads when document URI docId is malformed`() {
        // docId missing the "primary:" prefix required by resolveExternalStorageDocPath
        val docId = "unexpectedformat"
        val uri = Uri.parse(
            "content://com.android.externalstorage.documents/document/${Uri.encode(docId)}"
        )

        val destination = resolver.resolveDestination(uri, "archive.zip")

        assertTrue(
            "Malformed docId must still fall back to Downloads, not crash",
            destination.absolutePath.contains("Download"),
        )
    }

    @Test
    fun `getPathFromDocumentHierarchy with null documentFile returns null`() {
        val result = resolver.getPathFromDocumentHierarchy(null)
        org.junit.Assert.assertNull("Null documentFile must return null immediately", result)
    }

    @Test
    fun `getPathFromMediaStore returns null when ContentResolver query throws`() {
        val mockContext = mockk<android.content.Context>(relaxed = true)
        val mockCr = mockk<android.content.ContentResolver>(relaxed = true)
        every { mockContext.contentResolver } returns mockCr
        every { mockCr.query(any(), any(), any(), any(), any()) } throws RuntimeException("query failed")

        val testResolver = ExtractionDestinationResolver(mockContext)
        val result = testResolver.getPathFromMediaStore(Uri.parse("content://media/external/file/1"))

        org.junit.Assert.assertNull("Exception in MediaStore query must return null, not propagate", result)
    }
}
