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
    private val recentFilesBuffer = RecentFilesBuffer(maxSize = 5)

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
        val selectedItems = intent?.getStringArrayListExtra(EXTRA_SELECTED_ITEMS)
        Timber.tag(TAG).d("Service started for file: $fileName, uri: $archiveUriRaw, selected: ${selectedItems?.size ?: "all"}")

        if (archiveUriRaw == null) {
            Timber.tag(TAG).e("No archive URI provided")
            stopSelf()
            return START_NOT_STICKY
        }

        val archiveUri = ResourcePathConverter.fromUri(archiveUriRaw, this)
        startForegroundWithNotification(fileName)
        launchExtractionWorkflow(archiveUri, fileName, selectedItems)

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

    private fun launchExtractionWorkflow(
        archiveUri: ResourcePath,
        fileName: String,
        selectedItems: List<String>? = null
    ) {
        recentFilesBuffer.clear()

        serviceScope.launch {
            extractArchive(archiveUri, fileName, selectedItems)
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
                // Reset event bus to prevent stale events from being replayed
                // to new subscribers (e.g., when opening browser after extraction)
                eventBus.reset()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                break
            } else {
                Timber.tag(TAG).d("Processing next archive: ${task.fileName}")
                extractArchive(task.archiveUri, task.fileName, task.selectedItems)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun notifyExtractionProgress(fileName: String, progress: ExtractionProgress.Extracting) {
        val progressPercent = (progress.progress * 100).toInt()
        Timber.tag(TAG).d("Extracting: ${progress.currentFile} ($progressPercent%)")
        recentFilesBuffer.add(progress.currentFile)
        emitProgressEvent(fileName, progress)
        notificationManager.notify(
            NOTIFICATION_ID,
            createProgressNotification(
                fileName = fileName,
                progress = progressPercent,
                extractedCount = progress.extractedCount,
                totalCount = progress.totalCount,
                recentFiles = recentFilesBuffer.getFiles()
            )
        )
    }

    private fun showExtractionResult(fileName: String, lastError: String?, count: Int, folder: File) {
        if (lastError != null) {
            showCompletionNotification(fileName = fileName, success = false, message = lastError)
        } else {
            showCompletionNotification(
                fileName = fileName, success = true,
                message = "Extracted $count files", destinationPath = folder
            )
        }
    }

    private suspend fun extractArchive(
        archiveUri: ResourcePath,
        fileName: String,
        selectedItems: List<String>? = null
    ) {
        var extractedFilesCount = 0
        var lastError: String? = null
        var fileLoggingTree: app.otter.util.FileLoggingTree? = null

        try {
            val archiveFile = archiveFileFactory.createFromPath(archiveUri, fileName)
                ?: error("Cannot create archive file")

            val destinationFolder = getDestinationFolder(archiveUri, fileName)
            destinationFolder.mkdirs()

            if (BuildConfig.DEBUG) {
                fileLoggingTree = app.otter.util.FileLoggingTree(this, destinationFolder)
                Timber.plant(fileLoggingTree)
                Timber.tag(TAG).d("File logging enabled at: ${fileLoggingTree.getLogPath()}")
            }

            Timber.tag(TAG).d("Destination folder: ${destinationFolder.absolutePath}")
            val destinationPath = ResourcePathConverter.fromUri(Uri.fromFile(destinationFolder))
            Timber.tag(TAG).d("Starting extraction to: ${destinationFolder.absolutePath}")

            extractArchiveUseCase(archiveFile, destinationPath, selectedItems).collect { progress ->
                when (progress) {
                    is ExtractionProgress.Extracting -> {
                        extractedFilesCount = progress.extractedCount
                        notifyExtractionProgress(fileName, progress)
                    }
                    is ExtractionProgress.Success -> {
                        extractedFilesCount = progress.extractedCount
                        Timber.tag(TAG).d("Extraction success: $extractedFilesCount files")
                    }
                    is ExtractionProgress.Error -> {
                        lastError = progress.message
                        Timber.tag(TAG).e("Extraction error: $lastError")
                    }
                    is ExtractionProgress.Idle -> {}
                }
            }

            showExtractionResult(fileName, lastError, extractedFilesCount, destinationFolder)
        } catch (e: kotlinx.coroutines.CancellationException) {
            // User-initiated stop: no notification at all, not even a "failed" one.
            Timber.tag(TAG).d("Extraction cancelled: $fileName")
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Fatal error during extraction: ${e.message}")
            showCompletionNotification(fileName = fileName, success = false, message = e.message ?: "Unknown error")
        } finally {
            if (fileLoggingTree != null) Timber.uproot(fileLoggingTree)
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
        eventBus.emitProgress(
            fileName = fileName,
            currentFile = progress.currentFile,
            extractedCount = progress.extractedCount,
            totalCount = progress.totalCount,
            progress = progress.progress,
            recentFiles = recentFilesBuffer.getFiles()
        )

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
        extractedCount: Int = 0,
        totalCount: Int = 0,
        recentFiles: List<String> = emptyList()
    ): Notification {
        val helper = NotificationHelper(this, notificationManager)
        return helper.createProgressNotification(
            fileName = fileName,
            progress = progress,
            extractedCount = extractedCount,
            totalCount = totalCount,
            recentFiles = recentFiles
        )
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
        private const val EXTRA_SELECTED_ITEMS = "extra_selected_items"
        private const val ACTION_STOP_EXTRACTION = "app.otter.service.STOP_EXTRACTION"
        const val ACTION_EXTRACTION_PROGRESS = "app.otter.service.EXTRACTION_PROGRESS"
        const val ACTION_EXTRACTION_COMPLETE = "app.otter.service.EXTRACTION_COMPLETE"

        fun newIntent(
            context: Context,
            archiveUri: ResourcePath,
            fileName: String,
            selectedItems: List<String>? = null
        ): Intent {
            return Intent(context, ExtractionService::class.java).apply {
                data = ResourcePathConverter.toUri(archiveUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // For Samsung content:// URIs
                putExtra(EXTRA_FILE_NAME, fileName)
                if (selectedItems != null) {
                    putStringArrayListExtra(EXTRA_SELECTED_ITEMS, ArrayList(selectedItems))
                }
            }
        }

        fun newStopIntent(context: Context): Intent {
            return Intent(context, ExtractionService::class.java).apply {
                action = ACTION_STOP_EXTRACTION
            }
        }
    }
}
