package app.otter.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import app.otter.R
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.model.ExtractionResult
import app.otter.domain.usecase.ExtractArchiveUseCase
import app.otter.util.MimeTypeUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class ExtractionService : Service() {

    @Inject
    lateinit var extractArchiveUseCase: ExtractArchiveUseCase

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle stop action
        if (intent?.action == ACTION_STOP_EXTRACTION) {
            Log.d(TAG, "Extraction cancelled by user")
            serviceScope.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val archiveUri = intent?.data
        val fileName = intent?.getStringExtra(EXTRA_FILE_NAME) ?: "archive"

        Log.d(TAG, "Service started for file: $fileName, uri: $archiveUri")

        if (archiveUri == null) {
            Log.e(TAG, "No archive URI provided")
            stopSelf()
            return START_NOT_STICKY
        }

        Log.d(TAG, "Starting foreground service with notification")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                createProgressNotification(fileName, 0),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, createProgressNotification(fileName, 0))
        }
        Log.d(TAG, "Foreground service started, notification ID: $NOTIFICATION_ID")

        serviceScope.launch {
            // Extract this archive
            extractArchive(archiveUri, fileName)

            // Process remaining archives in queue
            while (true) {
                ExtractionQueue.markComplete()
                val task = ExtractionQueue.pollNext()
                if (task == null) {
                    // Queue empty, emit complete and stop
                    Log.d(TAG, "Queue empty, stopping service")
                    ExtractionEventBus.emitComplete()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                } else {
                    // Extract next archive
                    Log.d(TAG, "Processing next archive: ${task.fileName}")
                    extractArchive(task.archiveUri, task.fileName)
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun extractArchive(archiveUri: Uri, fileName: String) {
        var extractedFilesCount = 0
        var lastError: String? = null

        try {
            val archiveFile = createArchiveFile(archiveUri, fileName)
                ?: throw IllegalStateException("Cannot create archive file")

            // Try to extract in the same folder as the archive
            val destinationFolder = getDestinationFolder(archiveUri, fileName)
            destinationFolder.mkdirs()
            Log.d(TAG, "Destination folder: ${destinationFolder.absolutePath}")

            // Log extraction details

            val destinationUri = Uri.fromFile(destinationFolder)

            Log.d(TAG, "Starting extraction to: ${destinationFolder.absolutePath}")

            extractArchiveUseCase(archiveFile, destinationUri).collect { progress ->
                when (progress) {
                    is ExtractionProgress.Extracting -> {
                        extractedFilesCount = progress.extractedCount
                        val progressPercent = (progress.progress * 100).toInt()
                        Log.d(TAG, "Extracting: ${progress.currentFile} ($progressPercent%)")

                        // Send progress via EventBus (more reliable than broadcasts)
                        serviceScope.launch {
                            ExtractionEventBus.emitProgress(
                                fileName = fileName,
                                currentFile = progress.currentFile,
                                extractedCount = progress.extractedCount,
                                totalCount = progress.totalCount,
                                progress = progress.progress
                            )
                        }

                        // Also send broadcast for backward compatibility
                        sendProgressBroadcast(
                            fileName = fileName,
                            currentFile = progress.currentFile,
                            extractedCount = progress.extractedCount,
                            totalCount = progress.totalCount,
                            progress = progress.progress
                        )

                        notificationManager.notify(
                            NOTIFICATION_ID,
                            createProgressNotification(
                                fileName = fileName,
                                progress = progressPercent,
                                currentFile = progress.currentFile,
                                extractedCount = progress.extractedCount,
                                totalCount = progress.totalCount
                            )
                        )
                    }
                    is ExtractionProgress.Success -> {
                        extractedFilesCount = progress.extractedCount
                        Log.d(TAG, "Extraction success: $extractedFilesCount files")

                        sendCompleteBroadcast()
                    }
                    is ExtractionProgress.Error -> {
                        lastError = progress.message
                        Log.e(TAG, "Extraction error: $lastError")
                        val exceptionMessage = progress.exception?.message ?: "No exception details"
                    }
                    is ExtractionProgress.Idle -> {}
                }
            }

            if (lastError != null) {
                showCompletionNotification(
                    fileName = fileName,
                    success = false,
                    message = lastError!!
                )
            } else {
                showCompletionNotification(
                    fileName = fileName,
                    success = true,
                    message = "Extracted $extractedFilesCount files",
                    destinationPath = destinationFolder
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error during extraction: ${e.message}", e)
            showCompletionNotification(
                fileName = fileName,
                success = false,
                message = e.message ?: "Unknown error"
            )
        } finally {
        }
    }

    private fun getDestinationFolder(archiveUri: Uri, fileName: String): File {
        // Try to get parent folder from URI
        val documentFile = DocumentFile.fromSingleUri(this, archiveUri)
        val parentUri = documentFile?.parentFile?.uri

        if (parentUri != null) {
            // Try to get real path from parent URI
            val parentPath = getRealPathFromUri(parentUri)
            if (parentPath != null) {
                Log.d(TAG, "Extracting to same folder as archive: $parentPath")
                return File(parentPath, fileName.substringBeforeLast("."))
            }
        }

        // Fallback: Extract to Downloads folder
        Log.d(TAG, "Could not determine archive folder, using Downloads")
        val downloadFolder = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        return File(downloadFolder, fileName.substringBeforeLast("."))
    }

    private fun getRealPathFromUri(uri: Uri): String? {
        return try {
            if (DocumentsContract.isDocumentUri(this, uri)) {
                val docId = DocumentsContract.getDocumentId(uri)

                // Primary storage
                if (uri.authority == "com.android.externalstorage.documents") {
                    val split = docId.split(":")
                    if (split.size >= 2) {
                        val type = split[0]
                        val path = split[1]

                        if ("primary".equals(type, ignoreCase = true)) {
                            return "${android.os.Environment.getExternalStorageDirectory()}/$path"
                        }
                    }
                }
            }

            // Try direct path query
            contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex("_data")
                    if (columnIndex != -1) {
                        return cursor.getString(columnIndex)
                    }
                }
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting real path from URI", e)
            null
        }
    }

    private fun createArchiveFile(uri: Uri, fileName: String): app.otter.domain.model.ArchiveFile? {
        return when (uri.scheme) {
            "file" -> {
                // Handle file:// URIs
                val file = File(uri.path ?: return null)
                if (!file.exists() || !file.isFile) {
                    return null
                }

                val archiveType = app.otter.domain.model.ArchiveType.fromFileName(fileName)
                    ?: return null

                app.otter.domain.model.ArchiveFile(
                    uri = uri,
                    name = fileName,
                    sizeBytes = file.length(),
                    mimeType = MimeTypeUtil.getMimeType(fileName),
                    type = archiveType
                )
            }
            "content" -> {
                // Handle content:// URIs
                val cursor = contentResolver.query(uri, null, null, null, null)
                if (cursor == null) {
                    return null
                }

                cursor.use {
                    if (!it.moveToFirst()) return null

                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    val size = if (sizeIndex != -1) it.getLong(sizeIndex) else 0L

                    val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                    val archiveType = app.otter.domain.model.ArchiveType.fromFileName(fileName)
                        ?: return null

                    app.otter.domain.model.ArchiveFile(
                        uri = uri,
                        name = fileName,
                        sizeBytes = size,
                        mimeType = mimeType,
                        type = archiveType
                    )
                }
            }
            else -> {
                null
            }
        }
    }


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Archive Extraction",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows progress of archive extraction"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createProgressNotification(
        fileName: String,
        progress: Int,
        currentFile: String? = null,
        extractedCount: Int = 0,
        totalCount: Int = 0
    ): Notification {
        val title = if (totalCount > 0) {
            "Extracting $fileName ($extractedCount/$totalCount)"
        } else if (extractedCount > 0) {
            "Extracting $fileName ($extractedCount files)"
        } else {
            "Extracting $fileName"
        }

        val contentText = if (currentFile != null && totalCount > 0) {
            "$currentFile ($progress%)"
        } else if (currentFile != null) {
            currentFile
        } else {
            "Preparing extraction..."
        }

        // Create stop intent
        val stopIntent = Intent(this, ExtractionService::class.java).apply {
            action = ACTION_STOP_EXTRACTION
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                android.R.drawable.ic_delete,
                "Stop",
                stopPendingIntent
            )

        // Show determinate or indeterminate progress
        if (totalCount > 0) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, true) // Indeterminate progress bar
        }

        return builder.build()
    }

    private fun showCompletionNotification(
        fileName: String,
        success: Boolean,
        message: String,
        destinationPath: File? = null
    ) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(
                if (success) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_notify_error
            )
            .setContentTitle(if (success) "Extraction complete" else "Extraction failed")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (success && destinationPath != null && destinationPath.exists()) {
            // Show full path in expanded notification
            val locationText = "Location: ${destinationPath.absolutePath}\n\nOpen your file manager to view the extracted files."
            builder.setContentText("$fileName: $message")
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$message\n\n$locationText")
            )
        } else {
            builder.setContentText("$fileName: $message")
        }

        notificationManager.notify(COMPLETION_NOTIFICATION_ID, builder.build())
    }

    private fun sendProgressBroadcast(
        fileName: String,
        currentFile: String,
        extractedCount: Int,
        totalCount: Int,
        progress: Float
    ) {
        val intent = Intent(ACTION_EXTRACTION_PROGRESS).apply {
            putExtra(EXTRA_FILE_NAME, fileName)
            putExtra(EXTRA_CURRENT_FILE, currentFile)
            putExtra(EXTRA_EXTRACTED_COUNT, extractedCount)
            putExtra(EXTRA_TOTAL_COUNT, totalCount)
            putExtra(EXTRA_PROGRESS, progress)
        }
        Log.d(TAG, "Sending broadcast: $ACTION_EXTRACTION_PROGRESS - $fileName ($extractedCount/$totalCount)")
        sendBroadcast(intent)
    }

    private fun sendCompleteBroadcast() {
        val intent = Intent(ACTION_EXTRACTION_COMPLETE)
        sendBroadcast(intent)
    }

    companion object {
        private const val TAG = "ExtractionService"
        private const val CHANNEL_ID = "extraction_channel"
        private const val NOTIFICATION_ID = 1001
        private const val COMPLETION_NOTIFICATION_ID = 1002
        private const val EXTRA_FILE_NAME = "extra_file_name"
        private const val EXTRA_CURRENT_FILE = "extra_current_file"
        private const val EXTRA_EXTRACTED_COUNT = "extra_extracted_count"
        private const val EXTRA_TOTAL_COUNT = "extra_total_count"
        private const val EXTRA_PROGRESS = "extra_progress"
        private const val ACTION_STOP_EXTRACTION = "app.otter.service.STOP_EXTRACTION"
        const val ACTION_EXTRACTION_PROGRESS = "app.otter.service.EXTRACTION_PROGRESS"
        const val ACTION_EXTRACTION_COMPLETE = "app.otter.service.EXTRACTION_COMPLETE"

        fun newIntent(context: Context, archiveUri: Uri, fileName: String): Intent {
            return Intent(context, ExtractionService::class.java).apply {
                data = archiveUri
                putExtra(EXTRA_FILE_NAME, fileName)
            }
        }

        fun newStopIntent(context: Context): Intent {
            return Intent(context, ExtractionService::class.java).apply {
                action = ACTION_STOP_EXTRACTION
            }
        }
    }
}
