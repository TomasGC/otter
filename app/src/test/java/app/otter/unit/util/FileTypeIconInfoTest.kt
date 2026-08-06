package app.otter.unit.util

import app.otter.util.FileTypeIconInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import org.junit.Assert.assertEquals
import org.junit.Test

class FileTypeIconInfoTest {

    @Test
    fun `null mimeType returns default help icon with Surface tint`() {
        val info = FileTypeIconInfo.forMimeType(null)
        assertEquals(Icons.Default.Help, info.icon)
        assertEquals(FileTypeIconInfo.TintKey.Surface, info.tint)
    }

    @Test
    fun `unknown mimeType returns default help icon with Surface tint`() {
        val info = FileTypeIconInfo.forMimeType("application/octet-stream")
        assertEquals(Icons.Default.Help, info.icon)
        assertEquals(FileTypeIconInfo.TintKey.Surface, info.tint)
    }

    @Test
    fun `image mimeType prefix returns Image icon with Green tint`() {
        val info = FileTypeIconInfo.forMimeType("image/jpeg")
        assertEquals(Icons.Default.Image, info.icon)
        assertEquals(FileTypeIconInfo.TintKey.Green, info.tint)
    }

    @Test
    fun `image png returns Image icon`() {
        val info = FileTypeIconInfo.forMimeType("image/png")
        assertEquals(Icons.Default.Image, info.icon)
    }

    @Test
    fun `video mimeType prefix returns Movie icon with Red tint`() {
        val info = FileTypeIconInfo.forMimeType("video/mp4")
        assertEquals(Icons.Default.Movie, info.icon)
        assertEquals(FileTypeIconInfo.TintKey.Red, info.tint)
    }

    @Test
    fun `audio mimeType prefix returns MusicNote icon with Blue tint`() {
        val info = FileTypeIconInfo.forMimeType("audio/mpeg")
        assertEquals(Icons.Default.MusicNote, info.icon)
        assertEquals(FileTypeIconInfo.TintKey.Blue, info.tint)
    }

    @Test
    fun `pdf returns PictureAsPdf icon with Red tint`() {
        val info = FileTypeIconInfo.forMimeType("application/pdf")
        assertEquals(Icons.Default.PictureAsPdf, info.icon)
        assertEquals(FileTypeIconInfo.TintKey.Red, info.tint)
    }

    @Test
    fun `text prefix returns Description icon with Blue tint`() {
        val info = FileTypeIconInfo.forMimeType("text/plain")
        assertEquals(Icons.Default.Description, info.icon)
        assertEquals(FileTypeIconInfo.TintKey.Blue, info.tint)
    }

    @Test
    fun `msword returns Description icon`() {
        val info = FileTypeIconInfo.forMimeType("application/msword")
        assertEquals(Icons.Default.Description, info.icon)
    }

    @Test
    fun `ooxml word returns Description icon`() {
        val info = FileTypeIconInfo.forMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        assertEquals(Icons.Default.Description, info.icon)
    }

    @Test
    fun `ms excel returns TableChart icon with Green tint`() {
        val info = FileTypeIconInfo.forMimeType("application/vnd.ms-excel")
        assertEquals(Icons.Default.TableChart, info.icon)
        assertEquals(FileTypeIconInfo.TintKey.Green, info.tint)
    }

    @Test
    fun `odf text returns Description icon`() {
        val info = FileTypeIconInfo.forMimeType("application/vnd.oasis.opendocument.text")
        assertEquals(Icons.Default.Description, info.icon)
    }

    @Test
    fun `first matching rule wins for ambiguous prefix`() {
        val imageInfo = FileTypeIconInfo.forMimeType("image/svg+xml")
        assertEquals(FileTypeIconInfo.TintKey.Green, imageInfo.tint)
    }

    @Test
    fun `audio ogg returns MusicNote icon with Blue tint`() {
        val info = FileTypeIconInfo.forMimeType("audio/ogg")
        assertEquals(Icons.Default.MusicNote, info.icon)
        assertEquals(FileTypeIconInfo.TintKey.Blue, info.tint)
    }

    @Test
    fun `audio wav returns MusicNote icon`() {
        val info = FileTypeIconInfo.forMimeType("audio/wav")
        assertEquals(Icons.Default.MusicNote, info.icon)
    }

    @Test
    fun `video webm returns Movie icon with Red tint`() {
        val info = FileTypeIconInfo.forMimeType("video/webm")
        assertEquals(Icons.Default.Movie, info.icon)
        assertEquals(FileTypeIconInfo.TintKey.Red, info.tint)
    }

    @Test
    fun `video quicktime returns Movie icon`() {
        val info = FileTypeIconInfo.forMimeType("video/quicktime")
        assertEquals(Icons.Default.Movie, info.icon)
    }

    @Test
    fun `text html returns Description icon with Blue tint`() {
        val info = FileTypeIconInfo.forMimeType("text/html")
        assertEquals(Icons.Default.Description, info.icon)
        assertEquals(FileTypeIconInfo.TintKey.Blue, info.tint)
    }

    @Test
    fun `text csv returns TableChart icon with Green tint`() {
        val info = FileTypeIconInfo.forMimeType("text/csv")
        assertEquals(Icons.Default.TableChart, info.icon)
        assertEquals(FileTypeIconInfo.TintKey.Green, info.tint)
    }

    @Test
    fun `empty string mimeType returns default help icon with Surface tint`() {
        val info = FileTypeIconInfo.forMimeType("")
        assertEquals(Icons.Default.Help, info.icon)
        assertEquals(FileTypeIconInfo.TintKey.Surface, info.tint)
    }

    @Test
    fun `xlsx spreadsheet ooxml returns TableChart icon with Green tint`() {
        val info = FileTypeIconInfo.forMimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        assertEquals(Icons.Default.TableChart, info.icon)
        assertEquals(FileTypeIconInfo.TintKey.Green, info.tint)
    }

    @Test
    fun `odf spreadsheet returns TableChart icon with Green tint`() {
        val info = FileTypeIconInfo.forMimeType("application/vnd.oasis.opendocument.spreadsheet")
        assertEquals(Icons.Default.TableChart, info.icon)
        assertEquals(FileTypeIconInfo.TintKey.Green, info.tint)
    }

    @Test
    fun `ms powerpoint returns Slideshow icon with Orange tint`() {
        val info = FileTypeIconInfo.forMimeType("application/vnd.ms-powerpoint")
        assertEquals(Icons.Default.Slideshow, info.icon)
        assertEquals(FileTypeIconInfo.TintKey.Orange, info.tint)
    }

    @Test
    fun `zip returns FolderZip icon with Orange tint`() {
        val info = FileTypeIconInfo.forMimeType("application/zip")
        assertEquals(Icons.Default.FolderZip, info.icon)
        assertEquals(FileTypeIconInfo.TintKey.Orange, info.tint)
    }

    @Test
    fun `rar returns FolderZip icon with Orange tint`() {
        val info = FileTypeIconInfo.forMimeType("application/x-rar-compressed")
        assertEquals(Icons.Default.FolderZip, info.icon)
        assertEquals(FileTypeIconInfo.TintKey.Orange, info.tint)
    }

    @Test
    fun `7z returns FolderZip icon with Orange tint`() {
        val info = FileTypeIconInfo.forMimeType("application/x-7z-compressed")
        assertEquals(Icons.Default.FolderZip, info.icon)
        assertEquals(FileTypeIconInfo.TintKey.Orange, info.tint)
    }

    @Test
    fun `tar returns FolderZip icon with Orange tint`() {
        val info = FileTypeIconInfo.forMimeType("application/x-tar")
        assertEquals(Icons.Default.FolderZip, info.icon)
        assertEquals(FileTypeIconInfo.TintKey.Orange, info.tint)
    }

    @Test
    fun `gzip returns FolderZip icon with Orange tint`() {
        val info = FileTypeIconInfo.forMimeType("application/gzip")
        assertEquals(Icons.Default.FolderZip, info.icon)
        assertEquals(FileTypeIconInfo.TintKey.Orange, info.tint)
    }

    @Test
    fun `rpa returns FolderZip icon with Orange tint`() {
        val info = FileTypeIconInfo.forMimeType("application/x-rpa")
        assertEquals(Icons.Default.FolderZip, info.icon)
        assertEquals(FileTypeIconInfo.TintKey.Orange, info.tint)
    }
}
