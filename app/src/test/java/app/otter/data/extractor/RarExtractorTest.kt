package app.otter.data.extractor

import app.otter.domain.model.ArchiveType
import app.otter.util.PathValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for RarExtractor.
 *
 * Note: Full extraction testing requires native libraries (7-Zip-JBinding .so files)
 * which are not available in unit tests (JVM only). Use instrumented tests
 * (RarExtractorInstrumentedTest) for real extraction validation on device/emulator.
 *
 * These unit tests validate basic type support only.
 */
class RarExtractorTest {

    private val realPathValidator = PathValidator()
    private val archiveLibraryManager = ArchiveLibraryManager()
    private val extractor = RarExtractor(realPathValidator, archiveLibraryManager)

    @Test
    fun `should support RAR type`() {
        assertTrue(extractor.supports(ArchiveType.RAR))
    }

    @Test
    fun `should not support ZIP type`() {
        assertFalse(extractor.supports(ArchiveType.ZIP))
    }
}
