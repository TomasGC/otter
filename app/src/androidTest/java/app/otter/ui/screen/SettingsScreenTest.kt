package app.otter.ui.screen

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otter.domain.model.FileCategory
import app.otter.domain.model.FileCategoryFilterState
import app.otter.domain.model.UserSettings
import app.otter.domain.repository.SettingsRepository
import app.otter.ui.theme.OtterTheme
import app.otter.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class FakeSettingsRepository(initial: UserSettings = UserSettings()) : SettingsRepository {
        val state = MutableStateFlow(initial)
        override val settings: Flow<UserSettings> = state
        override suspend fun setCacheWindowSize(size: Int) {
            state.value = state.value.copy(cacheWindowSize = size)
        }
        override suspend fun setFileCategoryFilter(category: FileCategory, filterState: FileCategoryFilterState?) {
            val updated = state.value.fileCategoryFilters.toMutableMap()
            if (filterState == null) updated.remove(category) else updated[category] = filterState
            state.value = state.value.copy(fileCategoryFilters = updated)
        }
    }

    private fun setScreen(
        repository: FakeSettingsRepository = FakeSettingsRepository(),
        onNavigateBack: () -> Unit = {},
    ): FakeSettingsRepository {
        composeTestRule.setContent {
            OtterTheme {
                SettingsScreen(onNavigateBack = onNavigateBack, viewModel = SettingsViewModel(repository))
            }
        }
        return repository
    }

    @Test
    fun initialState_showsCacheWindowSizeAndTitle() {
        setScreen(FakeSettingsRepository(UserSettings(cacheWindowSize = 150)))

        composeTestRule.onNodeWithText("Cache window size: 150").assertIsDisplayed()
        composeTestRule.onNodeWithText("File type filter").assertIsDisplayed()
    }

    @Test
    fun allEightCategories_areDisplayed() {
        setScreen()

        FileCategory.entries.forEach { category ->
            composeTestRule.onNodeWithContentDescription(category.name).assertIsDisplayed()
        }
    }

    @Test
    fun movingSlider_updatesRepositoryCacheWindowSize() {
        val repository = setScreen(FakeSettingsRepository(UserSettings(cacheWindowSize = 100)))

        composeTestRule.onNodeWithTag("cacheWindowSizeSlider")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(300f) }
        composeTestRule.waitForIdle()

        assertEquals(300, repository.state.value.cacheWindowSize)
    }

    @Test
    fun movingSlider_updatesDisplayedText() {
        setScreen(FakeSettingsRepository(UserSettings(cacheWindowSize = 100)))

        composeTestRule.onNodeWithTag("cacheWindowSizeSlider")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(250f) }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Cache window size: 250").assertIsDisplayed()
    }

    @Test
    fun movingSliderAboveMax_clampsToMaximum() {
        val repository = setScreen(FakeSettingsRepository(UserSettings(cacheWindowSize = 100)))

        composeTestRule.onNodeWithTag("cacheWindowSizeSlider")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(600f) }
        composeTestRule.waitForIdle()

        assertEquals(UserSettings.MAX_CACHE_WINDOW_SIZE, repository.state.value.cacheWindowSize)
    }

    @Test
    fun movingSliderBelowMin_clampsToMinimum() {
        val repository = setScreen(FakeSettingsRepository(UserSettings(cacheWindowSize = 100)))

        composeTestRule.onNodeWithTag("cacheWindowSizeSlider")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(-10f) }
        composeTestRule.waitForIdle()

        assertEquals(UserSettings.MIN_CACHE_WINDOW_SIZE, repository.state.value.cacheWindowSize)
    }

    @Test
    fun tappingCategoryRowOnce_cyclesNullToInclude() {
        val repository = setScreen()

        composeTestRule.onNodeWithContentDescription("IMAGE").performClick()
        composeTestRule.waitForIdle()

        assertEquals(FileCategoryFilterState.INCLUDE, repository.state.value.fileCategoryFilters[FileCategory.IMAGE])
    }

    @Test
    fun tappingCategoryRowTwice_cyclesIncludeToExclude() {
        val repository = setScreen()

        composeTestRule.onNodeWithContentDescription("IMAGE").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("IMAGE").performClick()
        composeTestRule.waitForIdle()

        assertEquals(FileCategoryFilterState.EXCLUDE, repository.state.value.fileCategoryFilters[FileCategory.IMAGE])
    }

    @Test
    fun tappingCategoryRowThreeTimes_cyclesBackToNull() {
        val repository = setScreen()

        repeat(3) {
            composeTestRule.onNodeWithContentDescription("IMAGE").performClick()
            composeTestRule.waitForIdle()
        }

        assertNull(repository.state.value.fileCategoryFilters[FileCategory.IMAGE])
    }

    @Test
    fun tappingDifferentCategories_areIndependent() {
        val repository = setScreen()

        composeTestRule.onNodeWithContentDescription("IMAGE").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("VIDEO").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("VIDEO").performClick()
        composeTestRule.waitForIdle()

        assertEquals(FileCategoryFilterState.INCLUDE, repository.state.value.fileCategoryFilters[FileCategory.IMAGE])
        assertEquals(FileCategoryFilterState.EXCLUDE, repository.state.value.fileCategoryFilters[FileCategory.VIDEO])
    }

    @Test
    fun backButton_invokesOnNavigateBack() {
        var backInvoked = false
        setScreen(onNavigateBack = { backInvoked = true })

        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assertTrue(backInvoked)
    }
}
