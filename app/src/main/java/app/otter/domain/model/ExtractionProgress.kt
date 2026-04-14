package app.otter.domain.model

sealed class ExtractionProgress {
    data object Idle : ExtractionProgress()

    data class Extracting(
        val currentFile: String,
        val extractedCount: Int,
        val totalCount: Int,
        val progress: Float
    ) : ExtractionProgress()

    data class Success(
        val outputPath: String,
        val extractedCount: Int
    ) : ExtractionProgress()

    data class Error(
        val message: String,
        val exception: Exception?
    ) : ExtractionProgress()
}
