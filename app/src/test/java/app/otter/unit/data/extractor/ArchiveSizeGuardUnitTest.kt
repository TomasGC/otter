package app.otter.data.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveSizeGuardUnitTest {

    @Test
    fun `track under both limits does not throw`() {
        val guard = ArchiveSizeGuard(maxFileSizeBytes = 1000L, maxTotalSizeBytes = 10000L)
        guard.startEntry()
        guard.track(500)
    }

    @Test
    fun `track exceeding max file size throws SecurityException`() {
        val guard = ArchiveSizeGuard(maxFileSizeBytes = 1000L, maxTotalSizeBytes = 10000L)
        guard.startEntry()

        val ex = assertThrows(SecurityException::class.java) {
            guard.track(1001)
        }
        assertTrue("Message should mention file size limit", ex.message?.contains("file size") == true)
    }

    @Test
    fun `track accumulates across multiple calls for the same entry`() {
        val guard = ArchiveSizeGuard(maxFileSizeBytes = 1000L, maxTotalSizeBytes = 10000L)
        guard.startEntry()
        guard.track(400)
        guard.track(400)

        val ex = assertThrows(SecurityException::class.java) {
            guard.track(400) // 1200 > 1000
        }
        assertTrue("Message should mention file size limit", ex.message?.contains("file size") == true)
    }

    @Test
    fun `startEntry resets per-file counter for the next entry`() {
        val guard = ArchiveSizeGuard(maxFileSizeBytes = 1000L, maxTotalSizeBytes = 10000L)
        guard.startEntry()
        guard.track(900)

        guard.startEntry() // new entry
        guard.track(900) // should not throw: per-file counter reset, and 1800 < total limit 10000
    }

    @Test
    fun `track exceeding max total size throws even when each entry is under the per-file limit`() {
        val guard = ArchiveSizeGuard(maxFileSizeBytes = 1000L, maxTotalSizeBytes = 2500L)

        repeat(2) {
            guard.startEntry()
            guard.track(900) // 1800 total, under per-file and total limits
        }

        guard.startEntry()
        val ex = assertThrows(SecurityException::class.java) {
            guard.track(900) // 2700 total > 2500
        }
        assertTrue("Message should mention total size limit", ex.message?.contains("total size") == true)
    }

    @Test
    fun `default limits match documented 100MB per file and 500MB total`() {
        assertEquals(100L * 1024 * 1024, ArchiveSizeGuard.MAX_FILE_SIZE_BYTES)
        assertEquals(500L * 1024 * 1024, ArchiveSizeGuard.MAX_TOTAL_SIZE_BYTES)
    }
}
