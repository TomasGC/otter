package app.otter.unit.util

import app.otter.util.FileTypeIconInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
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
    fun `pdf returns Description icon with Orange tint`() {
        val info = FileTypeIconInfo.forMimeType("application/pdf")
        assertEquals(Icons.Default.Description, info.icon)
        assertEquals(FileTypeIconInfo.TintKey.Orange, info.tint)
    }

    @Test
    fun `text prefix returns Description icon`() {
        val info = FileTypeIconInfo.forMimeType("text/plain")
        assertEquals(Icons.Default.Description, info.icon)
        assertEquals(FileTypeIconInfo.TintKey.Orange, info.tint)
    }

    @Test
    fun `msword returns Description icon`() {
        val info = FileTypeIconInfo.forMimeType("application/msword")
        assertEquals(Icons.Default.Description, info.icon)
    }

    @Test
    fun `ooxml returns Description icon`() {
        val info = FileTypeIconInfo.forMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        assertEquals(Icons.Default.Description, info.icon)
    }

    @Test
    fun `ms office format returns Description icon`() {
        val info = FileTypeIconInfo.forMimeType("application/vnd.ms-excel")
        assertEquals(Icons.Default.Description, info.icon)
    }

    @Test
    fun `odf returns Description icon`() {
        val info = FileTypeIconInfo.forMimeType("application/vnd.oasis.opendocument.text")
        assertEquals(Icons.Default.Description, info.icon)
    }

    @Test
    fun `first matching rule wins for ambiguous prefix`() {
        // image/ matches before text/
        val imageInfo = FileTypeIconInfo.forMimeType("image/svg+xml")
        assertEquals(FileTypeIconInfo.TintKey.Green, imageInfo.tint)
    }
}
