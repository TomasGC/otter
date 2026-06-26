package app.otter.e2e

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.otter.PermissionsHelper
import app.otter.data.inspector.GzipInspector
import app.otter.domain.inspector.ArchiveType
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented tests for GzipInspector.
 *
 * Archives are created programmatically using Apache Commons Compress.
 * Uses context.cacheDir for temp files (no TemporaryFolder @Rule — blocked on some Android versions).
 * No native libraries required — pure Java implementation.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class GzipInspectorInstrumentedTest {

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
            ?.filter { it.name.endsWith(".gz") || it.name.endsWith(".gzip") }
            ?.forEach { it.delete() }
    }

    @Test
    fun countEntriesAlwaysReturnsOne() = runTest {
        val gzFile = createGzipInCacheDir("data.gz", "hello world")

        GzipInspector(gzFile).use { inspector ->
            assertEquals("GZIP always has exactly 1 entry", 1, inspector.countEntries())
        }
    }

    @Test
    fun entriesInnerNameStripsGzExtension() {
        val gzFile = createGzipInCacheDir("data.gz", "some content")

        GzipInspector(gzFile).use { inspector ->
            val entry = inspector.entries().first()
            assertEquals("Inner name should strip .gz extension", "data", entry.path)
        }
    }

    @Test
    fun entriesSizeBytesIsZero() {
        val gzFile = createGzipInCacheDir("report.gz", "report content here")

        GzipInspector(gzFile).use { inspector ->
            val entry = inspector.entries().first()
            assertEquals("Uncompressed size is unknown — must be 0", 0L, entry.sizeBytes)
        }
    }

    @Test
    fun entriesCompressedSizeEqualsFileSize() {
        val gzFile = createGzipInCacheDir("payload.gz", "payload data")

        GzipInspector(gzFile).use { inspector ->
            val entry = inspector.entries().first()
            assertEquals(
                "compressedSize must equal the actual file length",
                gzFile.length(),
                entry.compressedSize
            )
        }
    }

    @Test
    fun getArchiveTypeReturnsGzip() {
        val gzFile = createGzipInCacheDir("archive.gz", "content")

        GzipInspector(gzFile).use { inspector ->
            assertEquals(ArchiveType.GZIP, inspector.getArchiveType())
        }
    }

    @Test
    fun isEncryptedReturnsFalse() {
        val gzFile = createGzipInCacheDir("secure.gz", "content")

        GzipInspector(gzFile).use { inspector ->
            assertFalse("GZIP archives are never encrypted", inspector.isEncrypted())
        }
    }

    @Test
    fun entriesWithMultiPartExtension() {
        // "archive.log.gz" → inner name should be "archive.log"
        val gzFile = createGzipInCacheDir("archive.log.gz", "log content")

        GzipInspector(gzFile).use { inspector ->
            val entry = inspector.entries().first()
            assertEquals(
                "Multi-part extension: .gz should be stripped, leaving archive.log",
                "archive.log",
                entry.path
            )
        }
    }

    // region Helper

    /**
     * Creates a GZIP-compressed file in cacheDir containing [content].
     */
    private fun createGzipInCacheDir(filename: String, content: String): File {
        val file = File(context.cacheDir, filename)
        GzipCompressorOutputStream(file.outputStream()).use { gz ->
            gz.write(content.toByteArray())
        }
        return file
    }

    // endregion
}
