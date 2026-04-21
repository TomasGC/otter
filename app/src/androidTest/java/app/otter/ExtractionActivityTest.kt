package app.otter

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ExtractionActivityTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun activityFinishesWhenNoUriProvided() {
        // Given - Intent without data URI
        val intent = Intent(ApplicationProvider.getApplicationContext(), ExtractionActivity::class.java)

        // When - Launch activity
        val scenario = ActivityScenario.launch<ExtractionActivity>(intent)

        // Then - Activity should finish immediately (destroyed state)
        TimeUnit.MILLISECONDS.sleep(100) // Give activity time to finish
        assertTrue(
            "Activity should be destroyed",
            scenario.state == Lifecycle.State.DESTROYED,
        )
        scenario.close()
    }

    @Test
    fun activityHandlesValidUri() {
        // Given - Intent with valid archive URI
        val testUri = Uri.parse("content://com.android.providers.downloads.documents/document/test.zip")
        val intent = Intent(ApplicationProvider.getApplicationContext(), ExtractionActivity::class.java).apply {
            data = testUri
        }

        // When - Launch activity
        val scenario = ActivityScenario.launch<ExtractionActivity>(intent)

        // Then - Activity should handle the URI without crash
        // Activity may finish quickly after starting service
        TimeUnit.MILLISECONDS.sleep(100)
        // No assertion needed - success = no crash
        scenario.close()
    }

    @Test
    fun activityStartsWithContentUri() {
        // Given - Content URI intent
        val contentUri = Uri.parse("content://downloads/all_downloads/1")
        val intent = Intent(ApplicationProvider.getApplicationContext(), ExtractionActivity::class.java).apply {
            data = contentUri
            action = Intent.ACTION_VIEW
        }

        // When - Launch activity with content URI
        val scenario = ActivityScenario.launch<ExtractionActivity>(intent)

        // Then - Activity should start without crash
        TimeUnit.MILLISECONDS.sleep(100)
        // No assertion needed - success = no crash
        scenario.close()
    }

    @Test
    fun activityHandlesViewAction() {
        // Given - ACTION_VIEW intent (standard "Open with" flow)
        val testUri = Uri.parse("content://com.android.providers.downloads.documents/document/archive.zip")
        val intent = Intent(ApplicationProvider.getApplicationContext(), ExtractionActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = testUri
            type = "application/zip"
        }

        // When - Launch activity
        val scenario = ActivityScenario.launch<ExtractionActivity>(intent)

        // Then - Should handle VIEW action properly without crash
        TimeUnit.MILLISECONDS.sleep(100)
        // No assertion needed - success = no crash
        scenario.close()
    }

    @Test
    fun activityHandlesDifferentMimeTypes() {
        // Test ZIP mime type
        val zipUri = Uri.parse("content://test/archive.zip")
        val zipIntent = Intent(ApplicationProvider.getApplicationContext(), ExtractionActivity::class.java).apply {
            data = zipUri
            type = "application/zip"
        }

        val zipScenario = ActivityScenario.launch<ExtractionActivity>(zipIntent)
        zipScenario.close()

        // Test RAR mime type
        val rarUri = Uri.parse("content://test/archive.rar")
        val rarIntent = Intent(ApplicationProvider.getApplicationContext(), ExtractionActivity::class.java).apply {
            data = rarUri
            type = "application/x-rar-compressed"
        }

        val rarScenario = ActivityScenario.launch<ExtractionActivity>(rarIntent)
        rarScenario.close()

        // Test generic octet-stream
        val genericUri = Uri.parse("content://test/archive")
        val genericIntent = Intent(ApplicationProvider.getApplicationContext(), ExtractionActivity::class.java).apply {
            data = genericUri
            type = "application/octet-stream"
        }

        val genericScenario = ActivityScenario.launch<ExtractionActivity>(genericIntent)
        genericScenario.close()
    }

    @Test
    fun activityHandlesFileUri() {
        // Given - file:// URI (legacy, but should handle gracefully)
        val fileUri = Uri.parse("file:///storage/emulated/0/Download/test.zip")
        val intent = Intent(ApplicationProvider.getApplicationContext(), ExtractionActivity::class.java).apply {
            data = fileUri
        }

        // When - Launch activity
        val scenario = ActivityScenario.launch<ExtractionActivity>(intent)

        // Then - Should handle file URI without crash
        TimeUnit.MILLISECONDS.sleep(100)
        // No assertion needed - success = no crash
        scenario.close()
    }

    @Test
    fun activityHandlesMultipleLaunches() {
        // Test activity can be launched multiple times
        for (i in 1..3) {
            val testUri = Uri.parse("content://test/document$i")
            val intent = Intent(ApplicationProvider.getApplicationContext(), ExtractionActivity::class.java).apply {
                data = testUri
            }

            val scenario = ActivityScenario.launch<ExtractionActivity>(intent)
            scenario.close()

            // Small delay between launches
            TimeUnit.MILLISECONDS.sleep(100)
        }
    }

    @Test
    fun activityHandlesIntentWithoutAction() {
        // Given - Intent with URI but no action
        val testUri = Uri.parse("content://test/archive.zip")
        val intent = Intent(ApplicationProvider.getApplicationContext(), ExtractionActivity::class.java).apply {
            data = testUri
            // No action set
        }

        // When - Launch activity
        val scenario = ActivityScenario.launch<ExtractionActivity>(intent)

        // Then - Should work without crash (action is optional)
        TimeUnit.MILLISECONDS.sleep(100)
        // No assertion needed - success = no crash
        scenario.close()
    }

}
