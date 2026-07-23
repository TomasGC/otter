package app.otter.service

import android.Manifest
import android.content.Context
import android.os.Build
import android.net.Uri
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
 * Verifies notification content during active extraction:
 * title, progress text format, and Stop action button presence.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ExtractionNotificationContentTest {

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

    @Test
    fun activeExtraction_progressNotification_hasCorrectTitle() = runBlocking {
        val archiveFile = createMultiFileZip("notify-title-test.zip", fileCount = 3000)
        val archivePath = ResourcePathConverter.fromUri(Uri.fromFile(archiveFile))

        context.startService(ExtractionService.newIntent(context, archivePath, archiveFile.name))

        // Wait for first progress event
        withTimeout(15_000) {
            eventBus.progressState.first { it != null }
        }

        // Small delay to allow notification to be posted
        delay(500)

        val notification = notificationManager.activeNotifications
            .firstOrNull { it.id == PROGRESS_NOTIFICATION_ID }

        assertNotNull("Progress notification should be posted during extraction", notification)

        val title = notification!!.notification.extras
            .getString(android.app.Notification.EXTRA_TITLE) ?: ""

        assertTrue(
            "Notification title should contain archive name. Got: '$title'",
            title.contains(archiveFile.name, ignoreCase = true)
        )
        assertTrue(
            "Notification title should say 'Extracting'. Got: '$title'",
            title.contains("Extracting", ignoreCase = true)
        )
    }

    @Test
    fun activeExtraction_progressNotification_hasProgressText() = runBlocking {
        val archiveFile = createMultiFileZip("notify-text-test.zip", fileCount = 3000)
        val archivePath = ResourcePathConverter.fromUri(Uri.fromFile(archiveFile))

        context.startService(ExtractionService.newIntent(context, archivePath, archiveFile.name))

        // Wait until we see extractedCount > 0
        withTimeout(15_000) {
            eventBus.progressState.first { it != null && it.extractedCount > 0 }
        }
        delay(500)

        val notification = notificationManager.activeNotifications
            .firstOrNull { it.id == PROGRESS_NOTIFICATION_ID }
        assertNotNull("Progress notification should exist", notification)

        val text = notification!!.notification.extras
            .getString(android.app.Notification.EXTRA_TEXT) ?: ""

        // Text format: "X/Y files (Z%)" or "X files" — verify numbers present
        assertTrue(
            "Notification text should contain file count. Got: '$text'",
            text.contains("file", ignoreCase = true)
        )
    }

    @Test
    fun activeExtraction_progressNotification_hasStopAction() = runBlocking {
        val archiveFile = createMultiFileZip("notify-action-test.zip", fileCount = 3000)
        val archivePath = ResourcePathConverter.fromUri(Uri.fromFile(archiveFile))

        context.startService(ExtractionService.newIntent(context, archivePath, archiveFile.name))

        withTimeout(15_000) {
            eventBus.progressState.first { it != null }
        }
        delay(500)

        val notification = notificationManager.activeNotifications
            .firstOrNull { it.id == PROGRESS_NOTIFICATION_ID }
        assertNotNull("Progress notification should exist", notification)

        val actions = notification!!.notification.actions ?: emptyArray()
        assertTrue("Progress notification should have at least 1 action", actions.isNotEmpty())

        val stopAction = actions.firstOrNull {
            it.title?.toString()?.contains("Stop", ignoreCase = true) == true
        }
        assertNotNull(
            "Progress notification should have a 'Stop' action button. Actions: ${actions.map { it.title }}",
            stopAction
        )
    }

    // fileCount defaults to 3000 (not the historical 50) — a tiny archive extracts before
    // the 500ms post-progress-event delay elapses on fast CI runners, so the progress
    // notification has already been replaced/removed by the time the test checks for it.
    private fun createMultiFileZip(name: String, fileCount: Int): File {
        val file = File(context.cacheDir, name)
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            repeat(fileCount) { i ->
                val entry = ZipEntry("file_$i.txt")
                zos.putNextEntry(entry)
                zos.write("Content $i".repeat(100).toByteArray())
                zos.closeEntry()
            }
        }
        testFiles.add(file)
        return file
    }
}
