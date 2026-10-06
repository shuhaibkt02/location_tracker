package com.harmonyloop.location_tracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent?.action == "com.htc.intent.action.QUICKBOOT_POWERON") {
            
            LogHelper.log("BootCompletedReceiver: device boot detected")
            if (ConfigStorage.isTrackingActive(context)) {
                val config = ConfigStorage.loadConfig(context)
                if (config.autoResumeOnBoot) {
                    LogHelper.log("Auto-resuming location tracking following device boot")
                    val serviceIntent = Intent(context, DistanceTrackingService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            }
        }
    }
}
