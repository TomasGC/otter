package app.otter.util

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLogger {
    private var logFile: File? = null
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val filenameFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.US)

    fun init(context: Context) {
        val timestamp = filenameFormat.format(Date())
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        logFile = File(downloadsDir, "otter-log-$timestamp.txt")
        logFile?.writeText("=== Otter Debug Log ===\n")
        logFile?.appendText("Started at ${timestampFormat.format(Date())}\n\n")
    }

    fun log(tag: String, message: String) {
        val timestamp = timestampFormat.format(Date())
        val line = "[$timestamp] $tag: $message\n"
        Log.d(tag, message)
        try {
            logFile?.appendText(line)
        } catch (e: Exception) {
            Log.e("FileLogger", "Failed to write to log file", e)
        }
    }

    fun getLogPath(): String? = logFile?.absolutePath
}
