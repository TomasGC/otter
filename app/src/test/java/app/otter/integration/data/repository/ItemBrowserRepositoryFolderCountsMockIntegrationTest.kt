package app.otter.integration.data.repository

import app.otter.data.browser.FileSystemBrowser
import app.otter.data.inspector.ArchiveInspectorFactory
import app.otter.data.repository.ItemBrowserRepositoryImpl
import app.otter.domain.model.FolderCounts
import app.otter.util.MimeTypeUtil
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ItemBrowserRepositoryFolderCountsMockIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val inspectorFactory = mockk<ArchiveInspectorFactory>()
    private val repository = ItemBrowserRepositoryImpl(
        fileSystemBrowser = FileSystemBrowser(MimeTypeUtil()),
        inspectorFactory = inspectorFactory
    )

    @Test
    fun `getFolderCounts returns correct counts from real file system`() = runTest {
        val dir = tempFolder.newFolder("test-dir")
        File(dir, "sub1").mkdir()
        File(dir, "sub2").mkdir()
        tempFolder.newFile("test-dir/file1.txt")
        tempFolder.newFile("test-dir/file2.pdf")
        tempFolder.newFile("test-dir/file3.mp3")

        val result = repository.getFolderCounts(dir.absolutePath)

        assertEquals(FolderCounts(folderCount = 2, fileCount = 3), result)
    }
}
