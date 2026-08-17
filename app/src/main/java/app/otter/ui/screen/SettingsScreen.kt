package app.otter.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otter.domain.model.FileCategory
import app.otter.domain.model.UserSettings
import app.otter.domain.model.next
import app.otter.ui.component.FileCategoryFilterRow
import app.otter.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).padding(16.dp)) {
            Text(
                text = "Cache window size: ${settings.cacheWindowSize}",
                style = MaterialTheme.typography.titleMedium
            )
            Slider(
                value = settings.cacheWindowSize.toFloat(),
                onValueChange = { viewModel.setCacheWindowSize(it.toInt()) },
                valueRange = UserSettings.MIN_CACHE_WINDOW_SIZE.toFloat()..UserSettings.MAX_CACHE_WINDOW_SIZE.toFloat(),
                steps = ((UserSettings.MAX_CACHE_WINDOW_SIZE - UserSettings.MIN_CACHE_WINDOW_SIZE) / 25) - 1,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("cacheWindowSizeSlider")
            )

            Text(
                text = "File type filter",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            FileCategory.entries.forEach { category ->
                val state = settings.fileCategoryFilters[category]
                FileCategoryFilterRow(
                    category = category,
                    state = state,
                    onClick = { viewModel.setFileCategoryFilter(category, state.next()) }
                )
            }
        }
    }
}
