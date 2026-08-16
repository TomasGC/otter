package app.otter.ui.viewmodel

import app.otter.domain.model.FileCategory
import app.otter.domain.model.FileCategoryFilterState
import app.otter.domain.model.UserSettings
import app.otter.domain.repository.SettingsRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var settingsRepository: SettingsRepository
    private val settingsFlow = MutableStateFlow(UserSettings())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        settingsRepository = mockk(relaxed = true)
        every { settingsRepository.settings } returns settingsFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `settings exposes current repository value`() = runTest {
        settingsFlow.value = UserSettings(cacheWindowSize = 200)
        val viewModel = SettingsViewModel(settingsRepository)
        assertEquals(200, viewModel.settings.value.cacheWindowSize)
    }

    @Test
    fun `setCacheWindowSize delegates to repository`() = runTest {
        val viewModel = SettingsViewModel(settingsRepository)
        viewModel.setCacheWindowSize(300)
        coVerify { settingsRepository.setCacheWindowSize(300) }
    }

    @Test
    fun `setFileCategoryFilter delegates to repository`() = runTest {
        val viewModel = SettingsViewModel(settingsRepository)
        viewModel.setFileCategoryFilter(FileCategory.IMAGE, FileCategoryFilterState.INCLUDE)
        coVerify { settingsRepository.setFileCategoryFilter(FileCategory.IMAGE, FileCategoryFilterState.INCLUDE) }
    }
}
