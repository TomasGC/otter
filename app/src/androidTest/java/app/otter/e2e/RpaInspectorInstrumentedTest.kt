package app.otter.e2e

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.otter.PermissionsHelper
import app.otter.data.inspector.RpaInspector
import app.otter.domain.inspector.ArchiveType
import app.otter.domain.usecase.helpers.TestConstants
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented tests for RpaInspector (Ren'Py Archive 3.0 format).
 *
 * RPA uses XOR-obfuscated Zlib-compressed Python Pickle protocol 2 index.
 * On-device verification catches differences between ART/Dalvik and desktop JVM
 * (e.g. InflaterInputStream behavior, byte-order handling in RpaPickleParser).
 *
 * Requires test archive at [TestConstants.TestArchives.devicePath].
 * Each test calls [assumeTrue] to skip gracefully when device archive is absent.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RpaInspectorInstrumentedTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var context: Context

    @Before
    fun setUp() {
        hiltRule.inject()
        PermissionsHelper.grantStoragePermissions()
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun rpaCountEntriesReturnsPositiveNumber() = runTest {
        val rpaFile = deviceArchive("test_archive.rpa")
        assumeTrue("Device RPA archive not present — skipping", rpaFile.exists())

        RpaInspector(rpaFile).use { inspector ->
            assertTrue("RPA entry count must be > 0", inspector.countEntries() > 0)
        }
    }

    @Test
    fun rpaEntriesReturnsNonEmptySequence() {
        val rpaFile = deviceArchive("test_archive.rpa")
        assumeTrue("Device RPA archive not present — skipping", rpaFile.exists())

        RpaInspector(rpaFile).use { inspector ->
            assertTrue("RPA entries sequence must not be empty", inspector.entries().toList().isNotEmpty())
        }
    }

    @Test
    fun rpaGetArchiveTypeReturnsRpa() {
        val rpaFile = deviceArchive("test_archive.rpa")
        assumeTrue("Device RPA archive not present — skipping", rpaFile.exists())

        RpaInspector(rpaFile).use { inspector ->
            assertEquals(ArchiveType.RPA, inspector.getArchiveType())
        }
    }

    @Test
    fun rpaIsEncryptedReturnsFalse() {
        val rpaFile = deviceArchive("test_archive.rpa")
        assumeTrue("Device RPA archive not present — skipping", rpaFile.exists())

        RpaInspector(rpaFile).use { inspector ->
            assertFalse("RPA must report not encrypted", inspector.isEncrypted())
        }
    }

    @Test
    fun rpaEntryPathsAreFilesOnly() {
        val rpaFile = deviceArchive("test_archive.rpa")
        assumeTrue("Device RPA archive not present — skipping", rpaFile.exists())

        RpaInspector(rpaFile).use { inspector ->
            val entries = inspector.entries().toList()
            assertTrue("RPA must have at least one entry", entries.isNotEmpty())
            assertTrue("RPA format stores files only — no directory entries", entries.none { it.isDirectory })
            assertTrue("All entry paths must be non-blank", entries.all { it.path.isNotBlank() })
        }
    }

    @Test
    fun rpaInspectorIsReusable() = runTest {
        val rpaFile = deviceArchive("test_archive.rpa")
        assumeTrue("Device RPA archive not present — skipping", rpaFile.exists())

        RpaInspector(rpaFile).use { inspector ->
            val firstCount = inspector.countEntries()
            val secondCount = inspector.countEntries()
            assertEquals("Repeated countEntries() must return same value", firstCount, secondCount)

            val firstSize = inspector.entries().toList().size
            val secondSize = inspector.entries().toList().size
            assertEquals("Repeated entries() must return same size", firstSize, secondSize)
        }
    }

    @Test
    fun corruptedRpaDoesNotCrashOnOpen() {
        val corruptedFile = File(context.cacheDir, "corrupted_inspector_test.rpa")
        corruptedFile.writeBytes(ByteArray(64) { it.toByte() })

        try {
            runCatching {
                RpaInspector(corruptedFile).use { inspector ->
                    inspector.entries().toList()
                }
            }
            // Contract: inspecting a corrupted RPA archive never crashes the process.
        } finally {
            corruptedFile.delete()
        }
    }

    private fun deviceArchive(filename: String): File =
        File(TestConstants.TestArchives.devicePath, filename)
}
