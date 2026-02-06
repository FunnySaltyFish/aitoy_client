package com.funny.submaker.feature.asr

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.eygraber.uri.Uri
import com.funny.submaker.core.kmp.displayName
import com.funny.submaker.core.kmp.mimeType
import com.funny.submaker.core.kmp.sizeBytes
import com.funny.submaker.core.prefs.SubMakerPrefs
import com.funny.submaker.core.subtitle.SubtitleSegment
import com.funny.submaker.core.utils.nowMs
import com.funny.submaker.network.api.ApiException
import com.funny.submaker.network.api.SubMakerApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AsrViewModel : ViewModel() {
    private val vmScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var apiBaseUrl by mutableStateOf("")
    var apiKey by mutableStateOf("")

    var mediaUri by mutableStateOf<Uri?>(null)
    var mediaName by mutableStateOf<String?>(null)
    var mediaMimeType by mutableStateOf<String?>(null)
    var mediaSizeBytes by mutableStateOf<Long?>(null)

    var segments by mutableStateOf<List<SubtitleSegment>>(emptyList())

    var running by mutableStateOf(false)
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
        clearError()
    }

    fun mockRunAsr() {
        running = true
        clearError()
        val baseName = (mediaName ?: "media_${nowMs()}").substringBeforeLast('.')
        segments = listOf(
            SubtitleSegment(0, 1_500, "你好，这是一段示例字幕。"),
            SubtitleSegment(1_600, 3_600, "SubMaker MVP：导入媒体 → 云端 ASR → 导出 SRT/VTT。"),
            SubtitleSegment(3_700, 5_800, "文件：$baseName"),
        )
        lastResult = "已生成示例时间轴（${segments.size} 段），现在可以导出。"
        running = false
    }

    fun startMockAsr() {
        if (running) return
        running = true
        clearError()
        vmScope.launch {
            val costSeconds = 10
            runCatching {
                SubMakerApi.consumeTrial(seconds = costSeconds, deviceId = SubMakerPrefs.deviceId)
            }.onSuccess { payload ->
                payload.user?.let { SubMakerPrefs.user = it }
                // device 信息目前不做全局存储；账号页会拉取 device_status 展示
                mockRunAsr()
                lastResult = "已扣减试用：${costSeconds}s（MVP 模拟）。${lastResult.orEmpty()}"
            }.onFailure {
                errorMessage = it.userMessage()
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
