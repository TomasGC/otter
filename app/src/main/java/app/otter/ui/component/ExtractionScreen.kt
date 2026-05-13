package app.otter.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
        eventBus.progressEvents.collect { event ->
            currentFile = event.currentFile
            extractedCount = event.extractedCount
            totalCount = event.totalCount
            progress = event.progress
            recentFiles = event.recentFiles
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
