package com.funny.aitoy.update

import android.content.Intent
import androidx.core.content.FileProvider
import com.funny.aitoy.core.kmp.appCtx
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object AppUpdateInstaller {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun downloadAndInstall(
        url: String,
        versionName: String,
        onProgress: (Int) -> Unit,
    ) {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("下载失败：${response.code}")
            val body = response.body ?: error("下载失败")
            val total = body.contentLength().coerceAtLeast(1L)
            val outputFile = File(appCtx.cacheDir, "updates/aitoy-$versionName.apk")
            outputFile.parentFile?.mkdirs()
            body.byteStream().use { input ->
                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var readTotal = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        readTotal += read
                        onProgress(((readTotal * 100) / total).toInt().coerceIn(0, 100))
                    }
                }
            }
            installApk(outputFile)
        }
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
