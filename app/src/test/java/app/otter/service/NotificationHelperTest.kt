package app.otter.service

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
    fun `should create progress notification with indeterminate progress`() {
        // Given
        val fileName = "test.zip"

        // When
        val notification = helper.createProgressNotification(fileName, 0)

        // Then
        assertNotNull("Notification should not be null", notification)
    }

    @Test
    fun `should create progress notification with percentage`() {
        // Given
        val fileName = "test.zip"
        val progress = 50
        val extractedCount = 50
        val totalCount = 100

        // When
        val notification = helper.createProgressNotification(fileName, progress, extractedCount, totalCount)

        // Then
        assertNotNull("Notification should not be null", notification)
    }

    @Test
    fun `should create progress notification with file count only`() {
        // Given
        val fileName = "test.zip"
        val progress = 0
        val extractedCount = 10

        // When
        val notification = helper.createProgressNotification(fileName, progress, extractedCount, 0)

        // Then
        assertNotNull("Notification should not be null", notification)
    }

    @Test
    fun `should create success notification`() {
        // Given
        val fileName = "test.zip"
        val extractedFilesCount = 42
        val outputPath = "/storage/emulated/0/Download/test"

        // When
        val notification = helper.createSuccessNotification(fileName, extractedFilesCount, outputPath)

        // Then
        assertNotNull("Notification should not be null", notification)
    }

    @Test
    fun `should create failure notification`() {
        // Given
        val fileName = "test.zip"
        val errorMessage = "Corrupted archive"

        // When
        val notification = helper.createFailureNotification(fileName, errorMessage)

        // Then
        assertNotNull("Notification should not be null", notification)
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
        // Given
        val fileName = "test.zip"

        // When
        val notification = helper.createProgressNotification(fileName, 0, 0, 0)

        // Then
        assertNotNull("Notification should not be null", notification)
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
        val notification = helper.createSuccessNotification(fileName, 10, "/path")

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
}
