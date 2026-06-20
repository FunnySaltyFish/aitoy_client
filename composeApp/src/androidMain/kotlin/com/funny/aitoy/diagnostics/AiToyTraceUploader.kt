package com.funny.aitoy.diagnostics

import android.os.Build
import com.funny.aitoy.BridgeViewModel
import com.funny.aitoy.core.log.Log
import com.funny.aitoy.core.log.LogEvent
import com.funny.aitoy.core.log.LogSink
import com.funny.aitoy.core.utils.JsonX
import com.funny.aitoy.network.OkHttpUtils
import com.funny.aitoy.network.api.AiToyServices
import com.funny.aitoy.network.api.apiRequest
import com.funny.aitoy.network.api.service.TraceLogItem
import com.funny.aitoy.network.api.service.TraceUploadRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import kotlin.math.min

object AiToyTraceUploader : LogSink {
    private const val TAG = "AiToyTraceUpload"
    private const val MAX_PENDING = 800
    private const val MAX_BATCH = 120
    private const val MAX_MESSAGE_LENGTH = 4_000
    private const val UPLOAD_DELAY_MS = 2_000L
    private const val WAIT_CONTEXT_RETRY_MS = 5_000L
    private const val MAX_RETRY_DELAY_MS = 120_000L
    private const val PROBE_MIN_INTERVAL_MS = 30_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val probeLock = Any()
    private val pending = ArrayDeque<TraceLogItem>()
    private val lastProbeAt = mutableMapOf<String, Long>()

    private var uploadScheduled = false
    private var uploading = false
    private var installed = false
    private var failureCount = 0
    private var probeSeq = 0L
    private var lastContextProbeKey = ""

    private val sessionId: String = UUID.randomUUID().toString().replace("-", "")

    @Volatile
    private var context = TraceContext()

    fun install() {
        if (installed) return
        installed = true
        Log.addSink(this)
        emitProbe("install", force = true)
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
        val probeKey = listOf(userToken, selectedDeviceName, connectionState, protocolId).joinToString("|")
        if (probeKey != lastContextProbeKey) {
            lastContextProbeKey = probeKey
            emitProbe("context_update")
        }
        scheduleFlushIfNeeded(0L)
    }

    fun recordBle(message: String, error: Boolean = false) {
        enqueue(
            TraceLogItem(
                tsMs = System.currentTimeMillis(),
                level = if (error) "ERROR" else "INFO",
                tag = "AiToyBle",
                message = message.take(MAX_MESSAGE_LENGTH),
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
                }.take(MAX_MESSAGE_LENGTH),
            )
        )
    }

    private fun enqueue(item: TraceLogItem) {
        val shouldSchedule = synchronized(lock) {
            while (pending.size >= MAX_PENDING) pending.removeFirst()
            pending.addLast(item)
            if (uploadScheduled || uploading) {
                false
            } else {
                uploadScheduled = true
                true
            }
        }
        if (shouldSchedule) {
            scheduleFlush(UPLOAD_DELAY_MS)
        }
    }

    private fun scheduleFlushIfNeeded(delayMs: Long) {
        val shouldSchedule = synchronized(lock) {
            if (pending.isEmpty() || uploadScheduled || uploading) {
                false
            } else {
                uploadScheduled = true
                true
            }
        }
        if (shouldSchedule) {
            scheduleFlush(delayMs)
        }
    }

    private fun scheduleFlush(delayMs: Long) {
        scope.launch {
            if (delayMs > 0) delay(delayMs)
            flush()
        }
    }

    private fun retryDelayMs(nextFailureCount: Int): Long {
        val multiplier = 1L shl min(nextFailureCount - 1, 4)
        return min(10_000L * multiplier, MAX_RETRY_DELAY_MS)
    }

    private fun pendingSize(): Int = synchronized(lock) { pending.size }

    private suspend fun flush() {
        val current = context
        if (current.userToken.isBlank()) {
            synchronized(lock) {
                uploadScheduled = false
            }
            emitProbe(
                "waiting_context",
                extra = mapOf("pendingCount" to pendingSize()),
            )
            scheduleFlushIfNeeded(WAIT_CONTEXT_RETRY_MS)
            return
        }

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

        emitProbe(
            "flush_start",
            extra = mapOf("batchSize" to batch.size, "pendingAfterTake" to pendingSize()),
        )

        var accepted = 0
        var failure: Throwable? = null
        val sent = runCatching {
            val response = apiRequest {
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
            accepted = response.accepted
            true
        }.onFailure {
            failure = it
            Log.w(TAG, it) { "Trace 日志上传失败" }
        }.getOrDefault(false)

        val nextRetryDelay = if (sent) UPLOAD_DELAY_MS else retryDelayMs(failureCount + 1)
        synchronized(lock) {
            uploading = false
            if (sent) {
                failureCount = 0
            } else {
                failureCount += 1
                batch.asReversed().forEach { pending.addFirst(it) }
                while (pending.size > MAX_PENDING) pending.removeFirst()
            }
            if (pending.isNotEmpty() && !uploadScheduled) {
                uploadScheduled = true
                scheduleFlush(nextRetryDelay)
            }
        }

        if (sent) {
            emitProbe(
                "flush_success",
                extra = mapOf("batchSize" to batch.size, "accepted" to accepted),
            )
        } else {
            emitProbe(
                "flush_failure",
                force = true,
                extra = mapOf(
                    "batchSize" to batch.size,
                    "pendingCount" to pendingSize(),
                    "failureCount" to failureCount,
                    "retryDelayMs" to nextRetryDelay,
                    "errorClass" to (failure?.let { it::class.simpleName } ?: "Throwable"),
                    "errorMessage" to failure?.message.orEmpty().take(500),
                ),
            )
        }
    }

    private fun emitProbe(
        event: String,
        force: Boolean = false,
        extra: Map<String, Any?> = emptyMap(),
    ) {
        val now = System.currentTimeMillis()
        val seq = synchronized(probeLock) {
            val lastAt = lastProbeAt[event] ?: 0L
            if (!force && now - lastAt < PROBE_MIN_INTERVAL_MS) return
            lastProbeAt[event] = now
            probeSeq += 1
            probeSeq
        }
        val current = context
        scope.launch {
            runCatching {
                val payload = buildJsonObject {
                    put("probeEvent", event)
                    put("probeSeq", seq)
                    put("sessionId", sessionId)
                    put("userToken", current.userToken)
                    put("tokenKind", current.tokenKind())
                    put("versionCode", BridgeViewModel.APP_VERSION_CODE)
                    put("versionName", BridgeViewModel.APP_VERSION_NAME)
                    put("deviceModel", "${Build.MANUFACTURER} ${Build.MODEL}")
                    put("androidVersion", Build.VERSION.RELEASE ?: "")
                    put("selectedDeviceName", current.selectedDeviceName)
                    put("selectedDeviceAddress", current.selectedDeviceAddress)
                    put("connectionState", current.connectionState)
                    put("protocolId", current.protocolId)
                    put("protocolName", current.protocolName)
                    put("pendingCount", pendingSize())
                    extra.forEach { (key, value) ->
                        when (value) {
                            null -> put(key, "")
                            is Int -> put(key, value)
                            is Long -> put(key, value)
                            is Boolean -> put(key, value)
                            is Number -> put(key, value.toDouble())
                            else -> put(key, value.toString())
                        }
                    }
                }
                postProbe(payload)
            }
        }
    }

    private fun postProbe(payload: JsonObject) {
        val body = JsonX.json.encodeToString(JsonObject.serializer(), payload)
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("${OkHttpUtils.currentApiBaseUrl}diagnostics/trace-probe")
            .post(body)
            .build()
        OkHttpUtils.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
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

        fun tokenKind(): String = when {
            userToken.isBlank() -> "blank"
            userToken == "ut_fishfish" -> "fishfish"
            userToken.startsWith("ut_") -> "random"
            else -> "custom"
        }
    }
}
