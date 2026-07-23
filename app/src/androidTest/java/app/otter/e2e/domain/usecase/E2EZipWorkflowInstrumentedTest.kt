package app.otter.domain.usecase.e2e

import app.otter.data.extractor.ArchiveExtractor
import app.otter.data.extractor.ZipExtractor
import app.otter.domain.model.ArchiveType
import app.otter.domain.usecase.helpers.ArchiveNavigationTestHelper
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject

/**
 * E2E Workflow Tests for ZIP Archives
 */
@HiltAndroidTest
class E2EZipWorkflowInstrumentedTest : E2EArchiveWorkflowInstrumentedTest() {

    @Inject
    lateinit var zipExtractor: ZipExtractor

    override val archiveType: ArchiveType = ArchiveType.ZIP
    override val testArchivePath: String = ArchiveNavigationTestHelper.getArchivePath(ArchiveNavigationTestHelper.TEST_ARCHIVE_ZIP)
    override val extractor: ArchiveExtractor get() = zipExtractor
    override val archiveExtension: String = ".zip"

    // ZIP is the only format with a real malicious (path-traversal) fixture
    override val supportsMaliciousArchiveFixture: Boolean = true
}
