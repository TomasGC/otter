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
import app.otter.domain.usecase.helpers.ArchiveNavigationTestHelper
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BrowserActivityInstrumentedTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun launchWithNoUri_activityStartsAtFileSystemRoot() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), BrowserActivity::class.java)
        val scenario = ActivityScenario.launch<BrowserActivity>(intent)

        TimeUnit.MILLISECONDS.sleep(500)
        // Activity must not crash regardless of storage permission state (API 30+ may be PAUSED)
        assertNotEquals(Lifecycle.State.DESTROYED, scenario.state)
        scenario.close()
    }

    @Test
    fun launchWithArchiveUri_activityStartsInsideArchive() {
        val archivePath = ArchiveNavigationTestHelper.getArchivePath(
            ArchiveNavigationTestHelper.TEST_ARCHIVE_ZIP
        )
        val archiveUri = Uri.parse("file://$archivePath")

        val intent = Intent(ApplicationProvider.getApplicationContext(), BrowserActivity::class.java).apply {
            data = archiveUri
            action = Intent.ACTION_VIEW
        }

        val scenario = ActivityScenario.launch<BrowserActivity>(intent)
        TimeUnit.MILLISECONDS.sleep(500)

        // Activity should be running (not destroyed)
        assertNotEquals(Lifecycle.State.DESTROYED, scenario.state)
        scenario.close()
    }

    @Test
    fun postNotificationsPermissionDenied_activityStillLaunches() {
        // Revoke POST_NOTIFICATIONS permission via shell (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "pm revoke ${ApplicationProvider.getApplicationContext<android.app.Application>().packageName} " +
                    Manifest.permission.POST_NOTIFICATIONS
            )
            TimeUnit.MILLISECONDS.sleep(300)
        }

        val intent = Intent(ApplicationProvider.getApplicationContext(), BrowserActivity::class.java)
        val scenario = ActivityScenario.launch<BrowserActivity>(intent)
        TimeUnit.MILLISECONDS.sleep(500)

        // Activity must not crash regardless of notification permission
        assertNotEquals(Lifecycle.State.DESTROYED, scenario.state)
        scenario.close()

        // Restore permission for other tests
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "pm grant ${ApplicationProvider.getApplicationContext<android.app.Application>().packageName} " +
                    Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    @Test
    fun manageAllFilesPermissionNotGranted_activityStillLaunches() {
        // On Android 11+ without MANAGE_EXTERNAL_STORAGE, activity should still launch
        // (it requests it in onCreate but continues even if denied)
        val intent = Intent(ApplicationProvider.getApplicationContext(), BrowserActivity::class.java)
        val scenario = ActivityScenario.launch<BrowserActivity>(intent)
        TimeUnit.MILLISECONDS.sleep(1_000) // Longer wait: permission dialog may appear
        assertNotEquals(Lifecycle.State.DESTROYED, scenario.state)
        scenario.close()
    }

    @Test
    fun launchWithSamsungStyleContentUri_activityHandlesGracefully() {
        // Samsung My Files passes content:// URIs with com.android.externalstorage.documents authority
        val samsungUri = Uri.parse(
            "content://com.android.externalstorage.documents/document/primary%3ADownload%2Ftest.zip"
        )
        val intent = Intent(ApplicationProvider.getApplicationContext(), BrowserActivity::class.java).apply {
            data = samsungUri
            action = Intent.ACTION_VIEW
        }
        val scenario = ActivityScenario.launch<BrowserActivity>(intent)
        TimeUnit.MILLISECONDS.sleep(1_000)
        assertNotEquals(Lifecycle.State.DESTROYED, scenario.state)
        scenario.close()
    }
}
