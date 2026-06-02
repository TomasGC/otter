package app.otter.data.extractor

import app.otter.util.PathValidator
import app.otter.test.fakes.SimpleTempFileManager
import io.mockk.every
import io.mockk.mockk

/**
 * Mock-based integration tests for ZipExtractor.
 * Uses SimpleTempFileManager (real temp file creation), mocked SevenZipHelper, and mocked IZipFileReaderFactory.
 * All test scenarios are inherited from ZipExtractorIntegrationTestBase.
 *
 * This variant validates extraction logic with mocked ZIP reader (uses real file but mocked reader factory).
 * For full real I/O validation, see ZipExtractorRealIntegrationTest.
 */
class ZipExtractorIntegrationTest : ZipExtractorIntegrationTestBase() {

    private lateinit var tempFileManager: SimpleTempFileManager

    override fun createExtractor(): ZipExtractor {
        tempFileManager = SimpleTempFileManager()

        // Mock ZIP reader factory to use real ZIP file reader (no actual mocking benefit here)
        val mockZipReaderFactory = mockk<IZipFileReaderFactory>(relaxed = false)
        every { mockZipReaderFactory.create(any()) } answers {
            // Use real reader for actual ZIP file
            RealZipFileReader(firstArg())
        }

        return ZipExtractor(
            pathValidator = PathValidator(),                                    // Real
            tempFileManager = tempFileManager,                                  // Real
            sevenZipHelper = mockk(relaxed = true),                             // Mock (external lib)
            zipFileReaderFactory = mockZipReaderFactory                         // Mock (but delegates to real)
        )
    }

    override fun cleanupExtractor() {
        tempFileManager.cleanup()
    }
}
