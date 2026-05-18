package app.otter.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Tests for FileFormatters.
 *
 * Covers:
 * - formatFileSize for all units (B, KB, MB, GB)
 * - Boundary values (0, 1023, 1024, etc.)
 * - formatDate with various timestamps
 */
class FileFormattersTest {

    // ========================================================================
    // formatFileSize Tests
    // ========================================================================

    @Test
    fun `formatFileSize handles 0 bytes`() {
        val result = FileFormatters.formatFileSize(0)
        assertEquals("0 B", result)
    }

    @Test
    fun `formatFileSize handles bytes less than 1 KB`() {
        assertEquals("1 B", FileFormatters.formatFileSize(1))
        assertEquals("512 B", FileFormatters.formatFileSize(512))
        assertEquals("1023 B", FileFormatters.formatFileSize(1023))
    }

    @Test
    fun `formatFileSize handles exactly 1 KB`() {
        val result = FileFormatters.formatFileSize(1024)
        assertEquals("1.0 KB", result)
    }

    @Test
    fun `formatFileSize handles KB range`() {
        // 1.5 KB
        val result1 = FileFormatters.formatFileSize(1536)
        assertEquals("1.5 KB", result1)

        // 500 KB
        val result2 = FileFormatters.formatFileSize(512_000)
        assertEquals("500.0 KB", result2)

        // 1023.9 KB (just before 1 MB)
        val result3 = FileFormatters.formatFileSize(1024 * 1024 - 100)
        assertTrue(result3.startsWith("1023"))
        assertTrue(result3.endsWith("KB"))
    }

    @Test
    fun `formatFileSize handles exactly 1 MB`() {
        val result = FileFormatters.formatFileSize(1024 * 1024)
        assertEquals("1.0 MB", result)
    }

    @Test
    fun `formatFileSize handles MB range`() {
        // 5.5 MB
        val result1 = FileFormatters.formatFileSize((5.5 * 1024 * 1024).toLong())
        assertEquals("5.5 MB", result1)

        // 500 MB
        val result2 = FileFormatters.formatFileSize(500 * 1024 * 1024)
        assertEquals("500.0 MB", result2)

        // 1023.9 MB (just before 1 GB)
        val result3 = FileFormatters.formatFileSize(1024 * 1024 * 1024 - 100_000)
        assertTrue(result3.startsWith("1023"))
        assertTrue(result3.endsWith("MB"))
    }

    @Test
    fun `formatFileSize handles exactly 1 GB`() {
        val result = FileFormatters.formatFileSize(1024L * 1024 * 1024)
        assertEquals("1.0 GB", result)
    }

    @Test
    fun `formatFileSize handles GB range`() {
        // 2.5 GB
        val result1 = FileFormatters.formatFileSize((2.5 * 1024 * 1024 * 1024).toLong())
        assertEquals("2.5 GB", result1)

        // 100 GB
        val result2 = FileFormatters.formatFileSize(100L * 1024 * 1024 * 1024)
        assertEquals("100.0 GB", result2)

        // 1 TB (1024 GB) - still shows as GB
        val result3 = FileFormatters.formatFileSize(1024L * 1024 * 1024 * 1024)
        assertEquals("1024.0 GB", result3)
    }

    @Test
    fun `formatFileSize handles negative values`() {
        // Edge case: negative bytes (shouldn't happen in practice)
        val result = FileFormatters.formatFileSize(-100)
        assertEquals("-100 B", result)
    }

    @Test
    fun `formatFileSize handles Long MAX_VALUE`() {
        // Edge case: maximum Long value
        val result = FileFormatters.formatFileSize(Long.MAX_VALUE)
        // Should be in GB (very large number)
        assertTrue(result.endsWith("GB"))
    }

    // ========================================================================
    // formatDate Tests
    // ========================================================================

    @Test
    fun `formatDate formats epoch timestamp correctly`() {
        // January 1, 1970 00:00:00 UTC
        val result = FileFormatters.formatDate(0)

        // Depending on system locale, "Jan" might be translated
        // Just verify format structure: "MMM dd, yyyy"
        val parts = result.split(" ")
        assertEquals(3, parts.size) // e.g., ["Jan", "01,", "1970"]
        assertTrue(parts[2].matches(Regex("\\d{4}"))) // Year is 4 digits
    }

    @Test
    fun `formatDate formats recent timestamp correctly`() {
        // May 18, 2026 12:00:00 UTC (example timestamp)
        val timestamp = 1779339600000L

        val result = FileFormatters.formatDate(timestamp)

        // Verify year is 2026
        assertTrue(result.contains("2026"))

        // Verify format structure: "MMM dd, yyyy"
        val parts = result.split(" ")
        assertEquals(3, parts.size)
    }

    @Test
    fun `formatDate uses default locale`() {
        // Save current locale
        val originalLocale = Locale.getDefault()

        try {
            // Set to US locale
            Locale.setDefault(Locale.US)

            val timestamp = 1779339600000L // May 18, 2026
            val result = FileFormatters.formatDate(timestamp)

            // In US locale, month abbreviation is in English
            assertTrue(result.contains("May"))

        } finally {
            // Restore original locale
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `formatDate handles negative timestamp (before epoch)`() {
        // Before Unix epoch (should still format)
        val result = FileFormatters.formatDate(-1_000_000_000L)

        // Should contain a year (might be 1969 or before depending on timezone)
        val parts = result.split(" ")
        assertTrue(parts.size >= 3)
        assertTrue(parts[2].matches(Regex("\\d{4}")))
    }

    @Test
    fun `formatDate handles year boundaries`() {
        // December 31, 1999 12:00:00 UTC (mid-day to avoid timezone issues)
        val timestamp1 = 946641600000L
        val result1 = FileFormatters.formatDate(timestamp1)
        assertTrue(result1.contains("1999"))

        // January 1, 2000 12:00:00 UTC (Y2K, mid-day)
        val timestamp2 = 946728000000L
        val result2 = FileFormatters.formatDate(timestamp2)
        assertTrue(result2.contains("2000"))
    }
}
