package app.otter.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event bus for extraction progress updates using SharedFlow.
 * Allows Service to communicate with UI without broadcasts.
 */
object ExtractionEventBus {

    data class ProgressEvent(
        val fileName: String,
        val currentFile: String,
        val extractedCount: Int,
        val totalCount: Int,
        val progress: Float
    )

    private val _progressEvents = MutableSharedFlow<ProgressEvent>(replay = 1)
    val progressEvents: SharedFlow<ProgressEvent> = _progressEvents.asSharedFlow()

    private val _completeEvents = MutableSharedFlow<Unit>(replay = 0)
    val completeEvents: SharedFlow<Unit> = _completeEvents.asSharedFlow()

    suspend fun emitProgress(
        fileName: String,
        currentFile: String,
        extractedCount: Int,
        totalCount: Int,
        progress: Float
    ) {
        _progressEvents.emit(
            ProgressEvent(
                fileName = fileName,
                currentFile = currentFile,
                extractedCount = extractedCount,
                totalCount = totalCount,
                progress = progress
            )
        )
    }

    suspend fun emitComplete() {
        _completeEvents.emit(Unit)
    }
}
