package app.otter.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otter.domain.model.FileCategory
import app.otter.domain.model.FileCategoryFilterState
import app.otter.domain.model.UserSettings
import app.otter.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    fun setCacheWindowSize(size: Int) {
        viewModelScope.launch { settingsRepository.setCacheWindowSize(size) }
    }

    fun setFileCategoryFilter(category: FileCategory, state: FileCategoryFilterState?) {
        viewModelScope.launch { settingsRepository.setFileCategoryFilter(category, state) }
    }
}
