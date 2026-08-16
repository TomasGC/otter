package app.otter.util

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
import androidx.compose.ui.graphics.vector.ImageVector
import app.otter.domain.model.MimeGroups

object FileTypeIconInfo {

    enum class TintKey {
        Green,
        Red,
        Blue,
        Orange,
        Surface
    }

    data class IconInfo(val icon: ImageVector, val tint: TintKey)

    private val DEFAULT_ICON = IconInfo(Icons.Default.Help, TintKey.Surface)

    private val RULES: Map<List<String>, IconInfo> = mapOf(
        MimeGroups.IMAGE to IconInfo(Icons.Default.Image, TintKey.Green),
        MimeGroups.VIDEO to IconInfo(Icons.Default.Movie, TintKey.Red),
        MimeGroups.AUDIO to IconInfo(Icons.Default.MusicNote, TintKey.Blue),
        MimeGroups.ARCHIVE to IconInfo(Icons.Default.FolderZip, TintKey.Orange),
        MimeGroups.PDF to IconInfo(Icons.Default.PictureAsPdf, TintKey.Red),
        MimeGroups.SPREADSHEET to IconInfo(Icons.Default.TableChart, TintKey.Green),
        MimeGroups.PRESENTATION to IconInfo(Icons.Default.Slideshow, TintKey.Orange),
        MimeGroups.TEXT_DOCUMENT to IconInfo(Icons.Default.Description, TintKey.Blue),
    )

    fun forMimeType(mimeType: String?): IconInfo {
        if (mimeType == null) return DEFAULT_ICON
        return RULES.entries
            .firstOrNull { (prefixes, _) -> prefixes.any { mimeType.startsWith(it) } }
            ?.value ?: DEFAULT_ICON
    }
}
