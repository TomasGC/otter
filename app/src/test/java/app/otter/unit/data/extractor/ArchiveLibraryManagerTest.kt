package app.otter.data.extractor

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.inject.Singleton

class ArchiveLibraryManagerTest {

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
