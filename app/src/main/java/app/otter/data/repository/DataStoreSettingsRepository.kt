package app.otter.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.otter.domain.model.FileCategory
import app.otter.domain.model.FileCategoryFilterState
import app.otter.domain.model.UserSettings
import app.otter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    override val settings: Flow<UserSettings> = dataStore.data.map { prefs ->
        UserSettings(
            cacheWindowSize = (prefs[CACHE_WINDOW_SIZE_KEY] ?: UserSettings.DEFAULT_CACHE_WINDOW_SIZE)
                .coerceIn(UserSettings.MIN_CACHE_WINDOW_SIZE, UserSettings.MAX_CACHE_WINDOW_SIZE),
            fileCategoryFilters = FileCategory.entries.mapNotNull { category ->
                val raw = prefs[categoryKey(category)]
                // A malformed or legacy value (e.g. an enum constant removed in a later
                // version) must not crash the settings Flow — treat it as unset instead.
                val state = raw?.let { runCatching { FileCategoryFilterState.valueOf(it) }.getOrNull() }
                state?.let { category to it }
            }.toMap()
        )
    }

    override suspend fun setCacheWindowSize(size: Int) {
        val clamped = size.coerceIn(UserSettings.MIN_CACHE_WINDOW_SIZE, UserSettings.MAX_CACHE_WINDOW_SIZE)
        dataStore.edit { prefs -> prefs[CACHE_WINDOW_SIZE_KEY] = clamped }
    }

    override suspend fun setFileCategoryFilter(category: FileCategory, state: FileCategoryFilterState?) {
        dataStore.edit { prefs ->
            val key = categoryKey(category)
            if (state == null) {
                prefs.remove(key)
            } else {
                prefs[key] = state.name
            }
        }
    }

    companion object {
        private val CACHE_WINDOW_SIZE_KEY = intPreferencesKey("cache_window_size")
        private fun categoryKey(category: FileCategory) = stringPreferencesKey("file_category_${category.name}")
    }
}
