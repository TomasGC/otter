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
import app.otter.ui.component.ExtractionScreen
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

