package app.otter.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.otter.domain.model.FileCategory
import app.otter.domain.model.FileCategoryFilterState

/**
 * Tri-state row: tapping cycles the caller-supplied state via [app.otter.domain.model.next].
 * Empty box = neutral, green check = include, red minus = exclude.
 */
@Composable
fun FileCategoryFilterRow(
    category: FileCategory,
    state: FileCategoryFilterState?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (icon, tint) = when (state) {
            FileCategoryFilterState.INCLUDE -> Icons.Default.Check to Color(0xFF4CAF50)
            FileCategoryFilterState.EXCLUDE -> Icons.Default.Remove to Color(0xFFF44336)
            null -> Icons.Default.CheckBoxOutlineBlank to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Icon(
            imageVector = icon,
            contentDescription = category.name,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = category.name.lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
