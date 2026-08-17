package app.otter.integration.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.otter.data.repository.DataStoreSettingsRepository
import app.otter.domain.model.FileCategory
import app.otter.domain.model.FileCategoryFilterState
import app.otter.domain.model.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreSettingsRepositoryMockIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createDataStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(UnconfinedTestDispatcher()),
        produceFile = { tempFolder.newFile("test_settings.preferences_pb") }
    )

    private fun createRepository(): DataStoreSettingsRepository =
        DataStoreSettingsRepository(createDataStore())

    @Test
    fun `settings defaults to UserSettings default values when nothing persisted`() = runTest {
        val repository = createRepository()
        assertEquals(UserSettings(), repository.settings.first())
    }

    @Test
    fun `setCacheWindowSize persists and is readable back`() = runTest {
        val repository = createRepository()
        repository.setCacheWindowSize(250)
        assertEquals(250, repository.settings.first().cacheWindowSize)
    }

    @Test
    fun `setFileCategoryFilter persists INCLUDE state`() = runTest {
        val repository = createRepository()
        repository.setFileCategoryFilter(FileCategory.IMAGE, FileCategoryFilterState.INCLUDE)
        val settings = repository.settings.first()
        assertEquals(FileCategoryFilterState.INCLUDE, settings.fileCategoryFilters[FileCategory.IMAGE])
    }

    @Test
    fun `setFileCategoryFilter with null removes the category`() = runTest {
        val repository = createRepository()
        repository.setFileCategoryFilter(FileCategory.IMAGE, FileCategoryFilterState.INCLUDE)
        repository.setFileCategoryFilter(FileCategory.IMAGE, null)
        val settings = repository.settings.first()
        assertEquals(null, settings.fileCategoryFilters[FileCategory.IMAGE])
    }

    @Test
    fun `multiple categories persist independently`() = runTest {
        val repository = createRepository()
        repository.setFileCategoryFilter(FileCategory.IMAGE, FileCategoryFilterState.INCLUDE)
        repository.setFileCategoryFilter(FileCategory.VIDEO, FileCategoryFilterState.EXCLUDE)
        val settings = repository.settings.first()
        assertEquals(FileCategoryFilterState.INCLUDE, settings.fileCategoryFilters[FileCategory.IMAGE])
        assertEquals(FileCategoryFilterState.EXCLUDE, settings.fileCategoryFilters[FileCategory.VIDEO])
    }

    @Test
    fun `malformed persisted category value does not crash settings flow`() = runTest {
        val dataStore = createDataStore()
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("file_category_IMAGE")] = "NOT_A_REAL_STATE"
        }
        val repository = DataStoreSettingsRepository(dataStore)

        val settings = repository.settings.first()

        assertNull("Malformed value must be treated as unset, not crash", settings.fileCategoryFilters[FileCategory.IMAGE])
    }

    @Test
    fun `legacy enum value removed in a later version does not crash settings flow`() = runTest {
        val dataStore = createDataStore()
        // Simulates a category filter state that existed in an older app version and was
        // later removed from the FileCategoryFilterState enum.
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("file_category_ARCHIVE")] = "SOME_REMOVED_STATE"
        }
        val repository = DataStoreSettingsRepository(dataStore)

        val settings = repository.settings.first()

        assertNull(settings.fileCategoryFilters[FileCategory.ARCHIVE])
    }

    @Test
    fun `setCacheWindowSize clamps values below the minimum`() = runTest {
        val repository = createRepository()
        repository.setCacheWindowSize(-50)
        assertEquals(UserSettings.MIN_CACHE_WINDOW_SIZE, repository.settings.first().cacheWindowSize)
    }

    @Test
    fun `setCacheWindowSize clamps values above the maximum`() = runTest {
        val repository = createRepository()
        repository.setCacheWindowSize(999_999)
        assertEquals(UserSettings.MAX_CACHE_WINDOW_SIZE, repository.settings.first().cacheWindowSize)
    }

    @Test
    fun `setCacheWindowSize of zero clamps to the minimum`() = runTest {
        val repository = createRepository()
        repository.setCacheWindowSize(0)
        assertEquals(UserSettings.MIN_CACHE_WINDOW_SIZE, repository.settings.first().cacheWindowSize)
    }

    @Test
    fun `out-of-range persisted cache window size is clamped on read`() = runTest {
        val dataStore = createDataStore()
        // Simulates a value written before bounds enforcement existed, or corrupted externally.
        dataStore.edit { prefs -> prefs[intPreferencesKey("cache_window_size")] = -999 }
        val repository = DataStoreSettingsRepository(dataStore)

        assertEquals(UserSettings.MIN_CACHE_WINDOW_SIZE, repository.settings.first().cacheWindowSize)
    }

    @Test
    fun `concurrent setCacheWindowSize calls settle to a consistent final value`() = runTest {
        val repository = createRepository()

        // Simulates rapid slider drag events firing many onValueChange calls back to back.
        val jobs = (1..20).map { value ->
            async { repository.setCacheWindowSize(value * 10) }
        }
        jobs.forEach { it.await() }

        val finalValue = repository.settings.first().cacheWindowSize
        // DataStore serializes edit{} internally — the final value must be one of the values
        // actually written (not corrupted/interleaved), and within the valid clamped range.
        assertTrue(
            "Final value $finalValue must be one of the written values",
            (1..20).map { it * 10 }.contains(finalValue)
        )
    }

    @Test
    fun `concurrent setFileCategoryFilter calls for different categories all persist`() = runTest {
        val repository = createRepository()

        val jobs = FileCategory.entries.map { category ->
            async {
                repository.setFileCategoryFilter(category, FileCategoryFilterState.INCLUDE)
            }
        }
        jobs.forEach { it.await() }

        val settings = repository.settings.first()
        FileCategory.entries.forEach { category ->
            assertEquals(
                "Category $category must be persisted despite concurrent writes",
                FileCategoryFilterState.INCLUDE,
                settings.fileCategoryFilters[category]
            )
        }
    }

    @Test
    fun `setCacheWindowSize with the same value twice is idempotent`() = runTest {
        val repository = createRepository()
        repository.setCacheWindowSize(200)
        repository.setCacheWindowSize(200)
        assertEquals(200, repository.settings.first().cacheWindowSize)
    }

    @Test
    fun `setFileCategoryFilter with the same state twice is idempotent`() = runTest {
        val repository = createRepository()
        repository.setFileCategoryFilter(FileCategory.IMAGE, FileCategoryFilterState.EXCLUDE)
        repository.setFileCategoryFilter(FileCategory.IMAGE, FileCategoryFilterState.EXCLUDE)
        assertEquals(
            FileCategoryFilterState.EXCLUDE,
            repository.settings.first().fileCategoryFilters[FileCategory.IMAGE]
        )
    }
}
