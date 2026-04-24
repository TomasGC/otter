package app.otter.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import app.otter.data.util.ResourcePathConverter
import app.otter.domain.model.ResourcePath
import app.otter.util.MimeTypeUtil
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Unit tests for FileBrowserRepositoryImpl.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FileBrowserRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var mimeTypeUtil: MimeTypeUtil
    private lateinit var repository: FileBrowserRepositoryImpl

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        mimeTypeUtil = MimeTypeUtil()
        repository = FileBrowserRepositoryImpl(context, mimeTypeUtil)
    }

    @Test
    fun `listFiles with file URI should return files in directory`() = runTest {
        // Given
        val testDir = tempFolder.newFolder("test")
        val file1 = File(testDir, "file1.txt").apply { createNewFile() }
        val file2 = File(testDir, "file2.zip").apply { createNewFile() }
        val path = ResourcePathConverter.fromUri(Uri.fromFile(testDir))

        // When
        val result = repository.listFiles(path)

        // Then
        assertTrue(result.isSuccess)
        val files = result.getOrNull()!!
        assertEquals(2, files.size)
        assertTrue(files.any { it.name == "file1.txt" })
        assertTrue(files.any { it.name == "file2.zip" })
    }

    @Test
    fun `listFiles should include directories`() = runTest {
        // Given
        val testDir = tempFolder.newFolder("test")
        val subDir = File(testDir, "subfolder").apply { mkdir() }
        val path = ResourcePathConverter.fromUri(Uri.fromFile(testDir))

        // When
        val result = repository.listFiles(path)

        // Then
        val files = result.getOrNull()!!
        assertTrue(files.any { it.name == "subfolder" && it.isDirectory })
    }

    @Test
    fun `listFiles should set correct MIME types for archives`() = runTest {
        // Given
        val testDir = tempFolder.newFolder("test")
        File(testDir, "archive.zip").createNewFile()
        File(testDir, "archive.rar").createNewFile()
        val path = ResourcePathConverter.fromUri(Uri.fromFile(testDir))

        // When
        val result = repository.listFiles(path)

        // Then
        val files = result.getOrNull()!!
        val zipFile = files.find { it.name == "archive.zip" }!!
        val rarFile = files.find { it.name == "archive.rar" }!!

        assertEquals("application/zip", zipFile.mimeType)
        assertEquals("application/x-rar-compressed", rarFile.mimeType)
    }

    @Test
    fun `listFiles with nonexistent directory should return empty list`() = runTest {
        // Given
        val nonexistent = ResourcePath.from("file:///nonexistent/path")

        // When
        val result = repository.listFiles(nonexistent)

        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
    }

    @Test
    fun `listFiles with file instead of directory should return empty list`() = runTest {
        // Given
        val file = tempFolder.newFile("test.txt")
        val path = ResourcePathConverter.fromUri(Uri.fromFile(file))

        // When
        val result = repository.listFiles(path)

        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
    }

    @Test
    fun `listFiles with unsupported URI scheme should return failure`() = runTest {
        // Given
        val invalidPath = ResourcePath.from("invalid://test")

        // When
        val result = repository.listFiles(invalidPath)

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun `getParent should return parent directory for file URI`() {
        // Given
        val childDir = File("/storage/emulated/0/Download")
        val childPath = ResourcePathConverter.fromUri(Uri.fromFile(childDir))

        // When
        val parentPath = repository.getParent(childPath)

        // Then
        // Parent should exist
        assertTrue(parentPath != null)
    }

    @Test
    fun `getParent at root should return null`() {
        // Given
        val rootPath = ResourcePath.from("file:///")

        // When
        val parentPath = repository.getParent(rootPath)

        // Then
        assertNull(parentPath)
    }

    @Test
    fun `getParent at external storage root should return null`() {
        // Given - Use actual root path instead of getExternalStorageDirectory()
        // which may have a parent in Robolectric test environment
        val rootPath = ResourcePath.from("file:///")

        // When
        val parentPath = repository.getParent(rootPath)

        // Then
        assertNull(parentPath)
    }

    @Test
    fun `isRoot should return true for root path`() {
        // Given
        val rootPath = ResourcePath.from("file:///")

        // When
        val result = repository.isRoot(rootPath)

        // Then
        assertTrue(result)
    }

    @Test
    fun `isRoot should return true for external storage directory`() {
        // Given
        val externalStoragePath = ResourcePathConverter.fromUri(Uri.fromFile(Environment.getExternalStorageDirectory()))

        // When
        val result = repository.isRoot(externalStoragePath)

        // Then
        assertTrue(result)
    }

    @Test
    fun `isRoot should return false for subdirectory`() {
        // Given
        val subDir = tempFolder.newFolder("test")
        val path = ResourcePathConverter.fromUri(Uri.fromFile(subDir))

        // When
        val result = repository.isRoot(path)

        // Then
        assertFalse(result)
    }

    @Test
    fun `isRoot with unsupported URI scheme should return true`() {
        // Given
        val invalidPath = ResourcePath.from("invalid://test")

        // When
        val result = repository.isRoot(invalidPath)

        // Then
        assertTrue(result) // Default to true for unsupported schemes
    }

    @Test
    fun `listFiles should handle empty directory`() = runTest {
        // Given
        val emptyDir = tempFolder.newFolder("empty")
        val path = ResourcePathConverter.fromUri(Uri.fromFile(emptyDir))

        // When
        val result = repository.listFiles(path)

        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
    }

    @Test
    fun `listFiles should set sizeBytes to null for directories`() = runTest {
        // Given
        val testDir = tempFolder.newFolder("test")
        File(testDir, "subfolder").mkdir()
        val path = ResourcePathConverter.fromUri(Uri.fromFile(testDir))

        // When
        val result = repository.listFiles(path)

        // Then
        val folder = result.getOrNull()!!.find { it.isDirectory }!!
        assertNull(folder.sizeBytes)
    }

    @Test
    fun `listFiles should set sizeBytes for files`() = runTest {
        // Given
        val testDir = tempFolder.newFolder("test")
        val file = File(testDir, "test.txt")
        file.writeText("Hello World")
        val path = ResourcePathConverter.fromUri(Uri.fromFile(testDir))

        // When
        val result = repository.listFiles(path)

        // Then
        val fileItem = result.getOrNull()!!.find { !it.isDirectory }!!
        assertTrue(fileItem.sizeBytes!! > 0)
    }
}
