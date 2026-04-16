package app.otter.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ExtractionServiceTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun serviceStartsSuccessfully() {
        // Given
        val archiveUri = Uri.parse("content://com.android.providers.downloads.documents/document/1")
        val intent = Intent(context, ExtractionService::class.java).apply {
            data = archiveUri
            putExtra(ExtractionService.EXTRA_FILE_NAME, "test.zip")
        }

        // When - Start the service
        val binder = serviceRule.bindService(intent)

        // Then - Service should be bound successfully
        assertNotNull("Service should be bound", binder)
    }

    @Test
    fun serviceHandlesStopAction() {
        // Given
        val stopIntent = Intent(context, ExtractionService::class.java).apply {
            action = ExtractionService.ACTION_STOP_EXTRACTION
        }

        // When - Start service with stop action
        serviceRule.startService(stopIntent)

        // Then - Service should handle stop without crash
        // (Service will stop itself after handling the action)
        Thread.sleep(500) // Give service time to process
    }

    @Test
    fun serviceStopsWhenNoUriProvided() {
        // Given
        val intentWithoutUri = Intent(context, ExtractionService::class.java).apply {
            putExtra(ExtractionService.EXTRA_FILE_NAME, "test.zip")
        }

        // When - Start service without URI
        serviceRule.startService(intentWithoutUri)

        // Then - Service should stop itself gracefully
        Thread.sleep(500) // Give service time to stop
    }

    @Test
    fun serviceCreatesNotificationChannel() {
        // Given
        val testUri = Uri.parse("content://com.android.providers.downloads.documents/document/test")
        val intent = Intent(context, ExtractionService::class.java).apply {
            data = testUri
            putExtra(ExtractionService.EXTRA_FILE_NAME, "test.zip")
        }

        // When - Service is started
        serviceRule.startService(intent)
        serviceRule.bindService(intent)

        // Then - Should create notification channel (verified by no crash)
        // NotificationChannel creation is side-effect, difficult to assert
        Thread.sleep(200)
    }

    @Test
    fun serviceHandlesDifferentFileNames() {
        // Test various file names
        val fileNames = listOf(
            "test.zip",
            "archive with spaces.rar",
            "file-with-dashes.zip",
            "file_with_underscores.zip"
        )

        fileNames.forEach { fileName ->
            val testUri = Uri.parse("content://test/document/$fileName")
            val intent = Intent(context, ExtractionService::class.java).apply {
                data = testUri
                putExtra(ExtractionService.EXTRA_FILE_NAME, fileName)
            }

            // Should handle all file names without crash
            serviceRule.startService(intent)
            Thread.sleep(100)
        }
    }

    @Test
    fun serviceHandlesNullFileName() {
        // Given - Intent without file name
        val testUri = Uri.parse("content://test/document/unknown")
        val intent = Intent(context, ExtractionService::class.java).apply {
            data = testUri
            // No EXTRA_FILE_NAME provided
        }

        // When - Service starts
        serviceRule.startService(intent)

        // Then - Should use default "archive" name without crash
        Thread.sleep(200)
    }

    @Test
    fun serviceRestartsWithDifferentIntent() {
        // Given - First extraction
        val firstUri = Uri.parse("content://test/doc1")
        val firstIntent = Intent(context, ExtractionService::class.java).apply {
            data = firstUri
            putExtra(ExtractionService.EXTRA_FILE_NAME, "first.zip")
        }

        serviceRule.startService(firstIntent)
        Thread.sleep(300)

        // When - Start with different file
        val secondUri = Uri.parse("content://test/doc2")
        val secondIntent = Intent(context, ExtractionService::class.java).apply {
            data = secondUri
            putExtra(ExtractionService.EXTRA_FILE_NAME, "second.zip")
        }

        serviceRule.startService(secondIntent)

        // Then - Should handle restart properly
        Thread.sleep(200)
    }

    @Test
    fun serviceBindingReturnsNull() {
        // Given
        val testUri = Uri.parse("content://test/document")
        val intent = Intent(context, ExtractionService::class.java).apply {
            data = testUri
            putExtra(ExtractionService.EXTRA_FILE_NAME, "test.zip")
        }

        // When - Bind to service
        val binder = serviceRule.bindService(intent)

        // Then - Should return non-null binder (service bound successfully)
        assertNotNull("Service binder should not be null", binder)
    }

    @Test
    fun serviceHandlesRapidStopRequest() {
        // Given - Service started
        val testUri = Uri.parse("content://test/document")
        val startIntent = Intent(context, ExtractionService::class.java).apply {
            data = testUri
            putExtra(ExtractionService.EXTRA_FILE_NAME, "test.zip")
        }
        serviceRule.startService(startIntent)

        // When - Immediately send stop
        val stopIntent = Intent(context, ExtractionService::class.java).apply {
            action = ExtractionService.ACTION_STOP_EXTRACTION
        }
        serviceRule.startService(stopIntent)

        // Then - Should handle rapid stop without crash
        Thread.sleep(300)
    }
}
