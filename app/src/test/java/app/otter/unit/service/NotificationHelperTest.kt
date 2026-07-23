package app.otter.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NotificationHelperTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var helper: NotificationHelper

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        notificationManager = mockk(relaxed = true)
        helper = NotificationHelper(context, notificationManager)
    }

    @Test
    fun `createNotificationChannel creates channel with correct id and importance`() {
        helper.createNotificationChannel()
        verify {
            notificationManager.createNotificationChannel(match {
                it.id == NotificationHelper.CHANNEL_ID &&
                it.importance == android.app.NotificationManager.IMPORTANCE_LOW
            })
        }
    }

    @Test
    fun `should create progress notification with indeterminate progress`() {
        val notification = helper.createProgressNotification("test.zip", 0)
        assertNotNull(notification)
        assertEquals("Extracting test.zip", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Preparing extraction...", notification.extras.getString(Notification.EXTRA_TEXT))
    }

    @Test
    fun `should create progress notification with percentage`() {
        val notification = helper.createProgressNotification("test.zip", 50, 50, 100)
        assertNotNull(notification)
        assertEquals("50/100 files (50%)", notification.extras.getString(Notification.EXTRA_TEXT))
    }

    @Test
    fun `should create progress notification with file count only`() {
        val notification = helper.createProgressNotification("test.zip", 0, 10, 0)
        assertNotNull(notification)
        assertEquals("10 files", notification.extras.getString(Notification.EXTRA_TEXT))
    }

    @Test
    fun `should create success notification`() {
        val notification = helper.createSuccessNotification("test.zip", 42)
        assertNotNull(notification)
        assertEquals("Extraction Complete", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("42 files extracted from test.zip", notification.extras.getString(Notification.EXTRA_TEXT))
    }

    @Test
    fun `should create failure notification`() {
        val notification = helper.createFailureNotification("test.zip", "Corrupted archive")
        assertNotNull(notification)
        assertEquals("Extraction Failed", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Failed to extract test.zip: Corrupted archive",
            notification.extras.getString(Notification.EXTRA_TEXT))
    }

    @Test
    fun `should update notification via manager`() {
        // Given
        val notification = helper.createProgressNotification("test.zip", 50)

        // When
        helper.updateNotification(notification)

        // Then
        verify { notificationManager.notify(NotificationHelper.NOTIFICATION_ID, notification) }
    }

    @Test
    fun `should handle very long file names in progress notification`() {
        // Given
        val longFileName = "a".repeat(200) + ".zip"

        // When
        val notification = helper.createProgressNotification(longFileName, 25, 25, 100)

        // Then
        assertNotNull("Notification should not be null", notification)
    }

    @Test
    fun `should handle zero extracted files`() {
        val notification = helper.createProgressNotification("test.zip", 0, 0, 0)
        assertNotNull(notification)
        assertEquals("Preparing extraction...", notification.extras.getString(Notification.EXTRA_TEXT))
    }

    @Test
    fun `should handle very large file counts`() {
        // Given
        val fileName = "large_archive.zip"
        val extractedCount = 99999
        val totalCount = 100000

        // When
        val notification = helper.createProgressNotification(fileName, 99, extractedCount, totalCount)

        // Then
        assertNotNull("Notification should not be null", notification)
    }

    @Test
    fun `should handle special characters in file name`() {
        // Given
        val fileName = "my archive (2024) [v1].zip"

        // When
        val notification = helper.createProgressNotification(fileName, 50, 50, 100)

        // Then
        assertNotNull("Notification should not be null", notification)
    }

    @Test
    fun `should handle Unicode characters in file name`() {
        // Given
        val fileName = "文件.zip"

        // When
        val notification = helper.createSuccessNotification(fileName, 10)

        // Then
        assertNotNull("Notification should not be null", notification)
    }

    @Test
    fun `should handle empty error message`() {
        // Given
        val fileName = "test.zip"
        val errorMessage = ""

        // When
        val notification = helper.createFailureNotification(fileName, errorMessage)

        // Then
        assertNotNull("Notification should not be null", notification)
    }

    @Test
    fun `should handle very long error message`() {
        // Given
        val fileName = "test.zip"
        val errorMessage = "Error: " + "x".repeat(500)

        // When
        val notification = helper.createFailureNotification(fileName, errorMessage)

        // Then
        assertNotNull("Notification should not be null", notification)
    }

    @Test
    fun `should create notification with 100 percent progress`() {
        // Given
        val fileName = "test.zip"
        val progress = 100
        val extractedCount = 100
        val totalCount = 100

        // When
        val notification = helper.createProgressNotification(fileName, progress, extractedCount, totalCount)

        // Then
        assertNotNull("Notification should not be null", notification)
    }

    @Test
    fun `should create notification with file list`() {
        val recentFiles = listOf("file1.txt", "file2.jpg", "file3.pdf")
        val notification = helper.createProgressNotification(
            fileName = "test.zip", progress = 50, extractedCount = 3, totalCount = 6,
            recentFiles = recentFiles
        )
        assertNotNull(notification)
        val lines = notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        assertNotNull("InboxStyle lines must be present", lines)
        assertEquals("✓ file1.txt", lines!![0].toString())
        assertEquals("✓ file2.jpg", lines[1].toString())
        assertEquals("→ file3.pdf", lines[2].toString())
    }

    @Test
    fun `should create notification with empty file list`() {
        // Given
        val fileName = "test.zip"
        val progress = 0
        val recentFiles = emptyList<String>()

        // When
        val notification = helper.createProgressNotification(
            fileName = fileName,
            progress = progress,
            recentFiles = recentFiles
        )

        // Then
        assertNotNull("Notification should not be null", notification)
    }

    @Test
    fun `should create notification with partial file list`() {
        // Given
        val fileName = "test.zip"
        val progress = 25
        val extractedCount = 2
        val totalCount = 8
        val recentFiles = listOf("file1.txt", "file2.jpg")

        // When
        val notification = helper.createProgressNotification(
            fileName = fileName,
            progress = progress,
            extractedCount = extractedCount,
            totalCount = totalCount,
            recentFiles = recentFiles
        )

        // Then
        assertNotNull("Notification should not be null", notification)
    }

    @Test
    fun `should handle very long file paths in list`() {
        // Given
        val fileName = "test.zip"
        val progress = 33
        val extractedCount = 3
        val totalCount = 9
        val recentFiles = listOf(
            "very/long/path/to/deeply/nested/folder/structure/file1.txt",
            "another/extremely/long/path/with/many/subdirectories/file2.jpg",
            "yet/another/very/long/path/example/file3.pdf"
        )

        // When
        val notification = helper.createProgressNotification(
            fileName = fileName,
            progress = progress,
            extractedCount = extractedCount,
            totalCount = totalCount,
            recentFiles = recentFiles
        )

        // Then
        assertNotNull("Notification should not be null", notification)
    }
}
