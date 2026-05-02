package app.otter.data.extractor

/**
 * Strategy for calculating extraction progress.
 *
 * Different archive formats may have different ways of calculating progress:
 * - Known total count: Standard percentage calculation
 * - Unknown total (streaming formats): Indeterminate progress
 * - Single file (GZIP): Always 100% after extraction
 */
interface ProgressCalculator {
    /**
     * Calculates progress percentage based on extracted and total counts.
     *
     * @param extractedCount Number of files extracted so far
     * @param totalCount Total number of files to extract (-1 if unknown)
     * @return Progress value between 0.0 and 1.0
     */
    fun calculate(extractedCount: Int, totalCount: Int): Float
}

/**
 * Standard progress calculator for formats with known total count.
 * Used by: ZIP, RAR, 7z
 */
class StandardProgressCalculator : ProgressCalculator {
    override fun calculate(extractedCount: Int, totalCount: Int): Float {
        return if (totalCount > 0) extractedCount.toFloat() / totalCount else 0f
    }
}

/**
 * Indeterminate progress calculator for streaming formats without total count.
 * Used by: TAR (cannot count without consuming stream)
 *
 * Shows incremental progress but never reaches 100% until completion.
 */
class IndeterminateProgressCalculator : ProgressCalculator {
    override fun calculate(extractedCount: Int, totalCount: Int): Float {
        // For streaming formats, show progress as incrementing value
        // but never 100% (template method handles final 100%)
        return (extractedCount.toFloat() / (extractedCount + 1)).coerceAtMost(0.99f)
    }
}

/**
 * Single file progress calculator for compression formats.
 * Used by: GZIP (single file compression)
 *
 * Always returns 100% since there's only one file.
 */
class SingleFileProgressCalculator : ProgressCalculator {
    override fun calculate(extractedCount: Int, totalCount: Int): Float {
        return if (extractedCount > 0) 1.0f else 0f
    }
}
