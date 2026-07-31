package app.otter.data.extractor

import java.io.File

data class ExtractionOptions(
    val sourceFile: File? = null,
    val selectedItems: List<String>? = null
)
