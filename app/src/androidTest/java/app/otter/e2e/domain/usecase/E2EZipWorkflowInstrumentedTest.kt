package app.otter.domain.usecase.e2e

import app.otter.data.extractor.ArchiveExtractor
import app.otter.data.extractor.TestArchiveHelper
import app.otter.data.extractor.ZipExtractor
import app.otter.domain.model.ArchiveType
import app.otter.domain.usecase.helpers.ArchiveNavigationTestHelper
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
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

    override fun createCorruptedArchive(file: File) {
        TestArchiveHelper.createCorruptedZip(file)
    }

    override fun createMaliciousArchive(file: File) {
        TestArchiveHelper.createMaliciousZipWithPathTraversal(file)
    }

    override fun createEmptyArchive(file: File) {
        TestArchiveHelper.createEmptyZip(file)
    }

    override fun createLargeArchive(file: File, fileCount: Int) {
        TestArchiveHelper.createLargeZip(file, fileCount)
    }

    override fun createDeepNestedArchive(file: File, depth: Int) {
        TestArchiveHelper.createDeepNestedZip(file, depth)
    }

    override fun createArchiveWithLongFilename(file: File, maxLength: Int) {
        TestArchiveHelper.createZipWithLongFilename(file, maxLength)
    }
}
