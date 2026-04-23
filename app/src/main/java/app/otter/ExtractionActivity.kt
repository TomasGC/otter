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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import app.otter.service.ExtractionService
import app.otter.data.util.ResourcePathConverter
import app.otter.domain.model.ResourcePath

/**
 * Main activity that handles archive extraction in auto mode.
 * Launches extraction service and finishes immediately.
 */
class ExtractionActivity : ComponentActivity() {

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startExtractionService(archivePath, fileName)
                finish()
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
            finish()
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
            finish()
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
