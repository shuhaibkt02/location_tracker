package com.harmonyloop.location_tracker

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object OemBatteryHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else {
            true
        }
    }

    fun requestIgnoreBatteryOptimizations(context: Context, activity: Activity?): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    if (activity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val launcher = activity ?: context
                launcher.startActivity(intent)
                return true
            } catch (e: Exception) {
                LogHelper.logError("Failed to launch ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS: ${e.message}")
                try {
                    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        if (activity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val launcher = activity ?: context
                    launcher.startActivity(fallback)
                    return true
                } catch (e2: Exception) {
                    LogHelper.logError("Failed fallback to ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS: ${e2.message}")
                    return openAppDetails(context, activity)
                }
            }
        }
        return true
    }

    fun openOemBatterySettings(context: Context, activity: Activity?): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val intentList = getOemIntentCandidates(manufacturer)

        for (intent in intentList) {
            try {
                if (intent.resolveActivity(context.packageManager) != null) {
                    if (activity == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    val launcher = activity ?: context
                    launcher.startActivity(intent)
                    LogHelper.log("Successfully opened OEM settings for $manufacturer using ${intent.component}")
                    return true
                }
            } catch (e: Exception) {
                LogHelper.logError("Failed attempting OEM intent: ${e.message}")
            }
        }

        LogHelper.log("No OEM-specific intent resolved for $manufacturer. Falling back to App Details.")
        return openAppDetails(context, activity)
    }

    private fun openAppDetails(context: Context, activity: Activity?): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                if (activity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val launcher = activity ?: context
            launcher.startActivity(intent)
            true
        } catch (e: Exception) {
            LogHelper.logError("Failed to open application details settings: ${e.message}")
            false
        }
    }

    private fun getOemIntentCandidates(manufacturer: String): List<Intent> {
        val list = mutableListOf<Intent>()

        when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
                list.add(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")))
                list.add(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")))
                list.add(Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT))
            }
            manufacturer.contains("samsung") -> {
                list.add(Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")))
                list.add(Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity")))
                list.add(Intent().setComponent(ComponentName("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.battery.BatteryActivity")))
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                list.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")))
                list.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")))
                list.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")))
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> {
                list.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")))
                list.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")))
                list.add(Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")))
                list.add(Intent().setComponent(ComponentName("com.oplus.battery", "com.oplus.battery.PowerUsageModelActivity")))
            }
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> {
                list.add(Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")))
                list.add(Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")))
                list.add(Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")))
            }
            manufacturer.contains("transsion") || manufacturer.contains("tecno") || manufacturer.contains("infinix") -> {
                list.add(Intent().setComponent(ComponentName("com.transsion.phonemanager", "com.transsion.phonemanager.view.AutoBootManagerActivity")))
            }
        }

        // Generic battery optimization settings
        list.add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

        return list
    }
}
