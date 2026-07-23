package app.otter.service

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.otter.PermissionsHelper
import app.otter.data.util.ResourcePathConverter
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
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
 * Full cancellation journey:
 * 1. Start large-archive extraction
 * 2. Wait for extraction to begin (progress event received)
 * 3. Send stop intent
 * 4. Assert: no "Extraction complete" completion notification
 * 5. Assert: some (but not all) files present (partial output)
 * 6. Assert: service no longer running (progress notification removed)
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ExtractionCancellationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var eventBus: ExtractionEventBus

    @Inject
    lateinit var extractionQueue: ExtractionQueue

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManagerCompat
    private val testFiles = mutableListOf<File>()

    companion object {
        private const val PROGRESS_NOTIFICATION_ID = 1001
        private const val COMPLETION_NOTIFICATION_ID = 1002
        // Enough files/bytes to guarantee extraction takes several seconds even on fast CI
        // runners. Progress events are throttled to ~1/second (ProgressThrottler), so "first
        // progress event" can lag well behind actual extraction — 5000 tiny files still
        // completed before the stop signal could land, racing completion against
        // cancellation. Needs a much larger margin than intuition suggests.
        private const val LARGE_FILE_COUNT = 20_000
    }

    @Before
    fun setup() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()
        notificationManager = NotificationManagerCompat.from(context)
        extractionQueue.clear()
        notificationManager.cancelAll()
        PermissionsHelper.grantStoragePermissions()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName, Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    @After
    fun teardown() {
        context.startService(ExtractionService.newStopIntent(context))
        runBlocking { delay(500) }
        testFiles.forEach { it.delete() }
        testFiles.clear()
        extractionQueue.clear()
        notificationManager.cancelAll()
    }

    @Test
    fun stopDuringExtraction_noCompletionNotification_partialOutput() = runBlocking {
        val largeZip = createLargeZip("cancel-test.zip", LARGE_FILE_COUNT)
        val archivePath = ResourcePathConverter.fromUri(Uri.fromFile(largeZip))

        // Output goes to cacheDir/<archive-name-without-ext>/ (file:// URI resolves to cacheDir parent)
        val archiveBaseName = largeZip.nameWithoutExtension
        val outputDir = File(context.cacheDir, archiveBaseName)
        outputDir.deleteRecursively()
        testFiles.add(outputDir)

        // Start extraction
        context.startService(ExtractionService.newIntent(context, archivePath, largeZip.name))

        // Wait for first progress event (extraction has started)
        withTimeout(15_000) {
            eventBus.progressState.first { it != null && it.extractedCount > 0 }
        }

        // Send stop
        context.startService(ExtractionService.newStopIntent(context))

        // Wait for service to stop
        delay(3_000)

        // Assert 1: no completion notification
        val completionNotification = notificationManager.activeNotifications
            .firstOrNull { it.id == COMPLETION_NOTIFICATION_ID }
        assertNull("Completion notification must not be posted after cancellation", completionNotification)

        // Assert 2: progress notification removed (foreground service stopped)
        val progressNotification = notificationManager.activeNotifications
            .firstOrNull { it.id == PROGRESS_NOTIFICATION_ID }
        assertNull("Progress notification should be removed after stop", progressNotification)

        // Assert 3: partial output (some files present, but not all)
        if (outputDir.exists()) {
            val extractedCount = outputDir.walk().filter { it.isFile }.count()
            assertTrue("Should have extracted some files before stop", extractedCount > 0)
            assertTrue(
                "Should NOT have extracted all $LARGE_FILE_COUNT files (partial only)",
                extractedCount < LARGE_FILE_COUNT
            )
        }
        // If outputDir doesn't exist, service stopped before writing any file — also valid

        // Cleanup handled by teardown via testFiles
    }

    private fun createLargeZip(name: String, fileCount: Int): File {
        val file = File(context.cacheDir, name)
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            repeat(fileCount) { i ->
                val entry = ZipEntry("file_$i.txt")
                zos.putNextEntry(entry)
                // Pad each file to ~5KB so extraction takes measurable time
                zos.write("Content for file $i — ".repeat(100).toByteArray())
                zos.closeEntry()
            }
        }
        testFiles.add(file)
        return file
    }
}
