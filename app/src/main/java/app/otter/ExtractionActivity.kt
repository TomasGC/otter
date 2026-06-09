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

        val archivePath = ResourcePathConverter.fromUri(archiveUri, this)
        // Get filename from the ORIGINAL intent URI (before any path resolution)
        val fileName = getFileNameFromUri(archiveUri) ?: getFileName(archivePath) ?: "archive"

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
        val archiveUri = ResourcePathConverter.toUri(archivePath)

        // For content:// URIs, resolve to real file path first (so service can access without permissions)
        val resolvedPath = if (archiveUri.scheme == "content") {
            val realPath = app.otter.data.util.ResourcePathConverter.getRealPathFromContentUri(this, archiveUri)
            if (realPath != null) {
                app.otter.domain.model.ResourcePath.FileSystem(realPath)
            } else {
                // Can't resolve to file path — copy to cache dir so service can read it
                copyContentUriToCacheFile(archiveUri, fileName) ?: archivePath
            }
        } else {
            archivePath
        }

        val serviceIntent = ExtractionService.newIntent(this, resolvedPath, fileName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Toast.makeText(this, "Extracting $fileName...", Toast.LENGTH_SHORT).show()
    }

    private fun copyContentUriToCacheFile(uri: android.net.Uri, fileName: String): ResourcePath? {
        return try {
            val cacheFile = java.io.File(cacheDir, "extraction_$fileName")
            contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (cacheFile.exists() && cacheFile.length() > 0) {
                app.otter.domain.model.ResourcePath.FileSystem(cacheFile.absolutePath)
            } else null
        } catch (e: Exception) {
            android.util.Log.e("ExtractionActivity", "Failed to copy content URI to cache: ${e.message}")
            null
        }
    }

    private fun getFileNameFromUri(uri: android.net.Uri): String? {
        return try {
            when (uri.scheme) {
                "content" -> {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
                    }
                }
                "file" -> uri.lastPathSegment
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getFileName(path: ResourcePath): String? {
        // Try to extract filename from the resolved FileSystem path
        if (path is ResourcePath.FileSystem) {
            val pathStr = path.path
            if (!pathStr.startsWith("content://")) {
                return java.io.File(pathStr).name.takeIf { it.isNotBlank() }
            }
        }
        val uri = ResourcePathConverter.toUri(path)
        return getFileNameFromUri(uri)
    }
}

