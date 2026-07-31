package app.otter.integration.data.repository

import app.otter.data.browser.FileSystemBrowser
import app.otter.data.extractor.ArchiveLibraryManager
import app.otter.data.inspector.ArchiveInspectorFactory
import app.otter.data.repository.ItemBrowserRepositoryImpl
import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.BrowseResult
import app.otter.domain.model.ResourcePath
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

/**
 * Drives ItemBrowserRepositoryImpl through the real ArchiveInspectorFactory dispatch
 * (factory -> TarInspector/GzipInspector -> ArchiveBrowser), unlike ItemBrowserRepositoryImplTest
 * which fully mocks ArchiveInspector. ArchiveLibraryManager is mocked since TAR/GZIP dispatch
 * never touches it (only RAR/7z do).
 */
class ItemBrowserRepositoryRealInspectorMockIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val repository = ItemBrowserRepositoryImpl(
        fileSystemBrowser = mockk<FileSystemBrowser>(),
        inspectorFactory = ArchiveInspectorFactory(mockk<ArchiveLibraryManager>())
    )

    @Test
    fun `browse dispatches a real TAR file through TarInspector`() = runTest {
        val tar = createTar("archive.tar", listOf("readme.txt" to "hello", "docs/notes.txt" to "world"))
        val path = ResourcePath.ArchiveEntry(archivePath = tar.absolutePath, entryPath = "")

        val result = repository.browse(path, offset = 0, limit = 100)

        assertTrue(result.isSuccess)
        val browseResult = result.getOrNull()
        assertTrue(browseResult is BrowseResult.Complete)
        val names = browseResult!!.items.map { it.name }
        assertTrue(names.contains("readme.txt"))
        assertTrue(names.contains("docs"))
        assertTrue(browseResult.items.any { it is BrowsableItem.ArchiveDirectory && it.name == "docs" })
    }

    @Test
    fun `browse dispatches a real GZIP file through GzipInspector`() = runTest {
        val gz = createGzip("notes.txt.gz", "gzip content")
        val path = ResourcePath.ArchiveEntry(archivePath = gz.absolutePath, entryPath = "")

        val result = repository.browse(path, offset = 0, limit = 100)

        assertTrue(result.isSuccess)
        val browseResult = result.getOrNull()
        assertTrue(browseResult is BrowseResult.Complete)
        assertEquals(1, browseResult!!.items.size)
        assertEquals("notes.txt", browseResult.items.first().name)
    }

    private fun createTar(name: String, entries: List<Pair<String, String>>): File {
        val file = tempFolder.newFile(name)
        TarArchiveOutputStream(FileOutputStream(file)).use { tar ->
            for ((entryName, content) in entries) {
                val bytes = content.toByteArray()
                val entry = TarArchiveEntry(entryName)
                entry.size = bytes.size.toLong()
                tar.putArchiveEntry(entry)
                tar.write(bytes)
                tar.closeArchiveEntry()
            }
        }
        return file
    }

    private fun createGzip(name: String, content: String): File {
        val file = tempFolder.newFile(name)
        GzipCompressorOutputStream(FileOutputStream(file)).use { it.write(content.toByteArray()) }
        return file
    }
}
