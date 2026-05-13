package app.otter.service

import dagger.hilt.android.scopes.ServiceScoped
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Event bus for extraction progress updates using StateFlow for progress and SharedFlow for completion.
 * Allows Service to communicate with UI without broadcasts.
 *
 * StateFlow is used for progress events because it:
 * - Always has a current value (no timing issues)
 * - Automatically replays last value to new collectors
 * - Works perfectly with Compose (no race conditions)
 *
 * SharedFlow is used for completion events because they are one-off signals.
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

    // StateFlow for progress - always has a value, no timing issues
    private val _progressState = MutableStateFlow<ProgressEvent?>(null)
    val progressState: StateFlow<ProgressEvent?> = _progressState.asStateFlow()

    // SharedFlow for completion - one-off signal
    private val _completeEvents = MutableSharedFlow<Unit>(replay = 0)
    val completeEvents: SharedFlow<Unit> = _completeEvents.asSharedFlow()

    fun emitProgress(
        fileName: String,
        currentFile: String,
        extractedCount: Int,
        totalCount: Int,
        progress: Float,
        recentFiles: List<String> = emptyList()
    ) {
        _progressState.value = ProgressEvent(
            fileName = fileName,
            currentFile = currentFile,
            extractedCount = extractedCount,
            totalCount = totalCount,
            progress = progress,
            recentFiles = recentFiles
        )
    }

    suspend fun emitComplete() {
        _completeEvents.emit(Unit)
    }

    /**
     * Resets the event bus state by clearing current value.
     * Should be called after extraction completes to prevent stale events
     * from being shown to new subscribers.
     */
    fun reset() {
        _progressState.value = null
        _completeEvents.resetReplayCache()
    }
}
