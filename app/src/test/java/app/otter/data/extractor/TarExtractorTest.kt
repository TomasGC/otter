package app.otter.data.extractor

import app.otter.domain.model.ArchiveType
import app.otter.util.PathValidator
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TarExtractorTest {

    private lateinit var pathValidator: PathValidator
    private lateinit var tarExtractor: TarExtractor

    @Before
    fun setup() {
        pathValidator = mockk(relaxed = true)
        tarExtractor = TarExtractor(pathValidator)
    }

    @Test
    fun `supports TAR type`() {
        assertTrue(tarExtractor.supports(ArchiveType.TAR))
    }

    @Test
    fun `supports TAR_GZ type`() {
        assertTrue(tarExtractor.supports(ArchiveType.TAR_GZ))
    }

    @Test
    fun `does not support ZIP type`() {
        assertFalse(tarExtractor.supports(ArchiveType.ZIP))
    }

    @Test
    fun `does not support RAR type`() {
        assertFalse(tarExtractor.supports(ArchiveType.RAR))
    }

    @Test
    fun `does not support SEVEN_ZIP type`() {
        assertFalse(tarExtractor.supports(ArchiveType.SEVEN_ZIP))
    }
}
