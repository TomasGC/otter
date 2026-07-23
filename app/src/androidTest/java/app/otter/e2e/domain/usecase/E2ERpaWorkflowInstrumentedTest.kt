package app.otter.domain.usecase.e2e

import app.otter.data.extractor.ArchiveExtractor
import app.otter.data.extractor.RpaExtractor
import app.otter.domain.model.ArchiveType
import app.otter.domain.usecase.helpers.ArchiveNavigationTestHelper
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject

@HiltAndroidTest
class E2ERpaWorkflowInstrumentedTest : E2EArchiveWorkflowInstrumentedTest() {

    @Inject
    lateinit var rpaExtractor: RpaExtractor

    override val archiveType: ArchiveType = ArchiveType.RPA
    override val testArchivePath: String = ArchiveNavigationTestHelper.getArchivePath(
        ArchiveNavigationTestHelper.TEST_ARCHIVE_RPA
    )
    override val extractor: ArchiveExtractor get() = rpaExtractor
    override val archiveExtension: String = ".rpa"
}
