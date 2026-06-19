package app.otter.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.otter.R
import kotlinx.coroutines.launch

/**
 * Reusable extraction progress view.
 * Displays circular progress indicator, percentage, file count, and list of recent files.
 *
 * @param fileName Name of the archive being extracted
 * @param progress Progress value (0.0 to 1.0)
 * @param extractedCount Number of files extracted so far
 * @param totalCount Total number of files to extract
 * @param currentFile Current file being extracted (for backward compatibility)
 * @param recentFiles List of recently extracted files (last 3)
 * @param onStop Callback when Stop button is clicked
 * @param onBackground Callback when Background button is clicked
 */
@Composable
fun ExtractionProgressView(
    fileName: String,
    progress: Float,
    extractedCount: Int,
    totalCount: Int,
    currentFile: String,
    recentFiles: List<String> = emptyList(),
    onStop: () -> Unit,
    onBackground: () -> Unit
) {
    // Use Animatable for smooth transitions
    val animatedProgress = remember { Animatable(0f) }
    val animatedExtractedCount = remember { Animatable(0f) }
    val animatedPercentage = remember { Animatable(0f) }

    val scope = rememberCoroutineScope()

    // Animate to new values when they change
    LaunchedEffect(progress) {
        scope.launch {
            animatedProgress.animateTo(
                targetValue = progress,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            )
        }
    }

    LaunchedEffect(extractedCount) {
        scope.launch {
            animatedExtractedCount.animateTo(
                targetValue = extractedCount.toFloat(),
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            )
        }
    }

    LaunchedEffect(progress) {
        scope.launch {
            animatedPercentage.animateTo(
                targetValue = (progress * 100),
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Top section: Title
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.extraction_title, fileName),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 64.dp)
            )

            LinearProgressIndicator(
                progress = animatedProgress.value,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(bottom = 24.dp),
            )

            // Combined progress info on single line: "45 / 100 files 75%"
            if (totalCount > 0) {
                Text(
                    text = "${animatedExtractedCount.value.toInt()} / $totalCount files ${animatedPercentage.value.toInt()}%",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            } else {
                Text(
                    text = "${animatedPercentage.value.toInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }

        // Middle section: Recent files list with animated entries
        if (recentFiles.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .align(Alignment.Center)
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                val filesToShow = recentFiles.takeLast(5)
                filesToShow.forEachIndexed { index, file ->
                    val isLast = index == filesToShow.size - 1
                    val prefix = if (isLast) "→" else "✓"

                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(300)) +
                                slideInVertically(animationSpec = tween(300)) { -it },
                        exit = fadeOut(animationSpec = tween(300)) +
                                slideOutVertically(animationSpec = tween(300)) { -it }
                    ) {
                        Text(
                            text = "$prefix $file",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        } else if (currentFile.isNotEmpty()) {
            // Fallback to single file display (backward compatibility)
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.Start
            ) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(300))
                ) {
                    Text(
                        text = currentFile,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Bottom section: Buttons fixed at bottom right
        Row(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .align(Alignment.BottomEnd),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onStop) {
                Text(stringResource(R.string.extraction_button_stop))
            }

            Spacer(modifier = Modifier.width(8.dp))

            TextButton(onClick = onBackground) {
                Text(stringResource(R.string.extraction_button_background))
            }
        }
    }
}
