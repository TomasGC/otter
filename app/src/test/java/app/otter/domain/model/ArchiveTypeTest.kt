package app.otter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArchiveTypeTest {

    @Test
    fun `should detect ZIP from lowercase extension`() {
        val result = ArchiveType.fromFileName("test.zip")
        assertEquals(ArchiveType.ZIP, result)
    }

    @Test
    fun `should detect ZIP from uppercase extension`() {
        val result = ArchiveType.fromFileName("TEST.ZIP")
        assertEquals(ArchiveType.ZIP, result)
    }

    @Test
    fun `should detect ZIP from mixed case extension`() {
        val result = ArchiveType.fromFileName("MyFile.ZiP")
        assertEquals(ArchiveType.ZIP, result)
    }

    @Test
    fun `should detect ZIP with path`() {
        val result = ArchiveType.fromFileName("/path/to/archive.zip")
        assertEquals(ArchiveType.ZIP, result)
    }

    @Test
    fun `should return null for unknown extension`() {
        val result = ArchiveType.fromFileName("document.pdf")
        assertNull(result)
    }

    @Test
    fun `should return null for file without extension`() {
        val result = ArchiveType.fromFileName("noextension")
        assertNull(result)
    }

    @Test
    fun `should handle file with multiple dots`() {
        val result = ArchiveType.fromFileName("my.archive.file.zip")
        assertEquals(ArchiveType.ZIP, result)
    }
}
