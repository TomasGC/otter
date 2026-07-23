package app.otter.domain.usecase.e2e

import app.otter.data.extractor.ArchiveExtractor
import app.otter.data.extractor.RarExtractor
import app.otter.domain.model.ArchiveType
import app.otter.domain.usecase.helpers.ArchiveNavigationTestHelper
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject

/**
 * E2E Workflow Tests for RAR Archives.
 *
 * Requires native 7-Zip-JBinding .so — use real device.
 */
@HiltAndroidTest
class E2ERarWorkflowInstrumentedTest : E2EArchiveWorkflowInstrumentedTest() {

    @Inject
    lateinit var rarExtractor: RarExtractor

    override val archiveType: ArchiveType = ArchiveType.RAR
    override val testArchivePath: String = ArchiveNavigationTestHelper.getArchivePath(
        ArchiveNavigationTestHelper.TEST_ARCHIVE_RAR
    )
    override val extractor: ArchiveExtractor get() = rarExtractor
    override val archiveExtension: String = ".rar"

    // WinRAR CLI refuses to create an archive from an empty source directory
    override val supportsEmptyArchive: Boolean = false

    // 7-Zip-JBinding's RAR decoder does not perform CRC/data validation in EXTRACT mode
    // (only in TEST mode) — a byte-corrupted RAR archive still reports every entry OK, unlike
    // ZIP/7z/TAR/RPA/GZIP which all correctly detect the same corruption. Verified with
    // truncation + scattered 64-byte block corruption; tracked as a known native-library
    // limitation rather than a bug in this codebase's extraction logic.
    override val detectsCorruptedArchive: Boolean = false
}
