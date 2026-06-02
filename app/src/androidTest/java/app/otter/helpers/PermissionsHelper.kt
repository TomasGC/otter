package app.otter

import android.Manifest
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Helper for granting runtime permissions during instrumented tests.
 * On Android 11+, requires MANAGE_EXTERNAL_STORAGE permission granted via Settings.
 */
object PermissionsHelper {

    /**
     * Grant storage permissions required to read test archives.
     * MUST be called in @Before setup for ALL instrumented tests that access files.
     */
    fun grantStoragePermissions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ requires MANAGE_EXTERNAL_STORAGE via ADB or Settings
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "appops set $packageName MANAGE_EXTERNAL_STORAGE allow"
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-10 requires READ_EXTERNAL_STORAGE runtime permission
            instrumentation.uiAutomation.grantRuntimePermission(
                packageName,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            instrumentation.uiAutomation.grantRuntimePermission(
                packageName,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }

        // Give time for permissions to propagate
        Thread.sleep(500)
    }

}
