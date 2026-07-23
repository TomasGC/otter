package app.otter.domain.usecase.e2e

import app.otter.data.extractor.ArchiveExtractor
import app.otter.data.extractor.TarExtractor
import app.otter.domain.model.ArchiveType
import app.otter.domain.usecase.helpers.ArchiveNavigationTestHelper
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject

@HiltAndroidTest
class E2ETarWorkflowInstrumentedTest : E2EArchiveWorkflowInstrumentedTest() {

    @Inject
    lateinit var tarExtractor: TarExtractor

    override val archiveType: ArchiveType = ArchiveType.TAR
    override val testArchivePath: String = ArchiveNavigationTestHelper.getArchivePath(
        ArchiveNavigationTestHelper.TEST_ARCHIVE_TAR
    )
    override val extractor: ArchiveExtractor get() = tarExtractor
    override val archiveExtension: String = ".tar"
}
