package app.otter.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.otter.domain.model.FileCategory
import app.otter.domain.model.FileCategoryFilterState
import app.otter.domain.model.next

/**
 * Filter popup reached from the browsing top bar. Staged locally; committed to [onDismiss]
 * only when dismissed (click outside / back) — no separate apply button.
 */
@Composable
fun FileTypeFilterPopup(
    expanded: Boolean,
    currentFilters: Map<FileCategory, FileCategoryFilterState>,
    defaultFilters: Map<FileCategory, FileCategoryFilterState>,
    onDismiss: (Map<FileCategory, FileCategoryFilterState>) -> Unit,
) {
    var staged by remember(expanded) { mutableStateOf(currentFilters) }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { onDismiss(staged) }
    ) {
        FileCategory.entries.forEach { category ->
            FileCategoryFilterRow(
                category = category,
                state = staged[category],
                onClick = {
                    val next = staged[category].next()
                    staged = if (next == null) {
                        staged - category
                    } else {
                        staged + (category to next)
                    }
                }
            )
        }
        HorizontalDivider()
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            TextButton(onClick = { staged = defaultFilters }) {
                Text("Reset")
            }
            TextButton(onClick = { staged = emptyMap() }) {
                Text("Clear")
            }
        }
    }
}
