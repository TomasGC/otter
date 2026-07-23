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
import kotlinx.coroutines.launch
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
 * ExtractionService lifecycle tests: failure notification, stop-during-extraction,
 * and queue resilience (failed first item → second item still processes).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ExtractionServiceLifecycleTest {

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
        private const val COMPLETION_NOTIFICATION_ID = 1002
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
        val stopIntent = ExtractionService.newStopIntent(context)
        context.startService(stopIntent)
        runBlocking { delay(500) }
        testFiles.forEach { it.delete() }
        testFiles.clear()
        extractionQueue.clear()
        notificationManager.cancelAll()
    }

    // ========== Test 1: Failure notification ==========

    @Test
    fun failureNotification_corruptedArchive_postsFailedNotification() = runBlocking {
        val corruptedFile = createCorruptedZip("corrupted-for-notify.zip")
        val archivePath = ResourcePathConverter.fromUri(Uri.fromFile(corruptedFile))

        context.startService(ExtractionService.newIntent(context, archivePath, corruptedFile.name))

        // Wait for service to process (failure is fast for corrupted files)
        delay(5_000)

        val notification = notificationManager.activeNotifications
            .firstOrNull { it.id == COMPLETION_NOTIFICATION_ID }

        assertNotNull("Failure notification should be posted", notification)
        val title = notification!!.notification.extras
            .getString(android.app.Notification.EXTRA_TITLE) ?: ""
        assertTrue(
            "Notification title should indicate failure, got: '$title'",
            title.contains("failed", ignoreCase = true)
        )
    }

    // ========== Test 2: Stop during active extraction ==========

    @Test
    fun stopDuringExtraction_largeArchive_abortsWithoutCompletionNotification() = runBlocking {
        // Large archive takes time to extract — gives us a window to stop
        val largeFile = createLargeZip("large-for-stop.zip", fileCount = 500)
        val archivePath = ResourcePathConverter.fromUri(Uri.fromFile(largeFile))

        context.startService(ExtractionService.newIntent(context, archivePath, largeFile.name))

        // Wait until extraction has started (first progress event received)
        withTimeout(10_000) {
            eventBus.progressState.first { it != null }
        }

        // Send stop intent
        context.startService(ExtractionService.newStopIntent(context))

        // Wait for service to stop
        delay(3_000)

        // Assert: no "Extraction complete" notification
        val completionNotification = notificationManager.activeNotifications
            .firstOrNull { it.id == COMPLETION_NOTIFICATION_ID }

        // The completion notification (if any) must NOT say "complete"
        if (completionNotification != null) {
            val title = completionNotification.notification.extras
                .getString(android.app.Notification.EXTRA_TITLE) ?: ""
            assertFalse(
                "Completion notification should not say 'complete' after stop, got: '$title'",
                title.contains("complete", ignoreCase = true)
            )
        }
        // Either no notification posted, or the posted notification reflects the stop — both are valid
    }

    // ========== Test 3: Queue resilience ==========

    @Test
    fun queueResilience_firstFails_secondSucceeds() = runBlocking {
        val corruptedFile = createCorruptedZip("queue-fail-1.zip")
        val validFile = createSmallValidZip("queue-ok-2.zip")

        // Enqueue second file BEFORE starting service
        extractionQueue.enqueueAll(listOf(
            ExtractionQueue.ExtractionTask(
                ResourcePathConverter.fromUri(Uri.fromFile(validFile)),
                validFile.name
            )
        ))

        // Track which file names appear in progress events
        val processedNames = mutableSetOf<String>()
        val collectionJob = launch {
            eventBus.progressState.collect { event ->
                event?.fileName?.let { processedNames.add(it) }
            }
        }

        // Start service with corrupted file (first in queue — will fail)
        val corruptedPath = ResourcePathConverter.fromUri(Uri.fromFile(corruptedFile))

        var completionReceived = false
        val completionJob = launch {
            eventBus.completeEvents.first()
            completionReceived = true
        }

        context.startService(ExtractionService.newIntent(context, corruptedPath, corruptedFile.name))

        // Wait for both files to be processed (timeout generous for slow emulators)
        withTimeout(30_000) {
            while (!completionReceived) delay(100)
        }
        completionJob.cancel()

        collectionJob.cancel()

        // The valid second file must have been processed
        assertTrue(
            "Second (valid) file should have been processed after first (corrupted) failed. Processed: $processedNames",
            processedNames.contains(validFile.name)
        )
    }

    // ========== Test 4: Success notification ==========

    @Test
    fun successfulExtraction_postsCompletionNotificationWithCorrectContent() = runBlocking {
        val validFile = createSmallValidZip("success-notify.zip")
        val archivePath = ResourcePathConverter.fromUri(Uri.fromFile(validFile))

        context.startService(ExtractionService.newIntent(context, archivePath, validFile.name))

        withTimeout(15_000) {
            eventBus.completeEvents.first()
        }
        delay(500)

        val notification = notificationManager.activeNotifications
            .firstOrNull { it.id == COMPLETION_NOTIFICATION_ID }

        assertNotNull("Success completion notification should be posted", notification)

        val title = notification!!.notification.extras
            .getString(android.app.Notification.EXTRA_TITLE) ?: ""
        assertTrue(
            "Notification title should indicate success. Got: '$title'",
            title.contains("complete", ignoreCase = true)
        )

        val text = notification.notification.extras
            .getString(android.app.Notification.EXTRA_TEXT) ?: ""
        assertTrue(
            "Notification text should reference archive name or file count. Got: '$text'",
            text.contains(validFile.name, ignoreCase = true) || text.contains("file", ignoreCase = true)
        )
    }

    // ========== Helpers ==========

    private fun createCorruptedZip(name: String): File {
        val file = File(context.cacheDir, name)
        file.writeBytes(ByteArray(64) { it.toByte() }) // Random garbage
        testFiles.add(file)
        return file
    }

    private fun createLargeZip(name: String, fileCount: Int): File {
        val file = File(context.cacheDir, name)
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            repeat(fileCount) { i ->
                val entry = ZipEntry("file_$i.txt")
                zos.putNextEntry(entry)
                zos.write("Content for file $i — padding to make it bigger".repeat(10).toByteArray())
                zos.closeEntry()
            }
        }
        testFiles.add(file)
        return file
    }

    private fun createSmallValidZip(name: String): File {
        val file = File(context.cacheDir, name)
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            val entry = ZipEntry("ok.txt")
            zos.putNextEntry(entry)
            zos.write("Valid content".toByteArray())
            zos.closeEntry()
        }
        testFiles.add(file)
        return file
    }
}
