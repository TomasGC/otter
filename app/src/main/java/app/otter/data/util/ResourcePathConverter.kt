package app.otter.data.util

import android.net.Uri
import app.otter.domain.model.ResourcePath

/**
 * Converts between Android Uri and domain ResourcePath.
 * Located in data layer as it bridges Android framework and domain.
 */
object ResourcePathConverter {

    /**
     * Converts Android Uri to domain ResourcePath.
     */
    fun fromUri(uri: Uri): ResourcePath = ResourcePath(uri.toString())

    /**
     * Converts domain ResourcePath to Android Uri.
     */
    fun toUri(path: ResourcePath): Uri = Uri.parse(path.value)
}
