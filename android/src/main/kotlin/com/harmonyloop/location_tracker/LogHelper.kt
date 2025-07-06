package com.harmonyloop.location_tracker

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.io.File

object LogHelper {

    private const val TAG = "DistanceTracker"
    private const val MAX_LOGS = 1000

    private val logList = CopyOnWriteArrayList<String>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun log(message: String) {
        val timestamp = dateFormat.format(Date())
        val entry = "[$timestamp] $message"

        Log.d(TAG, entry)
        logList.add(entry)

        if (logList.size > MAX_LOGS) {
            logList.removeAt(0) // FIFO
        }
    }

    fun logError(message: String, throwable: Throwable? = null) {
        val timestamp = dateFormat.format(Date())
        val entry = "[$timestamp] ❌ ERROR: $message"

        Log.e(TAG, entry, throwable)
        logList.add(entry)

        if (logList.size > MAX_LOGS) {
            logList.removeAt(0)
        }
    }

    fun getLogs(): List<String> {
        return logList.toList()
    }

    fun clearLogs() {
        logList.clear()
    }
    fun exportLogsToFile(context: android.content.Context): String? {
        return try {
            val file = File(context.getExternalFilesDir(null), "distance_tracker_logs.txt")
            file.writeText(logList.joinToString("\n"))
            file.absolutePath
        } catch (e: Exception) {
            logError("Failed to export logs: ${e.message}")
            null
        }
    }
}
