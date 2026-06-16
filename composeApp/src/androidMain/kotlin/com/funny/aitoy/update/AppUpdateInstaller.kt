package com.funny.aitoy.update

import android.content.Intent
import androidx.core.content.FileProvider
import com.funny.aitoy.core.kmp.appCtx
import com.funny.aitoy.network.OkHttpUtils
import java.io.File

object AppUpdateInstaller {
    fun downloadAndInstall(
        url: String,
        versionName: String,
        onProgress: (Int) -> Unit,
    ) {
        val outputFile = File(appCtx.cacheDir, "updates/aitoy-$versionName.apk")
        OkHttpUtils.download(url, outputFile, onProgress)
        installApk(outputFile)
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(appCtx, "${appCtx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        appCtx.startActivity(intent)
    }
}
