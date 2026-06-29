package app.otter.e2e

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.otter.PermissionsHelper
import app.otter.data.inspector.TarInspector
import app.otter.domain.inspector.ArchiveType
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented tests for TarInspector.
 *
 * Archives are created programmatically using Apache Commons Compress.
 * Uses context.cacheDir for temp files (no TemporaryFolder @Rule — blocked on some Android versions).
 * No native libraries required — pure Java implementation.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TarInspectorInstrumentedTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var context: Context

    @Before
    fun setUp() {
        hiltRule.inject()
        PermissionsHelper.grantStoragePermissions()
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @After
    fun tearDown() {
        context.cacheDir.listFiles()
            ?.filter { it.name.endsWith(".tar") || it.name.endsWith(".tar.gz") || it.name.endsWith(".tar.bz2") }
            ?.forEach { it.delete() }
    }

    // region TAR

    @Test
    fun tarEntriesCountAndPaths() = runTest {
        val tarFile = createTarInCacheDir(
            "test_count.tar",
            listOf(
                "alpha.txt" to "content-alpha",
                "beta.txt" to "content-beta",
                "gamma.txt" to "content-gamma"
            )
        )

        TarInspector(tarFile, ArchiveType.TAR).use { inspector ->
            assertEquals("Expected 3 entries", 3, inspector.countEntries())

            val paths = inspector.entries().map { it.path }.toList()
            assertTrue("Missing alpha.txt", paths.contains("alpha.txt"))
            assertTrue("Missing beta.txt", paths.contains("beta.txt"))
            assertTrue("Missing gamma.txt", paths.contains("gamma.txt"))
        }
    }

    @Test
    fun tarDirectoryEntryRecognized() = runTest {
        val tarFile = createTarInCacheDir(
            "test_dir.tar",
            listOf("subdir/" to null, "subdir/file.txt" to "hello")
        )

        TarInspector(tarFile, ArchiveType.TAR).use { inspector ->
            val entries = inspector.entries().toList()
            val dirEntry = entries.firstOrNull { it.path == "subdir/" }
            assertTrue("Directory entry must exist", dirEntry != null)
            assertTrue("subdir/ must be recognized as directory", dirEntry!!.isDirectory)
        }
    }

    @Test
    fun tarGetArchiveTypeReturnsTar() {
        val tarFile = createTarInCacheDir("test_type.tar", listOf("f.txt" to "x"))

        TarInspector(tarFile, ArchiveType.TAR).use { inspector ->
            assertEquals(ArchiveType.TAR, inspector.getArchiveType())
        }
    }

    // endregion

    // region TAR.GZ

    @Test
    fun tarGzEntriesCorrect() = runTest {
        val tarGzFile = createTarGzInCacheDir(
            "test_count.tar.gz",
            listOf("one.txt" to "content-one", "two.txt" to "content-two")
        )

        TarInspector(tarGzFile, ArchiveType.TAR_GZ).use { inspector ->
            assertEquals("Expected 2 entries", 2, inspector.countEntries())

            val firstPath = inspector.entries().first().path
            assertTrue(
                "First path should be one.txt or two.txt",
                firstPath == "one.txt" || firstPath == "two.txt"
            )
        }
    }

    @Test
    fun tarGzIsEncryptedReturnsFalse() {
        val tarGzFile = createTarGzInCacheDir("test_enc.tar.gz", listOf("f.txt" to "data"))

        TarInspector(tarGzFile, ArchiveType.TAR_GZ).use { inspector ->
            assertFalse("TAR_GZ must report not encrypted", inspector.isEncrypted())
        }
    }

    // endregion

    // region TAR.BZ2

    @Test
    fun tarBz2EntriesCorrect() = runTest {
        val tarBz2File = createTarBz2InCacheDir(
            "test_count.tar.bz2",
            listOf("a.txt" to "content-a", "b.txt" to "content-b")
        )

        TarInspector(tarBz2File, ArchiveType.TAR_BZ2).use { inspector ->
            assertEquals("Expected 2 entries", 2, inspector.countEntries())

            val paths = inspector.entries().map { it.path }.toList()
            assertTrue("Missing a.txt", paths.contains("a.txt"))
            assertTrue("Missing b.txt", paths.contains("b.txt"))
        }
    }

    @Test
    fun tarBz2IsEncryptedReturnsFalse() {
        val tarBz2File = createTarBz2InCacheDir("test_enc.tar.bz2", listOf("f.txt" to "data"))

        TarInspector(tarBz2File, ArchiveType.TAR_BZ2).use { inspector ->
            assertFalse("TAR_BZ2 must report not encrypted", inspector.isEncrypted())
        }
    }

    // endregion

    // region Helpers

    /**
     * Creates a plain TAR archive in cacheDir.
     * @param entries List of (entryName, content) pairs. null content = directory entry.
     */
    private fun createTarInCacheDir(filename: String, entries: List<Pair<String, String?>>): File {
        val file = File(context.cacheDir, filename)
        TarArchiveOutputStream(file.outputStream()).use { tar ->
            writeTarEntries(tar, entries)
        }
        return file
    }

    /**
     * Creates a GZIP-compressed TAR archive in cacheDir.
     */
    private fun createTarGzInCacheDir(filename: String, entries: List<Pair<String, String?>>): File {
        val file = File(context.cacheDir, filename)
        TarArchiveOutputStream(GzipCompressorOutputStream(file.outputStream())).use { tar ->
            writeTarEntries(tar, entries)
        }
        return file
    }

    /**
     * Creates a BZip2-compressed TAR archive in cacheDir.
     */
    private fun createTarBz2InCacheDir(filename: String, entries: List<Pair<String, String?>>): File {
        val file = File(context.cacheDir, filename)
        TarArchiveOutputStream(BZip2CompressorOutputStream(file.outputStream())).use { tar ->
            writeTarEntries(tar, entries)
        }
        return file
    }

    private fun writeTarEntries(tar: TarArchiveOutputStream, entries: List<Pair<String, String?>>) {
        entries.forEach { (name, content) ->
            val entry = TarArchiveEntry(name)
            if (content != null) {
                val bytes = content.toByteArray()
                entry.size = bytes.size.toLong()
                tar.putArchiveEntry(entry)
                tar.write(bytes)
            } else {
                tar.putArchiveEntry(entry)
            }
            tar.closeArchiveEntry()
        }
    }

    // endregion
}
