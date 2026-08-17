package app.otter.service

import javax.inject.Inject

/**
 * Groups the extraction infrastructure FileBrowserViewModel exposes to the UI —
 * the progress event bus and the background extraction queue.
 */
class ExtractionCoordinator @Inject constructor(
    val eventBus: ExtractionEventBus,
    val extractionQueue: ExtractionQueue,
)
