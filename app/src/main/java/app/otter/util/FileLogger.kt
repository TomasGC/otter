package app.otter.util

import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLogger {
    private const val TAG = "FileLogger"
    private var logFile: File? = null
    private var writer: PrintWriter? = null

    fun initialize(destinationFolder: File, archiveName: String) {
        try {
            val logFileName = "${archiveName.substringBeforeLast(".")}_extraction.txt"
            logFile = File(destinationFolder.parentFile, logFileName)

            writer = PrintWriter(FileWriter(logFile, true), true)
            log("FileLogger initialized: ${logFile?.absolutePath}")
            Log.i(TAG, "Log file created: ${logFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize file logger", e)
        }
    }

    fun log(message: String, tag: String = "Otter") {
        try {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            val logMessage = "$timestamp [$tag] $message"

            // Write to file
            writer?.println(logMessage)

            // Also log to Logcat
            Log.d(tag, message)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log", e)
        }
    }

    fun logError(message: String, throwable: Throwable? = null, tag: String = "Otter") {
        try {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            writer?.println("$timestamp [$tag] ERROR: $message")

            if (throwable != null) {
                writer?.println("Exception: ${throwable.message}")
                throwable.printStackTrace(writer)
            }

            Log.e(tag, message, throwable)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write error log", e)
        }
    }

    fun close() {
        try {
            writer?.flush()
            writer?.close()
            writer = null
            log("FileLogger closed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close file logger", e)
        }
    }

    fun getLogFilePath(): String? = logFile?.absolutePath
}
