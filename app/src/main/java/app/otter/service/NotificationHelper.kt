package app.otter.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import app.otter.R

/**
 * Helper class for creating and managing extraction notifications.
 * Extracted from ExtractionService for testability.
 * Uses custom layout to display file list (Samsung My Files style).
 */
class NotificationHelper(
    private val context: Context,
    private val notificationManager: NotificationManager
) {

    companion object {
        const val CHANNEL_ID = "extraction_channel"
        const val CHANNEL_NAME = "Archive Extraction"
        const val NOTIFICATION_ID = 1
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of archive extraction"
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun createProgressNotification(
        fileName: String,
        progress: Int,
        extractedCount: Int = 0,
        totalCount: Int = 0,
        recentFiles: List<String> = emptyList()
    ): Notification {
        // Create stop intent
        val stopIntent = Intent(context, ExtractionService::class.java).apply {
            action = "STOP_EXTRACTION"
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build content text with file counter
        val contentText = if (totalCount > 0) {
            "$extractedCount/$totalCount files ($progress%)"
        } else if (extractedCount > 0) {
            "$extractedCount files"
        } else {
            "Preparing extraction..."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Extracting $fileName")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_delete,
                "Stop",
                stopPendingIntent
            )

        // Add InboxStyle with recent files if available
        if (recentFiles.isNotEmpty()) {
            val inboxStyle = NotificationCompat.InboxStyle()

            // Add recent files (last 3)
            val filesToShow = recentFiles.takeLast(3)
            filesToShow.forEachIndexed { index, file ->
                val isLast = index == filesToShow.size - 1
                val prefix = if (isLast) "→" else "✓"
                inboxStyle.addLine("$prefix $file")
            }

            // Summary line
            val summaryText = if (totalCount > 0) {
                "$extractedCount/$totalCount files"
            } else {
                "$extractedCount files"
            }
            inboxStyle.setSummaryText(summaryText)

            builder.setStyle(inboxStyle)
        }

        return builder.build()
    }

    fun createSuccessNotification(
        fileName: String,
        extractedFilesCount: Int,
    ): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Extraction Complete")
            .setContentText("$extractedFilesCount files extracted from $fileName")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()
    }

    fun createFailureNotification(
        fileName: String,
        errorMessage: String
    ): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Extraction Failed")
            .setContentText("Failed to extract $fileName: $errorMessage")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()
    }

    fun updateNotification(notification: Notification) {
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
