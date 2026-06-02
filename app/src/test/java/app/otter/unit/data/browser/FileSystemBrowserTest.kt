package app.otter.data.browser

import android.content.Context
import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.BrowseResult
import app.otter.domain.model.ResourcePath
import app.otter.util.MimeTypeUtil
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FileSystemBrowserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var mimeTypeUtil: MimeTypeUtil
    private lateinit var browser: FileSystemBrowser

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        mimeTypeUtil = MimeTypeUtil()
        browser = FileSystemBrowser(context, mimeTypeUtil)
    }

    @Test
    fun `browse returns FileSystemDirectory for directory`() = runTest {
        // Given
        val dir = tempFolder.newFolder("test-dir")
        File(dir, "sub1").apply { mkdir() }
        File(dir, "sub2").apply { mkdir() }
        File(dir, "file1.txt").apply { writeText("content1") }

        val path = ResourcePath.FileSystem(dir.absolutePath)

        // When
        val result = browser.browse(path)

        // Then
        assertTrue(result.isSuccess)
        val browseResult = result.getOrThrow()
        assertTrue(browseResult is BrowseResult.Complete)

        val items = (browseResult as BrowseResult.Complete).items
        assertEquals(3, items.size)

        val directories = items.filterIsInstance<BrowsableItem.FileSystemDirectory>()
        assertEquals(2, directories.size)
        assertTrue(directories.any { it.name == "sub1" })
        assertTrue(directories.any { it.name == "sub2" })

        val files = items.filterIsInstance<BrowsableItem.FileSystemFile>()
        assertEquals(1, files.size)
        assertEquals("file1.txt", files.first().name)
    }

    @Test
    fun `browse returns FileSystemFile for regular file`() = runTest {
        // Given
        val dir = tempFolder.newFolder("test-dir")
        File(dir, "document.pdf").apply {
            writeText("PDF content")
        }

        val path = ResourcePath.FileSystem(dir.absolutePath)

        // When
        val result = browser.browse(path)

        // Then
        assertTrue(result.isSuccess)
        val browseResult = result.getOrThrow()
        assertTrue(browseResult is BrowseResult.Complete)

        val items = (browseResult as BrowseResult.Complete).items
        assertEquals(1, items.size)

        val item = items.first()
        assertTrue(item is BrowsableItem.FileSystemFile)
        val fileItem = item as BrowsableItem.FileSystemFile
        assertEquals("document.pdf", fileItem.name)
        assertTrue(fileItem.sizeBytes > 0)
    }

    @Test
    fun `browse returns ArchiveFile for zip file`() = runTest {
        // Given
        val dir = tempFolder.newFolder("test-dir")
        File(dir, "archive.zip").apply { writeText("ZIP content") }
        File(dir, "archive.rar").apply { writeText("RAR content") }
        File(dir, "archive.7z").apply { writeText("7Z content") }
        File(dir, "archive.tar").apply { writeText("TAR content") }
        File(dir, "archive.gz").apply { writeText("GZ content") }
        File(dir, "archive.rpa").apply { writeText("RPA content") }

        val path = ResourcePath.FileSystem(dir.absolutePath)

        // When
        val result = browser.browse(path)

        // Then
        assertTrue(result.isSuccess)
        val browseResult = result.getOrThrow()
        assertTrue(browseResult is BrowseResult.Complete)

        val items = (browseResult as BrowseResult.Complete).items
        assertEquals(6, items.size)

        val archiveFiles = items.filterIsInstance<BrowsableItem.ArchiveFile>()
        assertEquals(6, archiveFiles.size)

        assertTrue(archiveFiles.any { it.name == "archive.zip" })
        assertTrue(archiveFiles.any { it.name == "archive.rar" })
        assertTrue(archiveFiles.any { it.name == "archive.7z" })
        assertTrue(archiveFiles.any { it.name == "archive.tar" })
        assertTrue(archiveFiles.any { it.name == "archive.gz" })
        assertTrue(archiveFiles.any { it.name == "archive.rpa" })
    }

    @Test
    fun `getParent returns parent directory`() {
        // Given
        val dir = tempFolder.newFolder("test-dir")
        val subDir = File(dir, "sub-dir").apply { mkdir() }
        val path = ResourcePath.FileSystem(subDir.absolutePath)

        // When
        val parent = browser.getParent(path)

        // Then
        assertTrue(parent is ResourcePath.FileSystem)
        val parentPath = parent as ResourcePath.FileSystem
        assertEquals(dir.absolutePath, parentPath.path)
    }

    @Test
    fun `getParent returns null for root`() {
        // Given
        val root = File("/")
        val path = ResourcePath.FileSystem(root.absolutePath)

        // When
        val parent = browser.getParent(path)

        // Then
        assertNull(parent)
    }

    @Test
    fun `isRoot returns true for root path`() {
        // Given
        val root = File("/")
        val path = ResourcePath.FileSystem(root.absolutePath)

        // When
        val result = browser.isRoot(path)

        // Then
        assertTrue(result)
    }

    @Test
    fun `isRoot returns false for non-root path`() {
        // Given
        val dir = tempFolder.newFolder("test-dir")
        val path = ResourcePath.FileSystem(dir.absolutePath)

        // When
        val result = browser.isRoot(path)

        // Then
        assertFalse(result)
    }
}
