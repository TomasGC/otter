package app.otter.domain.usecase.helpers

import app.otter.PermissionsHelper
import org.junit.Before

/**
 * Base class for ALL instrumented tests.
 * Automatically grants storage permissions required to access test archives.
 */
abstract class BaseInstrumentedTest {

    @Before
    fun grantPermissions() {
        PermissionsHelper.grantStoragePermissions()
    }
}
