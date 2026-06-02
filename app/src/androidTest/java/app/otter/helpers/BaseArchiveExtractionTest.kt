package app.otter.domain.usecase.helpers

import app.otter.domain.model.BrowsableItem
import app.otter.domain.model.ResourcePath
import app.otter.domain.usecase.BrowseItemsUseCase
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import java.io.File
import javax.inject.Inject

/**
 * Base class for archive extraction tests.
 * Provides common setup/teardown and helper methods for extraction tests.
 */
@HiltAndroidTest
abstract class BaseArchiveExtractionTest : BaseInstrumentedTest() {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var browseItemsUseCase: BrowseItemsUseCase

    protected lateinit var outputDir: File

    @Before
    fun injectDependencies() {
        hiltRule.inject()
    }

    @After
    fun cleanup() {
        if (::outputDir.isInitialized) {
            outputDir.deleteRecursively()
        }
    }

    protected suspend fun extractArchive(
        archivePath: String,
        selectedItems: List<BrowsableItem> = emptyList()
    ): File {
        outputDir = ArchiveExtractionTestHelper.createOutputDir()
        val archiveType = ArchiveExtractionTestHelper.getArchiveType(File(archivePath).name)
        return ArchiveExtractionTestHelper.extractArchive(archivePath, archiveType, outputDir, selectedItems)
    }

    protected suspend fun browseArchive(archivePath: String, path: ResourcePath? = null): List<BrowsableItem> {
        return ArchiveNavigationTestHelper.browseArchive(browseItemsUseCase, archivePath, path)
    }
}
