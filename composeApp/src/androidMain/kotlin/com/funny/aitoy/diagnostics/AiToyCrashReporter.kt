package com.funny.aitoy.diagnostics

import android.os.Build
import com.funny.aitoy.BridgeViewModel
import com.funny.aitoy.core.log.Log
import com.funny.aitoy.core.log.RecentLogBuffer
import com.funny.aitoy.core.prefs.DataSaverUtils
import com.funny.aitoy.network.api.AiToyServices
import com.funny.aitoy.network.api.apiRequest
import com.funny.aitoy.network.api.service.CrashReportRequest
import kotlinx.coroutines.runBlocking
import java.io.PrintWriter
import java.io.StringWriter
import java.util.UUID

object AiToyCrashReporter {
    private const val TAG = "AiToyCrash"
    private const val LAST_CRASH_REPORT_ID = "LAST_CRASH_REPORT_ID"
    private const val LAST_CRASH_SENT = "LAST_CRASH_SENT"

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val reportId = UUID.randomUUID().toString().replace("-", "")
            val sent = uploadCrash(reportId, throwable)
            DataSaverUtils.saveData(LAST_CRASH_REPORT_ID, reportId)
            DataSaverUtils.saveData(LAST_CRASH_SENT, sent)
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun consumeLastCrashNotice(): String? {
        val reportId = DataSaverUtils.readData(LAST_CRASH_REPORT_ID, "")
        if (reportId.isBlank()) return null
        val sent = DataSaverUtils.readData(LAST_CRASH_SENT, false)
        DataSaverUtils.remove(LAST_CRASH_REPORT_ID)
        DataSaverUtils.remove(LAST_CRASH_SENT)
        return if (sent) {
            "上次异常已自动发送，编号：$reportId"
        } else {
            "上次异常日志暂未发送成功。"
        }
    }

    private fun uploadCrash(reportId: String, throwable: Throwable): Boolean {
        return runCatching {
            runBlocking {
                apiRequest {
                    AiToyServices.diagnosticsService.uploadCrash(
                        CrashReportRequest(
                            reportId = reportId,
                            versionCode = BridgeViewModel.APP_VERSION_CODE,
                            versionName = BridgeViewModel.APP_VERSION_NAME,
                            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                            androidVersion = Build.VERSION.RELEASE ?: "",
                            exceptionTrace = throwable.stackTraceText(),
                            recentLogs = RecentLogBuffer.snapshot(),
                        )
                    )
                }
            }
            true
        }.onFailure {
            Log.e(TAG, it) { "崩溃日志上传失败" }
        }.getOrDefault(false)
    }

    private fun Throwable.stackTraceText(): String {
        val writer = StringWriter()
        PrintWriter(writer).use { printWriter -> printStackTrace(printWriter) }
        return writer.toString()
    }
}
