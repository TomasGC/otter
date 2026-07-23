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

    @Test
    fun `should detect RAR from lowercase extension`() {
        val result = ArchiveType.fromFileName("test.rar")
        assertEquals(ArchiveType.RAR, result)
    }

    @Test
    fun `should detect RAR from uppercase extension`() {
        val result = ArchiveType.fromFileName("TEST.RAR")
        assertEquals(ArchiveType.RAR, result)
    }

    @Test
    fun `should detect RAR from mixed case extension`() {
        val result = ArchiveType.fromFileName("MyFile.RaR")
        assertEquals(ArchiveType.RAR, result)
    }

    @Test
    fun `should detect RAR with path`() {
        val result = ArchiveType.fromFileName("/path/to/archive.rar")
        assertEquals(ArchiveType.RAR, result)
    }

    @Test
    fun `should detect SEVEN_ZIP from 7z extension`() {
        assertEquals(ArchiveType.SEVEN_ZIP, ArchiveType.fromFileName("archive.7z"))
    }

    @Test
    fun `should detect SEVEN_ZIP from uppercase 7Z extension`() {
        assertEquals(ArchiveType.SEVEN_ZIP, ArchiveType.fromFileName("ARCHIVE.7Z"))
    }

    @Test
    fun `should detect TAR from tar extension`() {
        assertEquals(ArchiveType.TAR, ArchiveType.fromFileName("archive.tar"))
    }

    @Test
    fun `should detect TAR_GZ from tar dot gz extension`() {
        assertEquals(ArchiveType.TAR_GZ, ArchiveType.fromFileName("archive.tar.gz"))
    }

    @Test
    fun `should detect TAR_GZ from tgz extension`() {
        assertEquals(ArchiveType.TAR_GZ, ArchiveType.fromFileName("archive.tgz"))
    }

    @Test
    fun `should detect TAR_GZ from uppercase TAR dot GZ extension`() {
        assertEquals(ArchiveType.TAR_GZ, ArchiveType.fromFileName("BACKUP.TAR.GZ"))
    }

    @Test
    fun `should detect TAR_BZ2 from tar dot bz2 extension`() {
        assertEquals(ArchiveType.TAR_BZ2, ArchiveType.fromFileName("archive.tar.bz2"))
    }

    @Test
    fun `should detect TAR_BZ2 from tbz2 extension`() {
        assertEquals(ArchiveType.TAR_BZ2, ArchiveType.fromFileName("archive.tbz2"))
    }

    @Test
    fun `should detect GZIP from gz extension`() {
        assertEquals(ArchiveType.GZIP, ArchiveType.fromFileName("photo.jpg.gz"))
    }

    @Test
    fun `should detect GZIP from gzip extension`() {
        assertEquals(ArchiveType.GZIP, ArchiveType.fromFileName("document.txt.gzip"))
    }

    @Test
    fun `should detect RPA from rpa extension`() {
        assertEquals(ArchiveType.RPA, ArchiveType.fromFileName("game.rpa"))
    }

    @Test
    fun `tar dot gz takes priority over plain gz — returns TAR_GZ not GZIP`() {
        // Multi-ext sorted by longest extension first: .tar.gz (7) > .gz (3)
        assertEquals(ArchiveType.TAR_GZ, ArchiveType.fromFileName("backup.tar.gz"))
    }

    @Test
    fun `should return null for empty string`() {
        assertNull(ArchiveType.fromFileName(""))
    }

    @Test
    fun `should return null for dot-only filename`() {
        assertNull(ArchiveType.fromFileName("."))
    }
}
