package app.otter.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
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
            "application/pdf",
            "text/",
            "application/msword",
            "application/vnd.openxmlformats-officedocument",
            "application/vnd.ms-",
            "application/vnd.oasis.opendocument"
        ) to IconInfo(Icons.Default.Description, TintKey.Orange),
    )

    fun forMimeType(mimeType: String?): IconInfo {
        if (mimeType == null) return DEFAULT_ICON
        return RULES.entries
            .firstOrNull { (prefixes, _) -> prefixes.any { mimeType.startsWith(it) } }
            ?.value ?: DEFAULT_ICON
    }
}
