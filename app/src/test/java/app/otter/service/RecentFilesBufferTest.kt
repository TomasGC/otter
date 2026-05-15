package app.otter.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecentFilesBufferTest {

    private lateinit var buffer: RecentFilesBuffer

    @Before
    fun setup() {
        buffer = RecentFilesBuffer(maxSize = 3)
    }

    @Test
    fun `add single file`() {
        buffer.add("file1.txt")

        assertEquals(1, buffer.size())
        assertEquals(listOf("file1.txt"), buffer.getFiles())
        assertEquals("file1.txt", buffer.getCurrentFile())
    }

    @Test
    fun `add multiple files within limit`() {
        buffer.add("file1.txt")
        buffer.add("file2.jpg")
        buffer.add("file3.pdf")

        assertEquals(3, buffer.size())
        assertEquals(listOf("file1.txt", "file2.jpg", "file3.pdf"), buffer.getFiles())
        assertEquals("file3.pdf", buffer.getCurrentFile())
    }

    @Test
    fun `add files beyond limit removes oldest`() {
        buffer.add("file1.txt")
        buffer.add("file2.jpg")
        buffer.add("file3.pdf")
        buffer.add("file4.doc") // Should remove file1.txt

        assertEquals(3, buffer.size())
        assertEquals(listOf("file2.jpg", "file3.pdf", "file4.doc"), buffer.getFiles())
        assertEquals("file4.doc", buffer.getCurrentFile())
    }

    @Test
    fun `getCompletedFiles excludes current file`() {
        buffer.add("file1.txt")
        buffer.add("file2.jpg")
        buffer.add("file3.pdf")

        assertEquals(listOf("file1.txt", "file2.jpg"), buffer.getCompletedFiles())
    }

    @Test
    fun `getCompletedFiles returns empty when only one file`() {
        buffer.add("file1.txt")

        assertTrue(buffer.getCompletedFiles().isEmpty())
    }

    @Test
    fun `clear removes all files`() {
        buffer.add("file1.txt")
        buffer.add("file2.jpg")

        buffer.clear()

        assertTrue(buffer.isEmpty())
        assertEquals(0, buffer.size())
        assertEquals(emptyList<String>(), buffer.getFiles())
        assertEquals(null, buffer.getCurrentFile())
    }

    @Test
    fun `getCurrentFile returns null when empty`() {
        assertEquals(null, buffer.getCurrentFile())
    }

    @Test
    fun `circular buffer maintains order`() {
        val buffer = RecentFilesBuffer(maxSize = 2)

        buffer.add("file1.txt")
        buffer.add("file2.jpg")
        buffer.add("file3.pdf") // Removes file1.txt
        buffer.add("file4.doc") // Removes file2.jpg

        assertEquals(listOf("file3.pdf", "file4.doc"), buffer.getFiles())
    }
}
