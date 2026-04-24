package app.otter.data.extractor

import app.otter.domain.model.ArchiveType
import app.otter.util.PathValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for SevenZipExtractor.
 *
 * Note: Full extraction testing requires native libraries (7-Zip-JBinding .so files)
 * which are not available in unit tests (JVM only). Use instrumented tests
 * (SevenZipExtractorInstrumentedTest) for real extraction validation on device/emulator.
 *
 * These unit tests validate basic type support only.
 */
class SevenZipExtractorTest {

    private val realPathValidator = PathValidator()
    private val extractor = SevenZipExtractor(realPathValidator)

    @Test
    fun `should support SEVEN_ZIP type`() {
        assertTrue(extractor.supports(ArchiveType.SEVEN_ZIP))
    }

    @Test
    fun `should not support ZIP type`() {
        assertFalse(extractor.supports(ArchiveType.ZIP))
    }

    @Test
    fun `should not support RAR type`() {
        assertFalse(extractor.supports(ArchiveType.RAR))
    }
}
