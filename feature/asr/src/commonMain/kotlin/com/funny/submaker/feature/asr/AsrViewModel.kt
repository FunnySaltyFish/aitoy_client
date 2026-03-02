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
import com.funny.submaker.core.prefs.SubMakerPrefs
import com.funny.submaker.core.prefs.currentUserDataOwnerId
import com.funny.submaker.core.prefs.userDataSaverState
import com.funny.submaker.core.subtitle.SubtitleSegment
import com.funny.submaker.core.utils.JsonX
import com.funny.submaker.core.utils.nowMs
import com.funny.submaker.database.SubtitleProjectRepository
import com.funny.submaker.database.model.SubtitleProjectEntity
import com.funny.submaker.network.api.ApiException
import com.funny.submaker.network.api.SubMakerServices
import com.funny.submaker.network.api.apiRequest
import com.funny.submaker.network.api.service.AsrCreateJobReq
import com.funny.submaker.network.api.service.AsrOptionsReq
import com.funny.submaker.network.api.service.AsrSessionReq
import com.funny.submaker.network.api.service.AsrUploadTicketReq
import com.funny.submaker.network.api.service.uploadFileToTicket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AsrViewModel : ViewModel() {
    private val vmScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val modelName = "qwen3-asr-flash-filetrans"
    private val maxPollCount = 120

    private val recentMediaKey = "ASR_RECENT_MEDIA_JSON"
    private val recentLanguagePairKey = "ASR_RECENT_LANGUAGE_PAIRS_JSON"
    private val sourceLanguageKey = "ASR_SOURCE_LANGUAGE"
    private val targetLanguageKey = "ASR_TARGET_LANGUAGE"
    private var scopedOwnerId = currentUserDataOwnerId()

    private var sourceLanguageName by userDataSaverState(sourceLanguageKey, AsrLanguage.Auto.name)
    private var targetLanguageName by userDataSaverState(targetLanguageKey, AsrLanguage.Zh.name)
    private var recentMediaJson by userDataSaverState(recentMediaKey, "[]")
    private var recentLanguagePairJson by userDataSaverState(recentLanguagePairKey, "[]")

    var apiBaseUrl by mutableStateOf("")
    var apiKey by mutableStateOf("")

    var mediaUri by mutableStateOf<Uri?>(null)
    var mediaName by mutableStateOf<String?>(null)
    var mediaMimeType by mutableStateOf<String?>(null)
    var mediaSizeBytes by mutableStateOf<Long?>(null)

    var sourceLanguage by mutableStateOf(sourceLanguageName.toAsrLanguageOr(AsrLanguage.Auto))
    var targetLanguage by mutableStateOf(targetLanguageName.toAsrLanguageOr(AsrLanguage.Zh))
    var recentMedia by mutableStateOf(loadRecentMedia(recentMediaJson))
    var recentLanguagePairs by mutableStateOf(loadRecentLanguagePairs(recentLanguagePairJson))

    var segments by mutableStateOf<List<SubtitleSegment>>(emptyList())

    var running by mutableStateOf(false)
    var stageText by mutableStateOf("待开始")
    var lastResult by mutableStateOf<String?>(null)
    var errorMessage by mutableStateOf<String?>(null)
    var linkedProjectId by mutableStateOf<String?>(null)
    var linkedProjectName by mutableStateOf<String?>(null)

    fun clearError() {
        errorMessage = null
    }

    fun onMediaPicked(uri: Uri) {
        syncScopedStateIfNeeded()
        mediaUri = uri
        mediaName = uri.displayName()
        mediaMimeType = uri.mimeType()
        mediaSizeBytes = uri.sizeBytes()
        stageText = "已选择文件"
        clearError()
        saveRecentMedia(
            AsrRecentMedia(
                uri = uri.toString(),
                name = mediaName ?: "未命名文件",
                mimeType = mediaMimeType,
                sizeBytes = mediaSizeBytes,
                updatedAtMs = nowMs(),
            )
        )
        linkedProjectId?.let {
            upsertLinkedProject { current ->
                current.copy(
                    sourceFileName = mediaName ?: current.sourceFileName,
                    updatedAtEpochMillis = nowMs(),
                )
            }
        }
    }

    fun onRecentMediaSelected(item: AsrRecentMedia) {
        syncScopedStateIfNeeded()
        mediaUri = Uri.parse(item.uri)
        mediaName = item.name
        mediaMimeType = item.mimeType
        mediaSizeBytes = item.sizeBytes
        stageText = "已选择最近文件"
        clearError()
        saveRecentMedia(item.copy(updatedAtMs = nowMs()))
    }

    fun updateSourceLanguage(language: AsrLanguage) {
        syncScopedStateIfNeeded()
        sourceLanguage = language
        persistLanguageSelection()
    }

    fun updateTargetLanguage(language: AsrLanguage) {
        syncScopedStateIfNeeded()
        targetLanguage = language
        persistLanguageSelection()
    }

    fun swapLanguages() {
        syncScopedStateIfNeeded()
        val from = sourceLanguage
        sourceLanguage = targetLanguage
        targetLanguage = from
        persistLanguageSelection()
    }

    fun applyRecentLanguagePair(pair: AsrRecentLanguagePair) {
        syncScopedStateIfNeeded()
        sourceLanguage = pair.source
        targetLanguage = pair.target
        persistLanguageSelection()
    }

    fun confirmLanguageSelection() {
        syncScopedStateIfNeeded()
        persistLanguageSelection()
        val next = AsrRecentLanguagePair(
            source = sourceLanguage,
            target = targetLanguage,
            updatedAtMs = nowMs(),
        )
        val merged = buildList {
            add(next)
            addAll(recentLanguagePairs.filterNot { it.source == next.source && it.target == next.target })
        }.take(4)
        recentLanguagePairs = merged
        persistRecentLanguagePairs(merged)
    }

    fun bindProject(projectId: String?) {
        syncScopedStateIfNeeded()
        if (projectId == linkedProjectId) return
        linkedProjectId = projectId
        linkedProjectName = null
        if (projectId == null) return
        vmScope.launch {
            SubtitleProjectRepository.queryOneById(currentOwnerUid(), projectId)
                .onSuccess { entity ->
                    linkedProjectName = entity?.name
                    if (entity != null) {
                        stageText = "已绑定项目：${entity.name}"
                    }
                }
        }
    }

    fun startAsr() {
        syncScopedStateIfNeeded()
        if (running) return
        val uri = mediaUri ?: run {
            errorMessage = "请先选择媒体文件"
            return
        }
        confirmLanguageSelection()
        running = true
        clearError()
        stageText = "准备上传"
        lastResult = null
        segments = emptyList()
        updateLinkedProjectStatus(ProjectStatus.Running.value)
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
                                language = sourceLanguage.code,
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
                updateLinkedProjectStatus(ProjectStatus.Done.value)
            }.onFailure {
                errorMessage = it.userMessage()
                stageText = "失败"
                updateLinkedProjectStatus(ProjectStatus.Failed.value)
                running = false
            }.onSuccess {
                running = false
            }
        }
    }

    fun onExportSuccess(format: String) {
        syncScopedStateIfNeeded()
        if (linkedProjectId == null) return
        upsertLinkedProject { current ->
            current.copy(
                lastExportFormat = format,
                segmentCount = segments.size,
                updatedAtEpochMillis = nowMs(),
            )
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

    private fun persistLanguageSelection() {
        sourceLanguageName = sourceLanguage.name
        targetLanguageName = targetLanguage.name
    }

    private fun saveRecentMedia(item: AsrRecentMedia) {
        val merged = buildList {
            add(item)
            addAll(recentMedia.filterNot { it.uri == item.uri })
        }.take(5)
        recentMedia = merged
        persistRecentMedia(merged)
    }

    private fun persistRecentMedia(items: List<AsrRecentMedia>) {
        val payload = buildJsonArray {
            items.forEach { media ->
                add(
                    buildJsonObject {
                        putString("uri", media.uri)
                        putString("name", media.name)
                        media.mimeType?.let { putString("mimeType", it) }
                        media.sizeBytes?.let { putLong("sizeBytes", it) }
                        putLong("updatedAtMs", media.updatedAtMs)
                    }
                )
            }
        }.toString()
        recentMediaJson = payload
    }

    private fun loadRecentMedia(raw: String): List<AsrRecentMedia> {
        val arr = raw.toJsonArrayOrEmpty()
        return arr.mapNotNull { item ->
            val obj = item.jsonObject
            val uri = obj.string("uri") ?: return@mapNotNull null
            val name = obj.string("name") ?: return@mapNotNull null
            AsrRecentMedia(
                uri = uri,
                name = name,
                mimeType = obj.string("mimeType"),
                sizeBytes = obj.long("sizeBytes"),
                updatedAtMs = obj.long("updatedAtMs") ?: 0L,
            )
        }.sortedByDescending { it.updatedAtMs }.take(5)
    }

    private fun persistRecentLanguagePairs(items: List<AsrRecentLanguagePair>) {
        val payload = buildJsonArray {
            items.forEach { pair ->
                add(
                    buildJsonObject {
                        putString("source", pair.source.name)
                        putString("target", pair.target.name)
                        putLong("updatedAtMs", pair.updatedAtMs)
                    }
                )
            }
        }.toString()
        recentLanguagePairJson = payload
    }

    private fun loadRecentLanguagePairs(raw: String): List<AsrRecentLanguagePair> {
        val arr = raw.toJsonArrayOrEmpty()
        return arr.mapNotNull { item ->
            val obj = item.jsonObject
            val source = obj.enum<AsrLanguage>("source") ?: return@mapNotNull null
            val target = obj.enum<AsrLanguage>("target") ?: return@mapNotNull null
            AsrRecentLanguagePair(
                source = source,
                target = target,
                updatedAtMs = obj.long("updatedAtMs") ?: 0L,
            )
        }.sortedByDescending { it.updatedAtMs }.take(4)
    }

    private fun syncScopedStateIfNeeded() {
        val ownerId = currentUserDataOwnerId()
        if (ownerId == scopedOwnerId) return
        scopedOwnerId = ownerId
        sourceLanguage = sourceLanguageName.toAsrLanguageOr(AsrLanguage.Auto)
        targetLanguage = targetLanguageName.toAsrLanguageOr(AsrLanguage.Zh)
        recentMedia = loadRecentMedia(recentMediaJson)
        recentLanguagePairs = loadRecentLanguagePairs(recentLanguagePairJson)
    }

    private fun updateLinkedProjectStatus(status: String) {
        if (linkedProjectId == null) return
        upsertLinkedProject { current ->
            current.copy(
                status = status,
                sourceFileName = mediaName ?: current.sourceFileName,
                segmentCount = if (segments.isEmpty()) current.segmentCount else segments.size,
                durationMs = if (segments.isEmpty()) {
                    current.durationMs
                } else {
                    segments.lastOrNull()?.endMs ?: current.durationMs
                },
                updatedAtEpochMillis = nowMs(),
            )
        }
    }

    private fun upsertLinkedProject(transform: (SubtitleProjectEntity) -> SubtitleProjectEntity) {
        val projectId = linkedProjectId ?: return
        vmScope.launch {
            val ownerUid = currentOwnerUid()
            SubtitleProjectRepository.queryOneById(ownerUid, projectId)
                .onSuccess { entity ->
                    val current = entity ?: return@onSuccess
                    SubtitleProjectRepository.upsert(transform(current))
                }
        }
    }

    private fun currentOwnerUid(): String {
        val uid = SubMakerPrefs.user.uid
        if (uid.isNotBlank()) return "uid:$uid"
        return "device:${SubMakerPrefs.deviceId}"
    }
}

private fun String.toJsonArrayOrEmpty(): JsonArray {
    return runCatching {
        JsonX.json.parseToJsonElement(this).jsonArray
    }.getOrDefault(JsonArray(emptyList()))
}

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.long(key: String): Long? =
    this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull()

private inline fun <reified T : Enum<T>> JsonObject.enum(key: String): T? {
    val name = string(key) ?: return null
    return enumValues<T>().firstOrNull { it.name == name }
}

private fun JsonObjectBuilder.putString(key: String, value: String) {
    put(key, JsonPrimitive(value))
}

private fun JsonObjectBuilder.putLong(key: String, value: Long) {
    put(key, JsonPrimitive(value))
}

private fun String.toAsrLanguageOr(default: AsrLanguage): AsrLanguage {
    return AsrLanguage.entries.firstOrNull { it.name == this } ?: default
}

private enum class ProjectStatus(val value: String) {
    Running("running"),
    Done("done"),
    Failed("failed"),
}

private fun String.toKeyHint(): String {
    if (length <= 8) return this
    return "${take(4)}****${takeLast(4)}"
}

private fun Throwable.userMessage(): String {
    return when (this) {
        is ApiException -> message
        else -> message ?: "请求失败"
    }
}
