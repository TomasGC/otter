package app.otter.data.extractor

import org.junit.Test

class ExtractionLoggerTest {

    private val logger = ExtractionLogger("TestTag")

    @Test
    fun `logProgress logs at interval boundary (divisible by 100)`() {
        logger.logProgress(100, 500, "test.txt")
    }

    @Test
    fun `logProgress skips logging when not at interval and not equal to total`() {
        logger.logProgress(50, 500, "test.txt")
    }

    @Test
    fun `logProgress logs when extractedCount equals totalCount`() {
        logger.logProgress(10, 10, "last.txt")
    }

    @Test
    fun `logProgress handles first file (count 1 not at interval)`() {
        logger.logProgress(1, 200, "first.txt")
    }

    @Test
    fun `logComplete logs extraction completion`() {
        logger.logComplete(42)
    }

    @Test
    fun `logComplete handles zero extracted files`() {
        logger.logComplete(0)
    }
}
