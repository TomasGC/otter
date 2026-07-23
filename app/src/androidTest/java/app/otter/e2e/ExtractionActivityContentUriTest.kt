package app.otter

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * ExtractionActivity tests for content:// URI handling paths and
 * POST_NOTIFICATIONS permission-denied flow.
 *
 * Complements ExtractionActivityTest (which covers basic URI launch scenarios).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ExtractionActivityContentUriTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val testFiles = mutableListOf<File>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @org.junit.After
    fun teardown() {
        testFiles.forEach { it.delete() }
        testFiles.clear()
    }

    @Test
    fun contentUri_viaFileProvider_resolves_andExtractionProceeds() {
        val zipFile = createSmallZipInCache("content-uri-test.zip")

        val contentUri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
        } catch (e: IllegalArgumentException) {
            org.junit.Assume.assumeTrue("FileProvider not configured for cache dir", false)
            return
        }

        val intent = Intent(context, ExtractionActivity::class.java).apply {
            data = contentUri
            action = Intent.ACTION_VIEW
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val scenario = ActivityScenario.launch<ExtractionActivity>(intent)
        TimeUnit.MILLISECONDS.sleep(500)

        assertNotEquals(Lifecycle.State.DESTROYED, scenario.state)
        scenario.close()
    }

    @Test
    fun contentUri_unresolvable_cacheCopyFallback_doesNotCrash() {
        val syntheticUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3Atest.zip")

        val intent = Intent(context, ExtractionActivity::class.java).apply {
            data = syntheticUri
            action = Intent.ACTION_VIEW
            // No FLAG_GRANT_READ_URI_PERMISSION: simulates a buggy/unpermissioned sender.
            // Adding the flag would cause startActivity() to throw SecurityException when the
            // test process itself has no permission to the URI — the activity never starts.
        }

        val scenario = ActivityScenario.launch<ExtractionActivity>(intent)
        TimeUnit.MILLISECONDS.sleep(1_000)

        assertNotEquals(Lifecycle.State.DESTROYED, scenario.state)
        scenario.close()
    }

    /**
     * POST_NOTIFICATIONS must already be denied before this test's process starts --
     * revoking it from a live process causes the OS to kill that process outright
     * (ActivityManager: "Killing ... permissions revoked"), which kills the test code
     * running inside it too. manage.py brackets this test with adb pm revoke/grant
     * around its own isolated instrumentation run; see TestAction.run_permission_isolated_test.
     */
    @Test
    fun postNotificationsDenied_extractionStillStarts_noPermissionCrash() {
        val zipFile = createSmallZipInCache("permission-denied-test.zip")
        val archiveUri = Uri.fromFile(zipFile)

        val intent = Intent(context, ExtractionActivity::class.java).apply {
            data = archiveUri
            action = Intent.ACTION_VIEW
        }

        val scenario = ActivityScenario.launch<ExtractionActivity>(intent)
        TimeUnit.MILLISECONDS.sleep(2_000)

        assertNotEquals(Lifecycle.State.DESTROYED, scenario.state)
        scenario.close()
    }

    private fun createSmallZipInCache(name: String): File {
        val file = File(context.cacheDir, name)
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            val entry = ZipEntry("test.txt")
            zos.putNextEntry(entry)
            zos.write("content".toByteArray())
            zos.closeEntry()
        }
        testFiles.add(file)
        return file
    }
}
