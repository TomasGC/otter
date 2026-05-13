package app.otter.service

import dagger.hilt.android.scopes.ServiceScoped
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Event bus for extraction progress updates using SharedFlow.
 * Allows Service to communicate with UI without broadcasts.
 *
 * This class is injectable via Hilt, allowing proper test isolation.
 * Each test gets its own instance, preventing cross-test event pollution.
 */
@Singleton
class ExtractionEventBus @Inject constructor() {

    data class ProgressEvent(
        val fileName: String,
        val currentFile: String,
        val extractedCount: Int,
        val totalCount: Int,
        val progress: Float,
        val recentFiles: List<String> = emptyList()
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
        progress: Float,
        recentFiles: List<String> = emptyList()
    ) {
        _progressEvents.emit(
            ProgressEvent(
                fileName = fileName,
                currentFile = currentFile,
                extractedCount = extractedCount,
                totalCount = totalCount,
                progress = progress,
                recentFiles = recentFiles
            )
        )
    }

    suspend fun emitComplete() {
        _completeEvents.emit(Unit)
    }

    /**
     * Resets the event bus state by clearing replay cache.
     * Should be called after extraction completes to prevent stale events
     * from being replayed to new subscribers.
     */
    fun reset() {
        _progressEvents.resetReplayCache()
        _completeEvents.resetReplayCache()
    }
}
