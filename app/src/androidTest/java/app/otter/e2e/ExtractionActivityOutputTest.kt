package app.otter

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.otter.service.ExtractionQueue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/**
 * Verifies the full user journey: open archive via ExtractionActivity →
 * service extracts → files appear on disk at the expected location.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ExtractionActivityOutputTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var extractionQueue: ExtractionQueue

    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val testFiles = mutableListOf<File>()

    @Before
    fun setup() {
        hiltRule.inject()
        extractionQueue.clear()
        PermissionsHelper.grantStoragePermissions()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName, Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    @After
    fun teardown() {
        testFiles.forEach { it.deleteRecursively() }
        testFiles.clear()
        extractionQueue.clear()
    }

    @Test
    fun openFileUriArchiveViaActivity_extractsFilesToDisk() = runBlocking {
        val zipFile = createSmallZip("activity-output-test.zip")

        // For file:// URIs in cacheDir, ExtractionDestinationResolver resolves
        // output to the same directory: cacheDir/activity-output-test/
        val expectedOutputDir = File(context.cacheDir, "activity-output-test")
        expectedOutputDir.deleteRecursively()
        testFiles.add(expectedOutputDir)

        val intent = Intent(context, ExtractionActivity::class.java).apply {
            data = Uri.fromFile(zipFile)
            action = Intent.ACTION_VIEW
        }

        val scenario = ActivityScenario.launch<ExtractionActivity>(intent)

        // Poll output dir: avoids race where fast extraction emits completeEvents
        // before SharedFlow(replay=0) subscriber starts
        withTimeout(30_000) {
            while (expectedOutputDir.walk().filter { it.isFile }.none()) {
                delay(200)
            }
        }

        assertTrue("Output directory must exist after activity-triggered extraction", expectedOutputDir.exists())
        val files = expectedOutputDir.walk().filter { it.isFile }.toList()
        assertTrue("At least 1 file must be extracted to the output directory", files.isNotEmpty())

        assertNotEquals(Lifecycle.State.DESTROYED, scenario.state)
        scenario.close()
    }

    private fun createSmallZip(name: String): File {
        val file = File(context.cacheDir, name)
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            val entry = ZipEntry("hello.txt")
            zos.putNextEntry(entry)
            zos.write("Hello from Otter instrumented test".toByteArray())
            zos.closeEntry()
        }
        testFiles.add(file)
        return file
    }
}
