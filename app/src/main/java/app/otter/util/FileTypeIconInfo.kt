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
        listOf("image/") to IconInfo(Icons.Default.Image, TintKey.Green),
        listOf("video/") to IconInfo(Icons.Default.Movie, TintKey.Red),
        listOf("audio/") to IconInfo(Icons.Default.MusicNote, TintKey.Blue),
        listOf(
            "application/zip",
            "application/x-zip",
            "application/x-rar",
            "application/vnd.rar",
            "application/x-7z-compressed",
            "application/x-tar",
            "application/gzip",
            "application/x-bzip",
            "application/x-compressed-tar",
            "application/x-bzip-compressed-tar",
            "application/x-xz-compressed-tar",
            "application/x-rpa",
        ) to IconInfo(Icons.Default.FolderZip, TintKey.Orange),
        listOf("application/pdf") to IconInfo(Icons.Default.PictureAsPdf, TintKey.Red),
        listOf(
            "text/csv",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml",
            "application/vnd.oasis.opendocument.spreadsheet",
        ) to IconInfo(Icons.Default.TableChart, TintKey.Green),
        listOf(
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml",
            "application/vnd.oasis.opendocument.presentation",
        ) to IconInfo(Icons.Default.Slideshow, TintKey.Orange),
        listOf(
            "text/",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml",
            "application/vnd.ms-",
            "application/vnd.oasis.opendocument",
        ) to IconInfo(Icons.Default.Description, TintKey.Blue),
    )

    fun forMimeType(mimeType: String?): IconInfo {
        if (mimeType == null) return DEFAULT_ICON
        return RULES.entries
            .firstOrNull { (prefixes, _) -> prefixes.any { mimeType.startsWith(it) } }
            ?.value ?: DEFAULT_ICON
    }
}
