package app.otter.util
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MimeTypeUtilTest {

    private lateinit var util: MimeTypeUtil

    @Before
    fun setup() {
        util = MimeTypeUtil()
    }

    // ===== Known archive types =====

    @Test
    fun `zip returns application slash zip`() {
        assertEquals("application/zip", util.getMimeType("file.zip"))
    }

    @Test
    fun `rar returns application slash x-rar-compressed`() {
        assertEquals("application/x-rar-compressed", util.getMimeType("file.rar"))
    }

    @Test
    fun `7z returns application slash x-7z-compressed`() {
        assertEquals("application/x-7z-compressed", util.getMimeType("file.7z"))
    }

    @Test
    fun `tar returns application slash x-tar`() {
        assertEquals("application/x-tar", util.getMimeType("archive.tar"))
    }

    @Test
    fun `gz returns application slash gzip`() {
        assertEquals("application/gzip", util.getMimeType("photo.jpg.gz"))
    }

    @Test
    fun `gzip extension returns application slash gzip`() {
        assertEquals("application/gzip", util.getMimeType("doc.txt.gzip"))
    }

    @Test
    fun `tgz returns application slash x-compressed-tar`() {
        assertEquals("application/x-compressed-tar", util.getMimeType("backup.tgz"))
    }

    @Test
    fun `tbz2 returns application slash x-bzip-compressed-tar`() {
        assertEquals("application/x-bzip-compressed-tar", util.getMimeType("archive.tbz2"))
    }

    @Test
    fun `rpa returns application slash x-rpa`() {
        assertEquals("application/x-rpa", util.getMimeType("game.rpa"))
    }

    // ===== Common file types =====

    @Test
    fun `jpg returns image slash jpeg`() {
        assertEquals("image/jpeg", util.getMimeType("photo.jpg"))
    }

    @Test
    fun `pdf returns application slash pdf`() {
        assertEquals("application/pdf", util.getMimeType("doc.pdf"))
    }

    @Test
    fun `txt returns text slash plain`() {
        assertEquals("text/plain", util.getMimeType("readme.txt"))
    }

    // ===== Unknown / edge cases =====

    @Test
    fun `unknown extension returns application slash octet-stream`() {
        assertEquals("application/octet-stream", util.getMimeType("file.xyz"))
    }

    @Test
    fun `no extension returns application slash octet-stream`() {
        assertEquals("application/octet-stream", util.getMimeType("noextension"))
    }

    @Test
    fun `empty string returns application slash octet-stream`() {
        assertEquals("application/octet-stream", util.getMimeType(""))
    }

    // ===== Case insensitivity =====

    @Test
    fun `uppercase ZIP extension returns application slash zip`() {
        assertEquals("application/zip", util.getMimeType("FILE.ZIP"))
    }

    @Test
    fun `mixed case Zip extension returns application slash zip`() {
        assertEquals("application/zip", util.getMimeType("archive.ZiP"))
    }

    // ===== Multi-dot filenames =====

    @Test
    fun `tar dot gz returns application slash x-compressed-tar`() {
        // getMimeType checks the two-part compound extension "tar.gz" before falling back to "gz"
        assertEquals("application/x-compressed-tar", util.getMimeType("backup.tar.gz"))
    }

    @Test
    fun `tar dot bz2 returns application slash x-bzip-compressed-tar`() {
        assertEquals("application/x-bzip-compressed-tar", util.getMimeType("archive.tar.bz2"))
    }

    @Test
    fun `tar dot xz returns application slash x-xz-compressed-tar`() {
        assertEquals("application/x-xz-compressed-tar", util.getMimeType("archive.tar.xz"))
    }

    @Test
    fun `multi-dot filename with zip returns application slash zip`() {
        assertEquals("application/zip", util.getMimeType("my.archive.v1.zip"))
    }

    @Test
    fun `name with dots in base tar gz still resolves`() {
        assertEquals("application/x-compressed-tar", util.getMimeType("my.backup.2026.tar.gz"))
    }

    @Test
    fun `getMimeType strips path prefix before extension lookup`() {
        assertEquals("application/x-compressed-tar", util.getMimeType("some/dir/archive.tar.gz"))
    }
}
