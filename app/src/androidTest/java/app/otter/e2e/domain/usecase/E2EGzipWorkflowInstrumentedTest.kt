package app.otter.domain.usecase.e2e

import app.otter.data.extractor.ArchiveExtractor
import app.otter.data.extractor.GzipExtractor
import app.otter.domain.model.ArchiveType
import app.otter.domain.usecase.helpers.ArchiveNavigationTestHelper
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject

@HiltAndroidTest
class E2EGzipWorkflowInstrumentedTest : E2EArchiveWorkflowInstrumentedTest() {

    @Inject
    lateinit var gzipExtractor: GzipExtractor

    override val archiveType: ArchiveType = ArchiveType.GZIP
    override val testArchivePath: String = ArchiveNavigationTestHelper.getArchivePath(
        ArchiveNavigationTestHelper.TEST_ARCHIVE_GZ
    )
    override val extractor: ArchiveExtractor get() = gzipExtractor
    override val archiveExtension: String = ".gz"
    override val supportsDirectoryNavigation: Boolean = false

    // GZIP compresses a single file — there's no way to represent an "empty" archive
    override val supportsEmptyArchive: Boolean = false
}
