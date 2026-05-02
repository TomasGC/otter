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
import timber.log.Timber
import androidx.core.app.NotificationCompat
import app.otter.BuildConfig
import app.otter.R
import app.otter.domain.model.ExtractionProgress
import app.otter.domain.usecase.ExtractArchiveUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import app.otter.data.util.ResourcePathConverter
import app.otter.domain.model.ResourcePath

@AndroidEntryPoint
class ExtractionService : Service() {

    @Inject
    lateinit var extractArchiveUseCase: ExtractArchiveUseCase

    @Inject
    lateinit var eventBus: ExtractionEventBus

    @Inject
    lateinit var extractionQueue: ExtractionQueue

    @Inject
    lateinit var archiveFileFactory: app.otter.util.ArchiveFileFactory

    @Inject
    lateinit var destinationResolver: app.otter.util.ExtractionDestinationResolver

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (handleStopAction(intent)) {
            return START_NOT_STICKY
        }

        val archiveUriRaw = intent?.data
        val fileName = intent?.getStringExtra(EXTRA_FILE_NAME) ?: "archive"
        Timber.tag(TAG).d("Service started for file: $fileName, uri: $archiveUriRaw")

        if (archiveUriRaw == null) {
            Timber.tag(TAG).e("No archive URI provided")
            stopSelf()
            return START_NOT_STICKY
        }

        val archiveUri = ResourcePathConverter.fromUri(archiveUriRaw)
        startForegroundWithNotification(fileName)
        launchExtractionWorkflow(archiveUri, fileName)

        return START_NOT_STICKY
    }

    private fun handleStopAction(intent: Intent?): Boolean {
        if (intent?.action == ACTION_STOP_EXTRACTION) {
            Timber.tag(TAG).d("Extraction cancelled by user")
            serviceScope.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return true
        }
        return false
    }

    private fun startForegroundWithNotification(fileName: String) {
        Timber.tag(TAG).d("Starting foreground service with notification")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                createProgressNotification(fileName, 0),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, createProgressNotification(fileName, 0))
        }
        Timber.tag(TAG).d("Foreground service started, notification ID: $NOTIFICATION_ID")
    }

    private fun launchExtractionWorkflow(archiveUri: ResourcePath, fileName: String) {
        serviceScope.launch {
            extractArchive(archiveUri, fileName)
            processQueuedExtractions()
        }
    }

    private suspend fun processQueuedExtractions() {
        while (true) {
            extractionQueue.markComplete()
            val task = extractionQueue.pollNext()
            if (task == null) {
                Timber.tag(TAG).d("Queue empty, stopping service")
                eventBus.emitComplete()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                break
            } else {
                Timber.tag(TAG).d("Processing next archive: ${task.fileName}")
                extractArchive(task.archiveUri, task.fileName)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun extractArchive(archiveUri: ResourcePath, fileName: String) {
        var extractedFilesCount = 0
        var lastError: String? = null
        var fileLoggingTree: app.otter.util.FileLoggingTree? = null

        try {
            val archiveFile = archiveFileFactory.createFromPath(archiveUri, fileName)
                ?: throw IllegalStateException("Cannot create archive file")

            // Try to extract in the same folder as the archive
            val destinationFolder = getDestinationFolder(archiveUri, fileName)
            destinationFolder.mkdirs()
            Timber.tag(TAG).d("Destination folder: ${destinationFolder.absolutePath}")

            // Add file logging tree for this extraction (debug builds only)
            if (BuildConfig.DEBUG) {
                fileLoggingTree = app.otter.util.FileLoggingTree(this, destinationFolder)
                Timber.plant(fileLoggingTree)
                Timber.tag(TAG).d("File logging enabled at: ${fileLoggingTree.getLogPath()}")
            }

            val destinationPath = ResourcePathConverter.fromUri(Uri.fromFile(destinationFolder))

            Timber.tag(TAG).d("Starting extraction to: ${destinationFolder.absolutePath}")

            extractArchiveUseCase(archiveFile, destinationPath).collect { progress ->
                when (progress) {
                    is ExtractionProgress.Extracting -> {
                        extractedFilesCount = progress.extractedCount
                        val progressPercent = (progress.progress * 100).toInt()
                        Timber.tag(TAG).d("Extracting: ${progress.currentFile} ($progressPercent%)")

                        emitProgressEvent(fileName, progress)

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
                        Timber.tag(TAG).d("Extraction success: $extractedFilesCount files")

                        // Emit complete event for this file (for UI refresh)
                        serviceScope.launch {
                            eventBus.emitComplete()
                        }

                        sendCompleteBroadcast()
                    }
                    is ExtractionProgress.Error -> {
                        lastError = progress.message
                        Timber.tag(TAG).e("Extraction error: $lastError")
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
            Timber.tag(TAG).e(e, "Fatal error during extraction: ${e.message}")
            showCompletionNotification(
                fileName = fileName,
                success = false,
                message = e.message ?: "Unknown error"
            )
        } finally {
            // Remove file logging tree for this extraction
            if (fileLoggingTree != null) {
                Timber.uproot(fileLoggingTree)
            }
        }
    }

    private fun getDestinationFolder(archivePath: ResourcePath, fileName: String): File {
        val archiveUri = ResourcePathConverter.toUri(archivePath)
        return destinationResolver.resolveDestination(archiveUri, fileName)
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

    private fun emitProgressEvent(fileName: String, progress: ExtractionProgress.Extracting) {
        // Send progress via EventBus (more reliable than broadcasts)
        serviceScope.launch {
            eventBus.emitProgress(
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
            setPackage(packageName)
            putExtra(EXTRA_FILE_NAME, fileName)
            putExtra(EXTRA_CURRENT_FILE, currentFile)
            putExtra(EXTRA_EXTRACTED_COUNT, extractedCount)
            putExtra(EXTRA_TOTAL_COUNT, totalCount)
            putExtra(EXTRA_PROGRESS, progress)
        }
        Timber.tag(TAG).d("Sending broadcast: $ACTION_EXTRACTION_PROGRESS - $fileName ($extractedCount/$totalCount)")
        sendBroadcast(intent)
    }

    private fun sendCompleteBroadcast() {
        val intent = Intent(ACTION_EXTRACTION_COMPLETE).apply {
            setPackage(packageName)
        }
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

        fun newIntent(context: Context, archiveUri: ResourcePath, fileName: String): Intent {
            return Intent(context, ExtractionService::class.java).apply {
                data = ResourcePathConverter.toUri(archiveUri)
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
