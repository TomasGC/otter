package app.otter.domain.repository

import app.otter.domain.model.FileCategory
import app.otter.domain.model.FileCategoryFilterState
import app.otter.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun setCacheWindowSize(size: Int)
    suspend fun setFileCategoryFilter(category: FileCategory, state: FileCategoryFilterState?)
}
