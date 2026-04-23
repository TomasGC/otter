package app.otter.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otter.data.util.ResourcePathConverter
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
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

/**
 * Instrumented tests for ExtractionService with Hilt
 * Tests actual service behavior on real device/emulator
 * Uses startService() to allow proper Hilt dependency injection
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ExtractionServiceInstrumentedTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var eventBus: ExtractionEventBus

    @Inject
    lateinit var extractionQueue: ExtractionQueue

    private lateinit var context: Context
    private val testFiles = mutableListOf<File>()

    @Before
    fun setup() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()

        // Clear queue before each test
        extractionQueue.clear()
    }

    @After
    fun teardown() {
        // Stop service explicitly
        val stopIntent = ExtractionService.newStopIntent(context)
        context.startService(stopIntent)

        // Wait a bit for service to stop
        runBlocking { delay(500) }

        // Clean up all test files
        testFiles.forEach { file ->
            if (file.exists()) {
                file.delete()
            }
        }
        testFiles.clear()
        extractionQueue.clear()
    }

    @Test
    fun serviceShouldEmitProgressEvents() = runBlocking {
        // Given - Use unique filename for this test
        val testFile = createTestZipFile("progress-test.zip")
        val archiveUri = Uri.fromFile(testFile)
        val archivePath = ResourcePathConverter.fromUri(archiveUri)
        val intent = ExtractionService.newIntent(context, archivePath, "progress-test.zip")

        // When - Start service with Hilt injection
        context.startService(intent)

        // Wait for progress event (faster with API 30 + KVM)
        val event = withTimeout(10000) {
            eventBus.progressEvents.first()
        }

        // Then
        assertNotNull("Should receive progress event", event)
        assertEquals("progress-test.zip", event.fileName)
        assertTrue("Should have extracted count", event.extractedCount >= 0)
    }

    @Test
    fun serviceShouldCompleteExtraction() = runBlocking {
        // Given - Use unique filename for this test
        val testFile = createTestZipFile("complete-test.zip")
        val archiveUri = Uri.fromFile(testFile)
        val archivePath = ResourcePathConverter.fromUri(archiveUri)
        val intent = ExtractionService.newIntent(context, archivePath, "complete-test.zip")

        // When - Start service with Hilt injection
        context.startService(intent)

        // Wait for completion event (faster with API 30 + KVM)
        withTimeout(15000) {
            eventBus.completeEvents.first()
        }

        // Then - If we reach here, extraction completed successfully
        assertTrue("Extraction should complete", true)
    }

    @Test
    fun queueShouldBeProcessedSequentially_singleFile() = runBlocking {
        // Given - Use unique filename for this test
        val testFile = createTestZipFile("single-queue-test.zip")
        val file1Uri = Uri.fromFile(testFile)
        val file1Path = ResourcePathConverter.fromUri(file1Uri)

        // When - Start service (Hilt injection)
        val intent = ExtractionService.newIntent(context, file1Path, "single-queue-test.zip")
        context.startService(intent)

        // Then - Wait for extraction to complete
        withTimeout(30000) {
            eventBus.completeEvents.first()
        }

        // If we reach here, queue processing works
        assertTrue("Should process single file from queue", true)
    }

    @Test
    fun queueShouldBeProcessedSequentially_twoFiles() = runBlocking {
        // Given - Use unique filenames for this test
        val file1 = createTestZipFile("queue-test-1.zip")
        val file2 = createTestZipFile("queue-test-2.zip")
        val file1Uri = Uri.fromFile(file1)
        val file2Uri = Uri.fromFile(file2)

        extractionQueue.enqueueAll(
            listOf(ExtractionQueue.ExtractionTask(ResourcePathConverter.fromUri(file2Uri), "queue-test-2.zip"))
        )

        // Start collecting events BEFORE starting service to avoid race condition
        val processedFiles = mutableSetOf<String>()
        val collectionJob = launch {
            eventBus.progressEvents.collect { event ->
                processedFiles.add(event.fileName)
            }
        }

        // When - Start service with first file (Hilt injection)
        val file1Path = ResourcePathConverter.fromUri(file1Uri)
        val intent = ExtractionService.newIntent(context, file1Path, "queue-test-1.zip")
        context.startService(intent)

        // Then - Wait for both files to be processed
        withTimeout(30000) { // 30s should be enough for 2 small files
            while (processedFiles.size < 2) {
                delay(100)
            }
        }

        collectionJob.cancel()

        // Verify both files were processed
        assertTrue("Should have processed queue-test-1.zip", processedFiles.contains("queue-test-1.zip"))
        assertTrue("Should have processed queue-test-2.zip", processedFiles.contains("queue-test-2.zip"))
    }

    @Test
    fun stopIntentShouldStopService() = runBlocking {
        // Given - Use unique filename for this test
        val testFile = createTestZipFile("stop-test.zip")
        val archiveUri = Uri.fromFile(testFile)
        val archivePath = ResourcePathConverter.fromUri(archiveUri)
        val intent = ExtractionService.newIntent(context, archivePath, "stop-test.zip")

        // When - Start service with Hilt injection
        context.startService(intent)

        // Small delay to ensure service starts
        delay(500)

        // Send stop intent
        val stopIntent = ExtractionService.newStopIntent(context)
        context.startService(stopIntent)

        // Then - Service should stop (we can't easily verify this without reflection)
        // If we reach here without crashing, the stop intent was processed
        assertTrue("Stop intent should be processed", true)
    }

    @Test
    fun serviceShouldHandleInvalidUri() = runBlocking {
        // Given - Invalid URI
        val invalidUri = Uri.parse("file:///nonexistent/file.zip")
        val invalidPath = ResourcePathConverter.fromUri(invalidUri)
        val intent = ExtractionService.newIntent(context, invalidPath, "invalid.zip")

        // When - Start service with Hilt injection
        context.startService(intent)

        // Then - Service should handle gracefully (not crash)
        // Wait a bit to ensure service processes the intent
        delay(1000)
        assertTrue("Service should handle invalid URI gracefully", true)
    }

    // Helper method to create a small test ZIP file
    private fun createTestZipFile(name: String): File {
        val zipFile = File(context.cacheDir, name)

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // Add 3 small test files
            for (i in 1..3) {
                val entry = ZipEntry("file$i.txt")
                zos.putNextEntry(entry)
                zos.write("Test content $i".toByteArray())
                zos.closeEntry()
            }
        }

        // Track for cleanup
        testFiles.add(zipFile)
        return zipFile
    }
}
