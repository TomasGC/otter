package app.otter.unit.domain.model

import app.otter.domain.model.FileCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class FileCategoryTest {

    @Test
    fun `null mimeType returns OTHER`() {
        assertEquals(FileCategory.OTHER, FileCategory.forMimeType(null))
    }

    @Test
    fun `unknown mimeType returns OTHER`() {
        assertEquals(FileCategory.OTHER, FileCategory.forMimeType("application/octet-stream"))
    }

    @Test
    fun `image mimeType returns IMAGE`() {
        assertEquals(FileCategory.IMAGE, FileCategory.forMimeType("image/png"))
    }

    @Test
    fun `video mimeType returns VIDEO`() {
        assertEquals(FileCategory.VIDEO, FileCategory.forMimeType("video/mp4"))
    }

    @Test
    fun `audio mimeType returns AUDIO`() {
        assertEquals(FileCategory.AUDIO, FileCategory.forMimeType("audio/mpeg"))
    }

    @Test
    fun `zip mimeType returns ARCHIVE`() {
        assertEquals(FileCategory.ARCHIVE, FileCategory.forMimeType("application/zip"))
    }

    @Test
    fun `rpa mimeType returns ARCHIVE`() {
        assertEquals(FileCategory.ARCHIVE, FileCategory.forMimeType("application/x-rpa"))
    }

    @Test
    fun `pdf mimeType returns DOCUMENT`() {
        assertEquals(FileCategory.DOCUMENT, FileCategory.forMimeType("application/pdf"))
    }

    @Test
    fun `text mimeType returns DOCUMENT`() {
        assertEquals(FileCategory.DOCUMENT, FileCategory.forMimeType("text/plain"))
    }

    @Test
    fun `msword mimeType returns DOCUMENT`() {
        assertEquals(FileCategory.DOCUMENT, FileCategory.forMimeType("application/msword"))
    }

    @Test
    fun `csv mimeType returns SPREADSHEET`() {
        assertEquals(FileCategory.SPREADSHEET, FileCategory.forMimeType("text/csv"))
    }

    @Test
    fun `ms-excel mimeType returns SPREADSHEET`() {
        assertEquals(FileCategory.SPREADSHEET, FileCategory.forMimeType("application/vnd.ms-excel"))
    }

    @Test
    fun `ms-powerpoint mimeType returns PRESENTATION`() {
        assertEquals(FileCategory.PRESENTATION, FileCategory.forMimeType("application/vnd.ms-powerpoint"))
    }

    // ========== OASIS prefix collisions (SPREADSHEET/PRESENTATION vs generic TEXT_DOCUMENT) ==========
    // These all share the "application/vnd.oasis.opendocument" prefix used by TEXT_DOCUMENT,
    // so correctness depends on RULES checking the more specific SPREADSHEET/PRESENTATION
    // entries first (insertion-order dependent - see MimeGroups/RULES ordering).

    @Test
    fun `oasis spreadsheet mimeType returns SPREADSHEET not DOCUMENT`() {
        assertEquals(
            FileCategory.SPREADSHEET,
            FileCategory.forMimeType("application/vnd.oasis.opendocument.spreadsheet")
        )
    }

    @Test
    fun `oasis presentation mimeType returns PRESENTATION not DOCUMENT`() {
        assertEquals(
            FileCategory.PRESENTATION,
            FileCategory.forMimeType("application/vnd.oasis.opendocument.presentation")
        )
    }

    @Test
    fun `oasis text mimeType returns DOCUMENT`() {
        assertEquals(FileCategory.DOCUMENT, FileCategory.forMimeType("application/vnd.oasis.opendocument.text"))
    }

    // ========== Case sensitivity ==========
    // Android's MimeTypeMap/ContentResolver always produce lowercase MIME types, so this
    // documents current (case-sensitive) behavior rather than asserting a requirement.

    @Test
    fun `uppercase mimeType prefix does not match and falls back to OTHER`() {
        assertEquals(FileCategory.OTHER, FileCategory.forMimeType("IMAGE/PNG"))
    }

    @Test
    fun `mixed case mimeType prefix does not match and falls back to OTHER`() {
        assertEquals(FileCategory.OTHER, FileCategory.forMimeType("Video/Mp4"))
    }

    // ========== Remaining ARCHIVE MIME types (only zip and x-rpa were previously tested) ==========

    @Test
    fun `x-rar mimeType returns ARCHIVE`() {
        assertEquals(FileCategory.ARCHIVE, FileCategory.forMimeType("application/x-rar"))
    }

    @Test
    fun `vnd rar mimeType returns ARCHIVE`() {
        assertEquals(FileCategory.ARCHIVE, FileCategory.forMimeType("application/vnd.rar"))
    }

    @Test
    fun `x-7z-compressed mimeType returns ARCHIVE`() {
        assertEquals(FileCategory.ARCHIVE, FileCategory.forMimeType("application/x-7z-compressed"))
    }

    @Test
    fun `x-tar mimeType returns ARCHIVE`() {
        assertEquals(FileCategory.ARCHIVE, FileCategory.forMimeType("application/x-tar"))
    }

    @Test
    fun `gzip mimeType returns ARCHIVE`() {
        assertEquals(FileCategory.ARCHIVE, FileCategory.forMimeType("application/gzip"))
    }

    @Test
    fun `x-bzip mimeType returns ARCHIVE`() {
        assertEquals(FileCategory.ARCHIVE, FileCategory.forMimeType("application/x-bzip"))
    }

    @Test
    fun `x-compressed-tar mimeType returns ARCHIVE`() {
        assertEquals(FileCategory.ARCHIVE, FileCategory.forMimeType("application/x-compressed-tar"))
    }

    @Test
    fun `x-bzip-compressed-tar mimeType returns ARCHIVE`() {
        assertEquals(FileCategory.ARCHIVE, FileCategory.forMimeType("application/x-bzip-compressed-tar"))
    }

    @Test
    fun `x-xz-compressed-tar mimeType returns ARCHIVE`() {
        assertEquals(FileCategory.ARCHIVE, FileCategory.forMimeType("application/x-xz-compressed-tar"))
    }
}
