package app.otter.domain.usecase

import app.otter.domain.model.FolderCounts
import app.otter.domain.repository.ItemBrowserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class GetFolderCountsUseCase @Inject constructor(
    private val repository: ItemBrowserRepository
) {
    operator fun invoke(paths: List<String>): Flow<Pair<String, FolderCounts>> = channelFlow {
        paths.forEach { path ->
            launch {
                runCatching { repository.getFolderCounts(path) }
                    .onSuccess { counts -> send(path to counts) }
            }
        }
    }
}
