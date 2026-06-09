package app.otter.domain.usecase.helpers

import android.net.Uri
import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.ResourcePath
import app.otter.domain.usecase.BrowseItemsUseCase
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import javax.inject.Inject

/**
 * Base class for archive navigation tests.
 * Provides common helper methods for browsing archives using injected BrowseItemsUseCase.
 */
@HiltAndroidTest
abstract class BaseArchiveNavigationTest : BaseInstrumentedTest() {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var browseItemsUseCase: BrowseItemsUseCase

    @Before
    fun injectDependencies() {
        hiltRule.inject()
    }

    protected suspend fun browseArchive(archivePath: String, path: ResourcePath? = null): List<BrowsableItem> {
        return ArchiveNavigationTestHelper.browseArchive(browseItemsUseCase, archivePath, path)
    }
}
