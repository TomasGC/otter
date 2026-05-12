package app.otter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import app.otter.service.ExtractionService
import app.otter.service.ExtractionEventBus
import app.otter.data.util.ResourcePathConverter
import app.otter.domain.model.ResourcePath
import app.otter.ui.theme.OtterTheme
import app.otter.ui.component.ExtractionProgressView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main activity that handles archive extraction in auto mode.
 * Displays extraction progress UI and launches extraction service.
 */
@AndroidEntryPoint
class ExtractionActivity : ComponentActivity() {

    @Inject
    lateinit var eventBus: ExtractionEventBus

    private companion object {
        const val REQUEST_NOTIFICATION_PERMISSION = 1001
    }

    private var pendingArchivePath: ResourcePath? = null
    private var pendingFileName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val archiveUri = intent?.data
        if (archiveUri == null) {
            Toast.makeText(this, "No archive file provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val archivePath = ResourcePathConverter.fromUri(archiveUri)
        val fileName = getFileName(archivePath) ?: "archive"

        // Display extraction UI
        setContent {
            OtterTheme {
                ExtractionScreen(
                    fileName = fileName,
                    eventBus = eventBus,
                    onComplete = { finish() }
                )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startExtractionService(archivePath, fileName)
            } else {
                pendingArchivePath = archivePath
                pendingFileName = fileName
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION
                )
            }
        } else {
            startExtractionService(archivePath, fileName)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                    this,
                    "Notification permission denied. Extraction will run without progress updates.",
                    Toast.LENGTH_LONG
                ).show()
            }

            pendingArchivePath?.let { path ->
                pendingFileName?.let { name ->
                    startExtractionService(path, name)
                }
            }
        }
    }

    private fun startExtractionService(archivePath: ResourcePath, fileName: String) {
        val serviceIntent = ExtractionService.newIntent(this, archivePath, fileName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Toast.makeText(this, "Extracting $fileName...", Toast.LENGTH_SHORT).show()
    }

    private fun getFileName(path: ResourcePath): String? {
        val uri = ResourcePathConverter.toUri(path)
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            }
        } catch (e: SecurityException) {
            // Permission denied (e.g., in tests or missing ACTION_OPEN_DOCUMENT)
            // Return null to use default filename
            null
        }
    }
}

@Composable
private fun ExtractionScreen(
    fileName: String,
    eventBus: ExtractionEventBus,
    onComplete: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var currentFile by remember { mutableStateOf("") }
    var extractedCount by remember { mutableIntStateOf(0) }
    var totalCount by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var isComplete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        eventBus.progressEvents.collect { event ->
            currentFile = event.currentFile
            extractedCount = event.extractedCount
            totalCount = event.totalCount
            progress = event.progress
        }
    }

    LaunchedEffect(Unit) {
        eventBus.completeEvents.collect {
            isComplete = true
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (isComplete) {
            // Show completion screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
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
                    text = stringResource(R.string.extraction_complete_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = stringResource(R.string.extraction_files_count, extractedCount),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                Button(onClick = onComplete) {
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
                currentFile = currentFile,
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
    }
}
