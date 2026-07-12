package com.funny.aitoy

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import com.funny.aitoy.core.kmp.appCtx
import com.funny.aitoy.core.kmp.openUrl
import com.funny.aitoy.diagnostics.AiToyCrashReporter
import com.funny.aitoy.update.AppUpdateInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

actual object BridgePlatform {
    actual val appVersionCode: Int by lazy {
        runCatching {
            val info = appCtx.packageManager.getPackageInfo(appCtx.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
        }.getOrDefault(100)
    }

    actual val appVersionName: String by lazy {
        runCatching {
            appCtx.packageManager.getPackageInfo(appCtx.packageName, 0).versionName.orEmpty()
        }.getOrDefault("0.1.0")
    }

    actual fun consumeLastCrashNotice(): String =
        AiToyCrashReporter.consumeLastCrashNotice().orEmpty()

    actual fun startForegroundService() {
        AiToyForegroundService.start(appCtx)
    }

    actual fun stopForegroundService() {
        AiToyForegroundService.stop(appCtx)
    }

    actual fun openUrl(url: String) {
        appCtx.openUrl(url)
    }

    actual fun openBatteryConnectionSettings() {
        val packageName = appCtx.packageName
        val powerManager = appCtx.getSystemService(PowerManager::class.java)
        val intent = if (powerManager?.isIgnoringBatteryOptimizations(packageName) == false) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:$packageName"))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$packageName"))
        }
        openSettingsIntent(intent)
    }

    actual fun openNotificationSettings() {
        val packageName = appCtx.packageName
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData("package:$packageName".toUri())
        }
        openSettingsIntent(intent)
    }

    actual fun stableDeviceId(address: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(address.toByteArray())
            .take(8)
            .joinToString("") { "%02x".format(it) }
        return "dev_$digest"
    }

    actual suspend fun downloadAndInstallUpdate(
        url: String,
        versionName: String,
        onProgress: (Int) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            AppUpdateInstaller.downloadAndInstall(url, versionName, onProgress)
        }
    }

    private fun openSettingsIntent(intent: Intent) {
        val packageName = appCtx.packageName
        runCatching {
            appCtx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            appCtx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData("package:$packageName".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
