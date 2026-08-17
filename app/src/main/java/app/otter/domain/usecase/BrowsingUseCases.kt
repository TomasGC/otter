package app.otter.domain.usecase

import javax.inject.Inject

/**
 * Groups the use cases FileBrowserViewModel needs to list a directory/archive and
 * report folder item counts — always used together, never independently.
 */
class BrowsingUseCases @Inject constructor(
    val browseItems: BrowseItemsUseCase,
    val getFolderCounts: GetFolderCountsUseCase,
)
