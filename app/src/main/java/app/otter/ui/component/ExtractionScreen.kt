package app.otter.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateValue
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.otter.R
import app.otter.service.ExtractionEventBus
import app.otter.service.ExtractionService

/**
 * Shared extraction screen used by both ExtractionActivity and FileBrowserScreen.
 * Displays extraction progress and completion screen.
 *
 * @param fileName Name of the archive being extracted
 * @param eventBus Event bus for extraction progress updates
 * @param onComplete Callback when extraction is complete and user closes the screen
 */
@Composable
fun ExtractionScreen(
    fileName: String,
    eventBus: ExtractionEventBus,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    var currentFile by remember { mutableStateOf("") }
    var extractedCount by remember { mutableIntStateOf(0) }
    var totalCount by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var recentFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var isComplete by remember { mutableStateOf(false) }

    // Animated loading dots using infinite transition
    val infiniteTransition = rememberInfiniteTransition(label = "startingDots")
    val dotCount by infiniteTransition.animateValue(
        initialValue = 0,
        targetValue = 4,
        typeConverter = Int.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dotCount"
    )

    LaunchedEffect(Unit) {
        eventBus.progressState.collect { event ->
            event?.let {
                currentFile = it.currentFile
                extractedCount = it.extractedCount
                totalCount = it.totalCount
                progress = it.progress
                recentFiles = it.recentFiles
            }
        }
    }

    LaunchedEffect(Unit) {
        eventBus.completeEvents.collect {
            isComplete = true
        }
    }

    // Display starting animation or actual file
    val displayFile = if (currentFile.isEmpty()) {
        "Starting" + ".".repeat(dotCount.coerceAtMost(3))
    } else {
        currentFile
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isComplete) {
                // Show completion screen with absolute positioning
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    // Title at top center
                    Text(
                        text = stringResource(R.string.extraction_complete_title),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )

                    // Icon and file count at center
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Complete",
                            modifier = Modifier
                                .size(120.dp)
                                .padding(bottom = 24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = stringResource(R.string.extraction_files_count, extractedCount),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Close button at bottom left
                    Button(
                        onClick = onComplete,
                        modifier = Modifier.align(Alignment.BottomStart)
                    ) {
                        Text(stringResource(R.string.extraction_button_close))
                    }
                }
            } else {
                // Show extraction progress
                ExtractionProgressView(
                    fileName = fileName,
                    progress = progress,
                    extractedCount = extractedCount,
                    totalCount = totalCount,
                    currentFile = displayFile,
                    recentFiles = recentFiles,
                    onStop = {
                        val stopIntent = ExtractionService.newStopIntent(context)
                        context.startService(stopIntent)
                        onComplete()
                    },
                    onBackground = {
                        onComplete()
                    }
                )
            }

            // Version label in bottom-left corner
            VersionLabel(
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
    }
}
