package com.harmonyloop.location_tracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class MidnightStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("MidnightStopReceiver", "Midnight alarm received, stopping tracking service.")
        val stopIntent = Intent(context, DistanceTrackingService::class.java).apply {
            action = DistanceTrackingService.STOP_FROM_MIDNIGHT_ACTION
        }
        context.startService(stopIntent)
    }
} 