package com.funny.aitoy

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val backgroundSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it }) {
            showBackgroundConnectionDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent { App() }
        AppDeepLinks.dispatch(intent?.dataString)
        window.decorView.post {
            startPermissionFlowIfNeeded()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AppDeepLinks.dispatch(intent.dataString)
    }

    private fun startPermissionFlowIfNeeded() {
        val permissions = requiredPermissions()
        if (permissions.all { hasPermission(it) }) return
        AlertDialog.Builder(this)
            .setTitle("先允许连接设备")
            .setMessage(
                "AI Toy 需要蓝牙权限来寻找并连接附近设备；也建议开启通知权限，用于在后台连接时保持应用存活。授权后，应用会继续回到当前页面。",
            )
            .setPositiveButton("继续授权") { _, _ ->
                permissionLauncher.launch(permissions)
            }
            .setNegativeButton("稍后再说", null)
            .show()
    }

    private fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            buildList {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }.toTypedArray()
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun showBackgroundConnectionDialog() {
        AlertDialog.Builder(this)
            .setTitle("保持后台连接")
            .setMessage(
                "手机需要持续在线，AI 才能在你切到其他应用后继续控制设备。建议在系统设置里允许后台运行，并在最近任务中锁定 AI Toy，避免被系统清理。设置完成后会自动回到这里。",
            )
            .setPositiveButton("去设置") { _, _ ->
                openBackgroundConnectionSettings()
            }
            .setNegativeButton("稍后设置", null)
            .show()
    }

    private fun openBackgroundConnectionSettings() {
        val packageName = packageName
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(PowerManager::class.java)
            if (powerManager?.isIgnoringBatteryOptimizations(packageName) == false) {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:$packageName"))
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$packageName"))
        }
        runCatching {
            backgroundSettingsLauncher.launch(intent)
        }.onFailure {
            backgroundSettingsLauncher.launch(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:$packageName")),
            )
        }
    }
}
