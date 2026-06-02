package app.otter.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for FileBrowserViewModel.NATURAL_ORDER comparator.
 *
 * Natural sort ensures "file_2" < "file_10" (not "file_10" < "file_2" lexicographically).
 * Critical for archive browsing with numbered files.
 */
class NaturalOrderComparatorTest {

    private val cmp = FileBrowserViewModel.NATURAL_ORDER

    // ========== Numeric segments ==========

    @Test
    fun `file_2 comes before file_10`() {
        assertTrue(cmp.compare("file_2", "file_10") < 0)
    }

    @Test
    fun `file_10 comes after file_9`() {
        assertTrue(cmp.compare("file_10", "file_9") > 0)
    }

    @Test
    fun `file_1 equals file_1`() {
        assertEquals(0, cmp.compare("file_1", "file_1"))
    }

    @Test
    fun `sorts list of numbered files correctly`() {
        val input = listOf("file_10", "file_2", "file_1", "file_20", "file_3")
        val sorted = input.sortedWith(cmp)
        assertEquals(listOf("file_1", "file_2", "file_3", "file_10", "file_20"), sorted)
    }

    @Test
    fun `sorts zero-padded and unpadded numbers same order`() {
        val input = listOf("file_010", "file_2", "file_1")
        val sorted = input.sortedWith(cmp)
        // 1 < 2 < 10
        assertTrue(sorted.indexOf("file_1") < sorted.indexOf("file_2"))
        assertTrue(sorted.indexOf("file_2") < sorted.indexOf("file_010"))
    }

    // ========== Pure alphabetic ==========

    @Test
    fun `alpha_a before alpha_b`() {
        assertTrue(cmp.compare("alpha_a", "alpha_b") < 0)
    }

    @Test
    fun `case insensitive A equals a`() {
        assertEquals(0, cmp.compare("File", "file"))
    }

    @Test
    fun `apple before banana alphabetically`() {
        assertTrue(cmp.compare("apple", "banana") < 0)
    }

    // ========== Mixed tokens ==========

    @Test
    fun `chapter2 before chapter10`() {
        assertTrue(cmp.compare("chapter2", "chapter10") < 0)
    }

    @Test
    fun `img_001 before img_002 before img_010`() {
        val sorted = listOf("img_010", "img_002", "img_001").sortedWith(cmp)
        assertEquals(listOf("img_001", "img_002", "img_010"), sorted)
    }

    @Test
    fun `track_1_intro before track_2_verse before track_10_outro`() {
        val sorted = listOf("track_10_outro", "track_1_intro", "track_2_verse").sortedWith(cmp)
        assertEquals(listOf("track_1_intro", "track_2_verse", "track_10_outro"), sorted)
    }

    // ========== Edge cases ==========

    @Test
    fun `empty string before any non-empty`() {
        assertTrue(cmp.compare("", "a") < 0)
    }

    @Test
    fun `pure numbers 1 before 2 before 10`() {
        val sorted = listOf("10", "2", "1").sortedWith(cmp)
        assertEquals(listOf("1", "2", "10"), sorted)
    }

    @Test
    fun `large numbers handled correctly`() {
        val sorted = listOf("file_1000000", "file_999", "file_9999").sortedWith(cmp)
        assertEquals(listOf("file_999", "file_9999", "file_1000000"), sorted)
    }

    @Test
    fun `folders before files with same prefix when names differ`() {
        // Alphabetic comparison between different named items
        assertTrue(cmp.compare("archive.zip", "backup.zip") < 0)
    }

    @Test
    fun `real archive scenario 100k file names sort correctly`() {
        val names = (1..20).map { "image_$it.png" }.shuffled()
        val sorted = names.sortedWith(cmp)
        assertEquals("image_1.png", sorted[0])
        assertEquals("image_2.png", sorted[1])
        assertEquals("image_10.png", sorted[9])
        assertEquals("image_20.png", sorted[19])
    }
}
