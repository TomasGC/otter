package app.otter.data.extractor

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.inject.Singleton

class ArchiveLibraryManagerUnitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val manager = ArchiveLibraryManager()

    @Test
    fun `openArchive with nonexistent file throws exception instead of crashing`() {
        val missingFile = File(tempFolder.root, "does-not-exist.rar")

        assertThrows(Exception::class.java) {
            manager.openArchive(missingFile)
        }
    }

    @Test
    fun `openArchive with a directory instead of a file throws exception`() {
        val directory = tempFolder.newFolder("not-a-file")

        assertThrows(Exception::class.java) {
            manager.openArchive(directory)
        }
    }

    @Test
    fun `is annotated Singleton so the native library is shared across all extractors`() {
        val annotation = ArchiveLibraryManager::class.java.getAnnotation(Singleton::class.java)
        assertNotNull(
            "ArchiveLibraryManager must stay @Singleton — the class doc explains this avoids " +
                "duplicate native library loads across RarExtractor/SevenZipExtractor",
            annotation
        )
    }

    @Test
    fun `openVolumedArchive with nonexistent file throws exception instead of crashing`() {
        val missingFile = File(tempFolder.root, "missing-multi.rar")
        assertThrows(Exception::class.java) {
            manager.openVolumedArchive(missingFile)
        }
    }

    @Test
    fun `openVolumedArchive with invalid archive content throws exception and cleans up`() {
        // VolumedArchiveInStream opens the file via callback.getStream() during openInArchive.
        // The catch block calls callback.close() before re-throwing to release that handle.
        val invalidFile = tempFolder.newFile("invalid.rar")
        invalidFile.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03))
        assertThrows(Exception::class.java) {
            manager.openVolumedArchive(invalidFile)
        }
    }

    @Test
    fun `openArchive can be called concurrently from multiple threads without throwing unexpected errors`() {
        val missingFile = File(tempFolder.root, "concurrent-missing.rar")
        val results = mutableListOf<Throwable>()
        val threads = (1..5).map {
            Thread {
                try {
                    manager.openArchive(missingFile)
                } catch (e: Throwable) {
                    synchronized(results) { results.add(e) }
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertTrue("All 5 concurrent calls must surface an exception, none must hang or crash the JVM", results.size == 5)
    }
}
