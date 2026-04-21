package app.otter.util

import android.content.Context
import android.os.Environment
import android.util.Log
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Timber Tree that logs to a file in Downloads directory.
 * Only used in DEBUG builds.
 */
class FileLoggingTree(context: Context) : Timber.Tree() {
    private val logFile: File
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    init {
        val filenameFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.US)
        val timestamp = filenameFormat.format(Date())
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        logFile = File(downloadsDir, "otter-log-$timestamp.txt")

        try {
            logFile.writeText("=== Otter Debug Log ===\n")
            logFile.appendText("Started at ${timestampFormat.format(Date())}\n\n")
        } catch (e: Exception) {
            Log.e("FileLoggingTree", "Failed to initialize log file", e)
        }
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        try {
            val timestamp = timestampFormat.format(Date())
            val priorityStr = when (priority) {
                Log.DEBUG -> "D"
                Log.INFO -> "I"
                Log.WARN -> "W"
                Log.ERROR -> "E"
                Log.ASSERT -> "A"
                else -> "V"
            }

            val line = buildString {
                append("[$timestamp] $priorityStr/${tag ?: "?"}: $message")
                if (t != null) {
                    append("\n")
                    append(Log.getStackTraceString(t))
                }
                append("\n")
            }

            logFile.appendText(line)
        } catch (e: Exception) {
            // Silently fail - don't crash the app because of logging
            Log.e("FileLoggingTree", "Failed to write to log file", e)
        }
    }

    fun getLogPath(): String = logFile.absolutePath
}
