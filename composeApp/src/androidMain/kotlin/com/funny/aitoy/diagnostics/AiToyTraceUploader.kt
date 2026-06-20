package com.funny.aitoy.diagnostics

import android.os.Build
import com.funny.aitoy.BridgeViewModel
import com.funny.aitoy.core.log.Log
import com.funny.aitoy.core.log.LogEvent
import com.funny.aitoy.core.log.LogSink
import com.funny.aitoy.network.api.AiToyServices
import com.funny.aitoy.network.api.apiRequest
import com.funny.aitoy.network.api.service.TraceLogItem
import com.funny.aitoy.network.api.service.TraceUploadRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

object AiToyTraceUploader : LogSink {
    private const val TAG = "AiToyTraceUpload"
    private const val MAX_PENDING = 800
    private const val MAX_BATCH = 120
    private const val UPLOAD_DELAY_MS = 2_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val pending = ArrayDeque<TraceLogItem>()
    private var uploadScheduled = false
    private var uploading = false
    private var installed = false

    private val sessionId: String = UUID.randomUUID().toString().replace("-", "")

    @Volatile
    private var context = TraceContext()

    fun install() {
        if (installed) return
        installed = true
        Log.addSink(this)
    }

    fun updateContext(
        userToken: String,
        selectedDeviceName: String,
        selectedDeviceAddress: String,
        connectionState: String,
        protocolId: String,
        protocolName: String,
    ) {
        context = TraceContext(
            userToken = userToken,
            selectedDeviceName = selectedDeviceName,
            selectedDeviceAddress = selectedDeviceAddress,
            connectionState = connectionState,
            protocolId = protocolId,
            protocolName = protocolName,
        )
    }

    fun recordBle(message: String, error: Boolean = false) {
        enqueue(
            TraceLogItem(
                tsMs = System.currentTimeMillis(),
                level = if (error) "ERROR" else "INFO",
                tag = "AiToyBle",
                message = message,
            )
        )
    }

    override fun log(event: LogEvent) {
        if (event.tag == TAG) return
        enqueue(
            TraceLogItem(
                tsMs = event.timestampMs,
                level = event.level.name,
                tag = event.tag,
                message = buildString {
                    append(event.message)
                    event.throwable?.let {
                        append(" | ")
                        append(it::class.simpleName ?: "Throwable")
                        append(": ")
                        append(it.message.orEmpty())
                    }
                },
            )
        )
    }

    private fun enqueue(item: TraceLogItem) {
        synchronized(lock) {
            while (pending.size >= MAX_PENDING) pending.removeFirst()
            pending.addLast(item)
            if (uploadScheduled || uploading) return
            uploadScheduled = true
        }
        scope.launch {
            delay(UPLOAD_DELAY_MS)
            flush()
        }
    }

    private suspend fun flush() {
        val batch = synchronized(lock) {
            uploadScheduled = false
            if (uploading || pending.isEmpty()) return
            uploading = true
            buildList {
                while (pending.isNotEmpty() && size < MAX_BATCH) {
                    add(pending.removeFirst())
                }
            }
        }
        val sent = runCatching {
            val current = context
            apiRequest {
                AiToyServices.diagnosticsService.uploadTrace(
                    TraceUploadRequest(
                        sessionId = sessionId,
                        userToken = current.userToken,
                        deviceId = current.deviceId(),
                        versionCode = BridgeViewModel.APP_VERSION_CODE,
                        versionName = BridgeViewModel.APP_VERSION_NAME,
                        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                        androidVersion = Build.VERSION.RELEASE ?: "",
                        selectedDeviceName = current.selectedDeviceName,
                        selectedDeviceAddress = current.selectedDeviceAddress,
                        connectionState = current.connectionState,
                        protocolId = current.protocolId,
                        protocolName = current.protocolName,
                        logs = batch,
                    )
                )
            }
            true
        }.onFailure {
            Log.w(TAG, it) { "Trace 日志上传失败" }
        }.getOrDefault(false)

        synchronized(lock) {
            uploading = false
            if (!sent) {
                batch.asReversed().forEach { pending.addFirst(it) }
                while (pending.size > MAX_PENDING) pending.removeFirst()
            }
            if (pending.isNotEmpty() && !uploadScheduled) {
                uploadScheduled = true
                scope.launch {
                    delay(if (sent) UPLOAD_DELAY_MS else 10_000L)
                    flush()
                }
            }
        }
    }

    private data class TraceContext(
        val userToken: String = "",
        val selectedDeviceName: String = "",
        val selectedDeviceAddress: String = "",
        val connectionState: String = "",
        val protocolId: String = "",
        val protocolName: String = "",
    ) {
        fun deviceId(): String = selectedDeviceAddress.ifBlank { "unknown" }
    }
}
