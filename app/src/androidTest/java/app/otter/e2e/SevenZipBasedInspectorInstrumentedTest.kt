package app.otter.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otter.PermissionsHelper
import app.otter.data.extractor.ArchiveLibraryManager
import app.otter.data.inspector.SevenZipBasedInspector
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
import javax.inject.Inject

/**
 * Instrumented tests for SevenZipBasedInspector (RAR and 7z formats).
 *
 * Requires 7-Zip-JBinding native .so files — only works on Android device/emulator.
 * Uses device archives at [TestConstants.TestArchives.devicePath].
 *
 * Each test calls [assumeTrue] to skip gracefully when the device archive is absent,
 * so the suite never fails due to missing test data.
 *
 * [ArchiveLibraryManager] is injected via Hilt (@Singleton) to ensure
 * the native library is initialized exactly once per test run.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SevenZipBasedInspectorInstrumentedTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var libraryManager: ArchiveLibraryManager

    @Before
    fun setUp() {
        hiltRule.inject()
        PermissionsHelper.grantStoragePermissions()
    }

    // region RAR

    @Test
    fun rarCountEntriesReturnsPositiveNumber() = runTest {
        val rarFile = deviceArchive("test_archive.rar")
        assumeTrue("Device RAR archive not present — skipping", rarFile.exists())

        SevenZipBasedInspector(rarFile, ArchiveType.RAR, libraryManager).use { inspector ->
            assertTrue("RAR entry count must be > 0", inspector.countEntries() > 0)
        }
    }

    @Test
    fun rarEntriesReturnsNonEmptySequence() {
        val rarFile = deviceArchive("test_archive.rar")
        assumeTrue("Device RAR archive not present — skipping", rarFile.exists())

        SevenZipBasedInspector(rarFile, ArchiveType.RAR, libraryManager).use { inspector ->
            assertTrue("RAR entries sequence must not be empty", inspector.entries().toList().isNotEmpty())
        }
    }

    @Test
    fun rarGetArchiveTypeReturnsRar() {
        val rarFile = deviceArchive("test_archive.rar")
        assumeTrue("Device RAR archive not present — skipping", rarFile.exists())

        SevenZipBasedInspector(rarFile, ArchiveType.RAR, libraryManager).use { inspector ->
            assertEquals(ArchiveType.RAR, inspector.getArchiveType())
        }
    }

    @Test
    fun rarIsEncryptedReturnsFalseForUnencryptedArchive() {
        val rarFile = deviceArchive("test_archive.rar")
        assumeTrue("Device RAR archive not present — skipping", rarFile.exists())

        SevenZipBasedInspector(rarFile, ArchiveType.RAR, libraryManager).use { inspector ->
            assertFalse("Unencrypted RAR must return false for isEncrypted()", inspector.isEncrypted())
        }
    }

    @Test
    fun corruptedRarDoesNotCrashOnOpenOrCountEntries() = runTest {
        // Real 7-Zip-JBinding native library against a genuinely corrupted RAR — only
        // previously proven via a mocked ArchiveLibraryManager, never the real native path.
        val rarFile = deviceArchive("corrupted_test_archive.rar")
        assumeTrue("Device corrupted RAR archive not present — skipping", rarFile.exists())

        runCatching {
            SevenZipBasedInspector(rarFile, ArchiveType.RAR, libraryManager).use { inspector ->
                inspector.countEntries()
            }
        }
        // No assertion on the outcome itself (open/count may throw or may tolerate corruption,
        // same accepted native-library leniency as extraction) — the contract under test is
        // that inspecting a corrupted archive never crashes the process.
    }

    // endregion

    // region 7z

    @Test
    fun sevenZipCountEntriesReturnsPositiveNumber() = runTest {
        val sevenZipFile = deviceArchive("test_archive.7z")
        assumeTrue("Device 7z archive not present — skipping", sevenZipFile.exists())

        SevenZipBasedInspector(sevenZipFile, ArchiveType.SEVEN_ZIP, libraryManager).use { inspector ->
            assertTrue("7z entry count must be > 0", inspector.countEntries() > 0)
        }
    }

    @Test
    fun sevenZipEntriesReturnsNonEmptySequence() {
        val sevenZipFile = deviceArchive("test_archive.7z")
        assumeTrue("Device 7z archive not present — skipping", sevenZipFile.exists())

        SevenZipBasedInspector(sevenZipFile, ArchiveType.SEVEN_ZIP, libraryManager).use { inspector ->
            assertTrue("7z entries sequence must not be empty", inspector.entries().toList().isNotEmpty())
        }
    }

    @Test
    fun sevenZipGetArchiveTypeReturnsSevenZip() {
        val sevenZipFile = deviceArchive("test_archive.7z")
        assumeTrue("Device 7z archive not present — skipping", sevenZipFile.exists())

        SevenZipBasedInspector(sevenZipFile, ArchiveType.SEVEN_ZIP, libraryManager).use { inspector ->
            assertEquals(ArchiveType.SEVEN_ZIP, inspector.getArchiveType())
        }
    }

    @Test
    fun sevenZipIsEncryptedReturnsFalseForUnencryptedArchive() {
        val sevenZipFile = deviceArchive("test_archive.7z")
        assumeTrue("Device 7z archive not present — skipping", sevenZipFile.exists())

        SevenZipBasedInspector(sevenZipFile, ArchiveType.SEVEN_ZIP, libraryManager).use { inspector ->
            assertFalse("Unencrypted 7z must return false for isEncrypted()", inspector.isEncrypted())
        }
    }

    @Test
    fun corrupted7zDoesNotCrashOnOpenOrCountEntries() = runTest {
        // Real 7-Zip-JBinding native library against a genuinely corrupted 7z — only
        // previously proven via a mocked ArchiveLibraryManager, never the real native path.
        val sevenZipFile = deviceArchive("corrupted_test_archive.7z")
        assumeTrue("Device corrupted 7z archive not present — skipping", sevenZipFile.exists())

        runCatching {
            SevenZipBasedInspector(sevenZipFile, ArchiveType.SEVEN_ZIP, libraryManager).use { inspector ->
                inspector.countEntries()
            }
        }
        // No assertion on the outcome itself — the contract under test is that inspecting a
        // corrupted archive never crashes the process, matching extraction's accepted leniency.
    }

    // endregion

    // region Helper

    private fun deviceArchive(filename: String): File =
        File(TestConstants.TestArchives.devicePath, filename)

    // endregion
}
