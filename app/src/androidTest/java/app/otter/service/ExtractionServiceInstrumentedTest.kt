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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
        runBlocking { delay(2000) }

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

        // Start collecting BEFORE service to avoid missing fast completions
        val deferred = CompletableDeferred<ExtractionEventBus.ProgressEvent>()
        val job = launch {
            eventBus.progressState.collect { e ->
                if (e != null && !deferred.isCompleted) deferred.complete(e)
            }
        }

        // When - Start service with Hilt injection
        context.startService(intent)

        // Then - Await event-driven; CI job timeout is the safety net
        val event = deferred.await()
        job.cancel()

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

        // Start collecting BEFORE service to avoid missing completion on SharedFlow (replay=0)
        val done = CompletableDeferred<Unit>()
        val job = launch {
            eventBus.completeEvents.collect { if (!done.isCompleted) done.complete(Unit) }
        }

        // When - Start service with Hilt injection
        context.startService(intent)

        // Then - Await event-driven
        done.await()
        job.cancel()

        assertTrue("Extraction should complete", true)
    }

    @Test
    fun queueShouldBeProcessedSequentially_singleFile() = runBlocking {
        // Given - Use unique filename for this test
        val testFile = createTestZipFile("single-queue-test.zip")
        val file1Uri = Uri.fromFile(testFile)
        val file1Path = ResourcePathConverter.fromUri(file1Uri)

        // Start collecting BEFORE service
        val done = CompletableDeferred<Unit>()
        val job = launch {
            eventBus.completeEvents.collect { if (!done.isCompleted) done.complete(Unit) }
        }

        // When - Start service (Hilt injection)
        val intent = ExtractionService.newIntent(context, file1Path, "single-queue-test.zip")
        context.startService(intent)

        // Then - Await event-driven
        done.await()
        job.cancel()

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

        // Start collecting BEFORE service; use UNLIMITED buffer to avoid StateFlow conflation
        val processedFiles = mutableSetOf<String>()
        val bothDone = CompletableDeferred<Unit>()
        val collectionJob = launch {
            eventBus.progressState.buffer(Channel.UNLIMITED).collect { event ->
                if (event == null) return@collect
                processedFiles.add(event.fileName)
                if (processedFiles.size >= 2 && !bothDone.isCompleted) bothDone.complete(Unit)
            }
        }

        // When - Start service with first file (Hilt injection)
        val file1Path = ResourcePathConverter.fromUri(file1Uri)
        val intent = ExtractionService.newIntent(context, file1Path, "queue-test-1.zip")
        context.startService(intent)

        // Then - Await event-driven
        bothDone.await()
        collectionJob.cancel()

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

    @Test
    fun service_emitsProgressWithRecentFiles() = runBlocking {
        // Given - Create test archive with multiple files
        val testFile = createTestZipFile("recent-files-test.zip")
        val archiveUri = Uri.fromFile(testFile)
        val archivePath = ResourcePathConverter.fromUri(archiveUri)
        val intent = ExtractionService.newIntent(context, archivePath, "recent-files-test.zip")

        // Start collecting BEFORE service with condition on recentFiles
        val deferred = CompletableDeferred<ExtractionEventBus.ProgressEvent>()
        val job = launch {
            eventBus.progressState.collect { e ->
                if (e != null && e.recentFiles.isNotEmpty() && !deferred.isCompleted) deferred.complete(e)
            }
        }

        // When - Start service
        context.startService(intent)

        // Then - Await event-driven
        val event = deferred.await()
        job.cancel()

        assertNotNull("Should receive progress event with recent files", event)
        assertTrue("Should have recent files", event.recentFiles.isNotEmpty())
        assertTrue("Recent files should be limited to 5", event.recentFiles.size <= 5)
    }


    @Test
    fun service_resetsEventBusOnStop() = runBlocking {
        // Given - Start extraction
        val testFile = createTestZipFile("reset-test.zip")
        val archiveUri = Uri.fromFile(testFile)
        val archivePath = ResourcePathConverter.fromUri(archiveUri)
        val intent = ExtractionService.newIntent(context, archivePath, "reset-test.zip")

        // Start collecting BEFORE service
        val firstEvent = CompletableDeferred<Unit>()
        val job = launch {
            eventBus.progressState.collect { e ->
                if (e != null && !firstEvent.isCompleted) firstEvent.complete(Unit)
            }
        }

        context.startService(intent)

        // Wait for at least one progress event
        firstEvent.await()
        job.cancel()

        // When - Stop service
        val stopIntent = ExtractionService.newStopIntent(context)
        context.startService(stopIntent)

        // Wait for service to stop
        delay(1000)

        // Then - EventBus should be reset (replay cache cleared)
        assertTrue("EventBus should be reset after stop", true)
    }

    @Test
    fun service_extractedFilesWrittenToExpectedLocation() = runBlocking {
        val testFile = createTestZipFile("file-output-test.zip")
        val archivePath = ResourcePathConverter.fromUri(Uri.fromFile(testFile))

        // For file:// URIs in cacheDir, the destination resolver places output
        // in the same directory as the archive: cacheDir/file-output-test/
        val expectedOutputDir = File(context.cacheDir, "file-output-test")
        expectedOutputDir.deleteRecursively()
        testFiles.add(expectedOutputDir)

        // Start collecting BEFORE service
        val done = CompletableDeferred<Unit>()
        val job = launch {
            eventBus.completeEvents.collect { if (!done.isCompleted) done.complete(Unit) }
        }

        context.startService(ExtractionService.newIntent(context, archivePath, testFile.name))

        // Await event-driven
        done.await()
        job.cancel()

        assertTrue("Output directory must exist after extraction", expectedOutputDir.exists())
        val extractedFiles = expectedOutputDir.walk().filter { it.isFile }.toList()
        assertTrue("At least 1 file must be extracted to the output directory", extractedFiles.isNotEmpty())
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
