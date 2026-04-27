package app.otter.data.extractor

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Instrumented tests for ArchiveLibraryManager.
 *
 * Note: Full archive opening requires native libraries (7-Zip-JBinding .so files)
 * which are only available in instrumented tests (Android runtime), not unit tests (JVM only).
 */
class ArchiveLibraryManagerInstrumentedTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val manager = ArchiveLibraryManager()

    @Test
    fun should_throw_exception_when_opening_nonexistent_file() {
        // Given
        val nonExistentFile = File("/path/to/nonexistent.7z")

        // When/Then
        assertThrows(Exception::class.java) {
            manager.openArchive(nonExistentFile)
        }
    }

    @Test
    fun should_throw_exception_when_opening_invalid_archive() {
        // Given - Create a file with invalid content
        val invalidArchive = tempFolder.newFile("invalid.7z")
        invalidArchive.writeText("This is not a valid archive")

        // When/Then - Expect any exception (SevenZipException or IllegalStateException)
        assertThrows(Exception::class.java) {
            manager.openArchive(invalidArchive)
        }
    }

    @Test
    fun should_throw_exception_when_opening_empty_file() {
        // Given
        val emptyFile = tempFolder.newFile("empty.7z")

        // When/Then
        assertThrows(Exception::class.java) {
            manager.openArchive(emptyFile)
        }
    }

    @Test
    fun should_be_thread_safe_for_concurrent_calls() {
        // Given
        val invalidArchive = tempFolder.newFile("test.7z")
        invalidArchive.writeText("invalid")

        // When - Multiple threads try to open archives simultaneously
        val threads = (1..10).map {
            Thread {
                try {
                    manager.openArchive(invalidArchive)
                } catch (e: Exception) {
                    // Expected to fail with invalid archive
                }
            }
        }

        // Then - Should not crash or deadlock
        threads.forEach { it.start() }
        threads.forEach { it.join(5000) } // 5 second timeout

        assertTrue("All threads should complete", threads.all { !it.isAlive })
    }

    @Test
    fun should_handle_multiple_sequential_calls() {
        // Given
        val invalidArchive = tempFolder.newFile("test.7z")
        invalidArchive.writeText("invalid")

        // When/Then - Multiple sequential calls should all fail consistently
        repeat(5) {
            assertThrows(Exception::class.java) {
                manager.openArchive(invalidArchive)
            }
        }
    }

    @Test
    fun manager_should_be_instantiable() {
        // When
        val manager1 = ArchiveLibraryManager()
        val manager2 = ArchiveLibraryManager()

        // Then - Should create separate instances (singleton is managed by Hilt)
        assertNotNull(manager1)
        assertNotNull(manager2)
    }
}
