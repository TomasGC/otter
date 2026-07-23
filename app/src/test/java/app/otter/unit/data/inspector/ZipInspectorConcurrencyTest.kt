package app.otter.data.inspector

import app.otter.test.ArchiveTestHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ZipInspectorConcurrencyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var zipFile: File

    @Before
    fun setup() {
        zipFile = tempFolder.newFile("concurrent.zip")
        ArchiveTestHelper.createZipArchive(
            zipFile,
            mapOf("a.txt" to "A", "b.txt" to "B", "c.txt" to "C")
        )
    }

    @Test
    fun `concurrent entries and countEntries calls return consistent results`() = runTest {
        val inspector = ZipInspector(zipFile)

        val results = (1..10).map {
            async {
                val count = inspector.countEntries()
                val entryCount = inspector.entries().count()
                Pair(count, entryCount)
            }
        }.awaitAll()

        inspector.close()

        results.forEach { (count, entryCount) ->
            assertEquals("countEntries should return 3", 3, count)
            assertEquals("entries().count() should return 3", 3, entryCount)
        }
    }

    @Test
    fun `concurrent entries calls do not corrupt each other`() = runTest {
        val inspector = ZipInspector(zipFile)

        val allNames = (1..5).map {
            async { inspector.entries().map { e -> e.path }.toSet() }
        }.awaitAll()

        inspector.close()

        val expected = setOf("a.txt", "b.txt", "c.txt")
        allNames.forEach { names ->
            assertEquals("All concurrent reads must see same entries", expected, names)
        }
    }
}
