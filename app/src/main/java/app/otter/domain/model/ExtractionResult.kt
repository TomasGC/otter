package app.otter.domain.model

sealed class ExtractionResult {
    data class Success(
        val outputPath: String,
        val extractedFilesCount: Int
    ) : ExtractionResult()

    data class Failure(
        val errorMessage: String,
        val cause: Exception?
    ) : ExtractionResult()
}
