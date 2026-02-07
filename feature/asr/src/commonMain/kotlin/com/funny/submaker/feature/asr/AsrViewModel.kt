package com.funny.submaker.feature.asr

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.eygraber.uri.Uri
import com.funny.submaker.core.kmp.displayName
import com.funny.submaker.core.kmp.mimeType
import com.funny.submaker.core.kmp.readBytes
import com.funny.submaker.core.kmp.sizeBytes
import com.funny.submaker.core.subtitle.SubtitleSegment
import com.funny.submaker.core.utils.nowMs
import com.funny.submaker.network.api.ApiException
import com.funny.submaker.network.api.SubMakerServices
import com.funny.submaker.network.api.apiRequest
import com.funny.submaker.network.api.service.AsrCreateJobReq
import com.funny.submaker.network.api.service.AsrOptionsReq
import com.funny.submaker.network.api.service.AsrSessionReq
import com.funny.submaker.network.api.service.AsrUploadTicketReq
import com.funny.submaker.network.api.service.uploadFileToTicket
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AsrViewModel : ViewModel() {
    private val vmScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val modelName = "qwen3-asr-flash-filetrans"
    private val maxPollCount = 120

    var apiBaseUrl by mutableStateOf("")
    var apiKey by mutableStateOf("")

    var mediaUri by mutableStateOf<Uri?>(null)
    var mediaName by mutableStateOf<String?>(null)
    var mediaMimeType by mutableStateOf<String?>(null)
    var mediaSizeBytes by mutableStateOf<Long?>(null)

    var segments by mutableStateOf<List<SubtitleSegment>>(emptyList())

    var running by mutableStateOf(false)
    var stageText by mutableStateOf("待开始")
    var lastResult by mutableStateOf<String?>(null)
    var errorMessage by mutableStateOf<String?>(null)

    fun clearError() {
        errorMessage = null
    }

    fun onMediaPicked(uri: Uri) {
        mediaUri = uri
        mediaName = uri.displayName()
        mediaMimeType = uri.mimeType()
        mediaSizeBytes = uri.sizeBytes()
        stageText = "已选择文件"
        clearError()
    }

    fun startAsr() {
        if (running) return
        val uri = mediaUri ?: run {
            errorMessage = "请先选择媒体文件"
            return
        }
        running = true
        clearError()
        stageText = "准备上传"
        lastResult = null
        segments = emptyList()
        vmScope.launch {
            runCatching {
                val targetBaseUrl = apiBaseUrl.trim().ifBlank { null }
                val localApiKey = apiKey.trim().ifBlank { null }
                val asrService = SubMakerServices.asrService(targetBaseUrl)
                val sessionId = if (localApiKey != null) {
                    stageText = "创建 ASR 会话"
                    apiRequest {
                        asrService.createSession(
                            AsrSessionReq(
                                apiKey = localApiKey,
                                keyHint = localApiKey.toKeyHint(),
                            )
                        )
                    }.sessionId
                } else {
                    null
                }

                stageText = "读取本地文件"
                val fileBytes = uri.readBytes()
                if (fileBytes.isEmpty()) {
                    throw ApiException(1001, "读取文件失败：内容为空")
                }
                val fileName = (mediaName ?: "media_${nowMs()}.bin").ifBlank { "media_${nowMs()}.bin" }
                val fileType = mediaMimeType ?: "application/octet-stream"

                stageText = "获取上传票据"
                val ticket = apiRequest {
                    asrService.createUploadTicket(
                        AsrUploadTicketReq(
                            model = modelName,
                            fileName = fileName,
                            contentType = fileType,
                            sessionId = sessionId,
                        )
                    )
                }

                stageText = "直传音频到 OSS"
                SubMakerServices.uploadService.uploadFileToTicket(
                    uploadHost = ticket.uploadHost,
                    formFields = ticket.formFields,
                    fileName = fileName,
                    contentType = fileType,
                    bytes = fileBytes,
                )

                stageText = "提交识别任务"
                val created = apiRequest {
                    asrService.createJob(
                        AsrCreateJobReq(
                            model = modelName,
                            fileUrl = ticket.resolvedFileUrl,
                            sessionId = sessionId,
                            options = AsrOptionsReq(
                                language = null,
                                enableItn = false,
                                enableWords = false,
                                channelId = listOf(0),
                            ),
                        ),
                    )
                }

                var pollCount = 0
                var status = created.status
                while (pollCount < maxPollCount && status !in setOf("SUCCEEDED", "FAILED", "EXPIRED")) {
                    pollCount += 1
                    stageText = "识别中（$pollCount）"
                    delay(1500)
                    status = apiRequest { asrService.pollJob(created.jobId) }.status
                }
                if (status != "SUCCEEDED") {
                    throw ApiException(1502, "识别未完成，当前状态：$status")
                }

                stageText = "下载并解析结果"
                val result = apiRequest { asrService.getResult(created.jobId) }
                if (result.status != "SUCCEEDED") {
                    val err = result.error?.message ?: "任务状态异常：${result.status}"
                    throw ApiException(1503, err)
                }
                val parsed = result.segments
                    .filter { it.endMs > it.startMs && it.text.isNotBlank() }
                    .map { SubtitleSegment(startMs = it.startMs, endMs = it.endMs, text = it.text) }
                if (parsed.isEmpty()) {
                    throw ApiException(1504, "识别结果为空")
                }
                segments = parsed
                stageText = "完成"
                lastResult = "识别完成：${segments.size} 段，可导出 SRT/VTT"
            }.onFailure {
                errorMessage = it.userMessage()
                stageText = "失败"
                running = false
            }.onSuccess {
                running = false
            }
        }
    }

    fun suggestedExportFileName(ext: String): String {
        val base = (mediaName ?: "submaker_${nowMs()}").substringBeforeLast('.')
        return "$base.$ext"
    }

    override fun onCleared() {
        vmScope.cancel()
        super.onCleared()
    }
}

private fun Throwable.userMessage(): String =
    when (this) {
        is ApiException -> message
        else -> message ?: "请求失败"
    }

private fun String.toKeyHint(): String {
    if (isBlank()) return ""
    return "sk-****${takeLast(4)}"
}
