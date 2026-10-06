package com.harmonyloop.location_tracker

import android.content.Context

/**
 * Guards against runaway restart loops by cutting off restarts if >= 3 crashes occur in 60s.
 */
object CrashProtector {
    private const val PREFS_NAME = "location_tracker_crash_guard"
    private const val KEY_CRASH_COUNT = "crash_count"
    private const val KEY_LAST_START_TIME = "last_start_time"

    fun shouldHaltAutoRestart(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_CRASH_COUNT, 0)
        val lastTime = prefs.getLong(KEY_LAST_START_TIME, 0L)
        val now = System.currentTimeMillis()

        // Reset if last crash was over a minute ago
        if (now - lastTime > 60000L) {
            prefs.edit().putInt(KEY_CRASH_COUNT, 1).putLong(KEY_LAST_START_TIME, now).apply()
            return false
        }

        val newCount = count + 1
        prefs.edit().putInt(KEY_CRASH_COUNT, newCount).putLong(KEY_LAST_START_TIME, now).apply()
        return newCount >= 3
    }

    fun markSuccessfulRun(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
