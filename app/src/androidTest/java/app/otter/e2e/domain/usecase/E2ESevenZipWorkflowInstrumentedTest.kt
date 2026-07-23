package app.otter.domain.usecase.e2e

import app.otter.data.extractor.ArchiveExtractor
import app.otter.data.extractor.SevenZipExtractor
import app.otter.domain.model.ArchiveType
import app.otter.domain.usecase.helpers.ArchiveNavigationTestHelper
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject

/**
 * E2E Workflow Tests for 7z Archives.
 * Same native-lib note as E2ERarWorkflowInstrumentedTest.
 * Requires real device for native 7-Zip-JBinding .so.
 */
@HiltAndroidTest
class E2ESevenZipWorkflowInstrumentedTest : E2EArchiveWorkflowInstrumentedTest() {

    @Inject
    lateinit var sevenZipExtractor: SevenZipExtractor

    override val archiveType: ArchiveType = ArchiveType.SEVEN_ZIP
    override val testArchivePath: String = ArchiveNavigationTestHelper.getArchivePath(
        ArchiveNavigationTestHelper.TEST_ARCHIVE_7Z
    )
    override val extractor: ArchiveExtractor get() = sevenZipExtractor
    override val archiveExtension: String = ".7z"
}
