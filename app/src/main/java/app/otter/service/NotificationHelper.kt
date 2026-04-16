package app.otter.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import app.otter.R

/**
 * Helper class for creating and managing extraction notifications.
 * Extracted from ExtractionService for testability.
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
        totalCount: Int = 0
    ): Notification {
        val stopIntent = Intent(context, ExtractionService::class.java).apply {
            action = "STOP_EXTRACTION"
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = when {
            totalCount > 0 -> "Extracting: $extractedCount/$totalCount files ($progress%)"
            extractedCount > 0 -> "Extracting: $extractedCount files"
            else -> "Preparing extraction..."
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
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
            .build()
    }

    fun createSuccessNotification(
        fileName: String,
        extractedFilesCount: Int,
        outputPath: String
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
