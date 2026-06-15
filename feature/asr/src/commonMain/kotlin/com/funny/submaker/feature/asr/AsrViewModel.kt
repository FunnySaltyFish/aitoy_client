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
    private var targetLanguageName by userDataSaverState(targetLanguageKey, AsrLanguage.En.name)
    private var recentMediaJson by userDataSaverState(recentMediaKey, "[]")
    private var recentLanguagePairJson by userDataSaverState(recentLanguagePairKey, "[]")

    val translationTermbases = listOf("通用科技", "产品品牌", "人物访谈", "影视解说")
    val translationModels = listOf("GPT-5.4", "GPT-5.4 Mini", "Qwen Max", "DeepSeek V3")

    var apiBaseUrl by mutableStateOf("")
    var apiKey by mutableStateOf("")

    var mediaUri by mutableStateOf<Uri?>(null)
    var mediaName by mutableStateOf<String?>(null)
    var mediaMimeType by mutableStateOf<String?>(null)
    var mediaSizeBytes by mutableStateOf<Long?>(null)

    var sourceLanguage by mutableStateOf(sourceLanguageName.toAsrLanguageOr(AsrLanguage.Auto))
    var targetLanguage by mutableStateOf(targetLanguageName.toAsrLanguageOr(AsrLanguage.En))
    var recentMedia by mutableStateOf(loadRecentMedia(recentMediaJson))
    var recentLanguagePairs by mutableStateOf(loadRecentLanguagePairs(recentLanguagePairJson))

    var segments by mutableStateOf<List<SubtitleSegment>>(emptyList())
    var selectedSegmentIndex by mutableStateOf<Int?>(null)
    var currentPreviewPositionMs by mutableStateOf(0L)

    var running by mutableStateOf(false)
    var stageText by mutableStateOf("待开始")
    var transcriptionProgress by mutableStateOf(0f)
    var estimatedRemainingText by mutableStateOf("预计约 01:20")
    var lastResult by mutableStateOf<String?>(null)
    var errorMessage by mutableStateOf<String?>(null)
    var linkedProjectId by mutableStateOf<String?>(null)
    var linkedProjectName by mutableStateOf<String?>(null)

    var translationPhase by mutableStateOf(TranslationWorkflowStage.Idle)
    var translationRecommendation by mutableStateOf<TranslationRecommendation?>(null)
    var glossaryTerms by mutableStateOf<List<TranslationGlossaryTerm>>(emptyList())
    var selectedTermbase by mutableStateOf(translationTermbases.first())
    var selectedTranslationModel by mutableStateOf(translationModels.first())

    fun clearError() {
        errorMessage = null
    }

    fun onMediaPicked(uri: Uri) {
        syncScopedStateIfNeeded()
        mediaUri = uri
        mediaName = uri.displayName()
        mediaMimeType = uri.mimeType()
        mediaSizeBytes = uri.sizeBytes()
        resetWorkspaceState()
        stageText = "已选择文件"
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
        resetWorkspaceState()
        stageText = "已选择最近文件"
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
        resetWorkspaceState()
        running = true
        updateProgress(0.06f, "预计约 01:20")
        stageText = "准备上传"
        updateLinkedProjectStatus(ProjectStatus.Running.value)
        vmScope.launch {
            runCatching {
                val targetBaseUrl = apiBaseUrl.trim().ifBlank { null }
                val localApiKey = apiKey.trim().ifBlank { null }
                val asrService = SubMakerServices.asrService(targetBaseUrl)
                val sessionId = if (localApiKey != null) {
                    stageText = "创建 ASR 会话"
                    updateProgress(0.12f, "预计约 01:15")
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
                updateProgress(0.18f, "预计约 01:10")
                val fileBytes = uri.readBytes()
                if (fileBytes.isEmpty()) {
                    throw ApiException(1001, "读取文件失败：内容为空")
                }
                val fileName = (mediaName ?: "media_${nowMs()}.bin").ifBlank { "media_${nowMs()}.bin" }
                val fileType = mediaMimeType ?: "application/octet-stream"

                stageText = "获取上传票据"
                updateProgress(0.32f, "预计约 00:56")
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

                stageText = "传输媒体文件"
                updateProgress(0.54f, "预计约 00:42")
                SubMakerServices.uploadService.uploadFileToTicket(
                    uploadHost = ticket.uploadHost,
                    formFields = ticket.formFields,
                    fileName = fileName,
                    contentType = fileType,
                    bytes = fileBytes,
                )

                stageText = "提交识别任务"
                updateProgress(0.68f, "预计约 00:28")
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
                    stageText = "转录处理中"
                    val fraction = 0.72f + (pollCount.toFloat() / maxPollCount.toFloat()) * 0.22f
                    updateProgress(fraction, estimateTextForPoll(pollCount))
                    delay(1500)
                    status = apiRequest { asrService.pollJob(created.jobId) }.status
                }
                if (status != "SUCCEEDED") {
                    throw ApiException(1502, "识别未完成，当前状态：$status")
                }

                stageText = "整理字幕结果"
                updateProgress(0.96f, "预计约 00:03")
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
                selectedSegmentIndex = 0
                currentPreviewPositionMs = parsed.first().startMs
                stageText = "转录完成"
                updateProgress(1f, "已完成")
                lastResult = "识别完成：${segments.size} 条字幕，已进入精修区"
                updateLinkedProjectStatus(ProjectStatus.Done.value)
            }.onFailure {
                errorMessage = it.userMessage()
                stageText = "失败"
                estimatedRemainingText = "请重试"
                updateLinkedProjectStatus(ProjectStatus.Failed.value)
                running = false
            }.onSuccess {
                running = false
            }
        }
    }

    fun retryAsr() {
        startAsr()
    }

    fun selectSegment(index: Int) {
        val target = segments.getOrNull(index) ?: return
        selectedSegmentIndex = index
        currentPreviewPositionMs = target.startMs
    }

    fun updateSelectedSegmentText(text: String) {
        updateSelectedSegment { it.copy(text = text) }
    }

    fun nudgeSelectedSegmentStart(deltaMs: Long) {
        updateSelectedSegment { current ->
            val nextStart = (current.startMs + deltaMs).coerceAtLeast(0L)
            val safeEnd = maxOf(nextStart + 100L, current.endMs)
            current.copy(startMs = minOf(nextStart, safeEnd - 100L), endMs = safeEnd).normalize()
        }
    }

    fun nudgeSelectedSegmentEnd(deltaMs: Long) {
        updateSelectedSegment { current ->
            val nextEnd = (current.endMs + deltaMs).coerceAtLeast(current.startMs + 100L)
            current.copy(endMs = nextEnd).normalize()
        }
    }

    fun splitSelectedSegmentIntoTwoLines() {
        updateSelectedSegment { current ->
            val normalized = current.text.trim().replace("\n", " ")
            if (normalized.length <= 8) return@updateSelectedSegment current
            val splitIndex = normalized.findBalancedSplitIndex()
            current.copy(text = normalized.substring(0, splitIndex).trim() + "\n" + normalized.substring(splitIndex).trim())
        }
    }

    fun mergeSelectedWithNext() {
        val index = selectedSegmentIndex ?: return
        if (index >= segments.lastIndex) return
        val current = segments[index]
        val next = segments[index + 1]
        val merged = SubtitleSegment(
            startMs = current.startMs,
            endMs = maxOf(current.endMs, next.endMs),
            text = listOf(current.text.trim(), next.text.trim()).filter { it.isNotBlank() }.joinToString("\n"),
        )
        segments = buildList {
            addAll(segments.subList(0, index))
            add(merged)
            addAll(segments.subList(index + 2, segments.size))
        }
        selectedSegmentIndex = index
        currentPreviewPositionMs = merged.startMs
    }

    fun selectTranslationTermbase(label: String) {
        selectedTermbase = label
    }

    fun selectTranslationModel(model: String) {
        selectedTranslationModel = model
    }

    fun scanTranslationRecommendation() {
        if (segments.isEmpty() || translationPhase == TranslationWorkflowStage.Scanning) return
        translationPhase = TranslationWorkflowStage.Scanning
        translationRecommendation = null
        glossaryTerms = emptyList()
        vmScope.launch {
            delay(1200)
            val recommendation = buildRecommendation()
            translationRecommendation = recommendation
            glossaryTerms = buildGlossaryTerms(recommendation.topic)
            translationPhase = TranslationWorkflowStage.Ready
        }
    }

    fun confirmTranslationRecommendation() {
        if (translationPhase != TranslationWorkflowStage.Ready) return
        translationPhase = TranslationWorkflowStage.Translating
        stageText = "已进入翻译状态"
    }

    fun addGlossaryTerm() {
        glossaryTerms = glossaryTerms + TranslationGlossaryTerm(
            id = "term_${nowMs()}_${glossaryTerms.size}",
            source = "",
            target = "",
        )
    }

    fun updateGlossaryTermSource(id: String, value: String) {
        glossaryTerms = glossaryTerms.map { term ->
            if (term.id == id) term.copy(source = value) else term
        }
    }

    fun updateGlossaryTermTarget(id: String, value: String) {
        glossaryTerms = glossaryTerms.map { term ->
            if (term.id == id) term.copy(target = value) else term
        }
    }

    fun deleteGlossaryTerm(id: String) {
        glossaryTerms = glossaryTerms.filterNot { it.id == id }
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

    private fun resetWorkspaceState() {
        running = false
        segments = emptyList()
        selectedSegmentIndex = null
        currentPreviewPositionMs = 0L
        transcriptionProgress = 0f
        estimatedRemainingText = "预计约 01:20"
        lastResult = null
        clearError()
        resetTranslationState()
    }

    private fun resetTranslationState() {
        translationPhase = TranslationWorkflowStage.Idle
        translationRecommendation = null
        glossaryTerms = emptyList()
        selectedTermbase = translationTermbases.first()
        selectedTranslationModel = translationModels.first()
    }

    private fun updateSelectedSegment(transform: (SubtitleSegment) -> SubtitleSegment) {
        val index = selectedSegmentIndex ?: return
        val current = segments.getOrNull(index) ?: return
        val updated = transform(current).normalize()
        segments = segments.toMutableList().also { it[index] = updated }
        currentPreviewPositionMs = updated.startMs
    }

    private fun updateProgress(progress: Float, estimate: String) {
        transcriptionProgress = progress.coerceIn(0f, 1f)
        estimatedRemainingText = estimate
    }

    private fun estimateTextForPoll(pollCount: Int): String {
        val remainSeconds = ((maxPollCount - pollCount).coerceAtLeast(1) * 1.5f).toInt()
        val minutes = remainSeconds / 60
        val seconds = remainSeconds % 60
        return "预计约 ${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }

    private fun buildRecommendation(): TranslationRecommendation {
        val corpus = buildString {
            append(mediaName.orEmpty())
            append(' ')
            append(segments.take(6).joinToString(" ") { it.text })
        }.lowercase()
        val topic = when {
            corpus.contains("interview") || corpus.contains("采访") || corpus.contains("主持") -> "人物访谈 / 纪实表达"
            corpus.contains("game") || corpus.contains("剧情") || corpus.contains("直播") -> "影视娱乐 / 情绪表达"
            corpus.contains("product") || corpus.contains("demo") || corpus.contains("模型") || corpus.contains("技术") -> "科技演示 / 产品讲解"
            else -> "知识解说 / 通用内容"
        }
        val style = when (topic) {
            "人物访谈 / 纪实表达" -> "口语顺滑、保留态度与语气"
            "影视娱乐 / 情绪表达" -> "节奏鲜明、短句优先、兼顾情绪落点"
            "科技演示 / 产品讲解" -> "专业克制、术语统一、优先信息准确"
            else -> "自然直译、兼顾读感与完整度"
        }
        val summary = "专业翻译智能体检测到此视频为 $topic，将采用 $style 的翻译策略，并优先匹配 $selectedTermbase 术语库。"
        return TranslationRecommendation(
            topic = topic,
            style = style,
            summary = summary,
        )
    }

    private fun buildGlossaryTerms(topic: String): List<TranslationGlossaryTerm> {
        val terms = when (topic) {
            "人物访谈 / 纪实表达" -> listOf(
                "镜头前表达" to "on-camera delivery",
                "追问" to "follow-up question",
                "情绪停顿" to "emotional pause",
            )
            "影视娱乐 / 情绪表达" -> listOf(
                "高能片段" to "highlight moment",
                "剧情反转" to "plot twist",
                "卡点" to "beat drop",
            )
            "科技演示 / 产品讲解" -> listOf(
                "工作流" to "workflow",
                "延迟" to "latency",
                "上下文窗口" to "context window",
                "推理模型" to "reasoning model",
            )
            else -> listOf(
                "核心观点" to "core idea",
                "重点信息" to "key message",
                "补充说明" to "additional note",
            )
        }
        return terms.mapIndexed { index, (source, target) ->
            TranslationGlossaryTerm(
                id = "term_${nowMs()}_$index",
                source = source,
                target = target,
            )
        }
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
        targetLanguage = targetLanguageName.toAsrLanguageOr(AsrLanguage.En)
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

private fun SubtitleSegment.normalize(): SubtitleSegment {
    val nextStart = startMs.coerceAtLeast(0L)
    val nextEnd = endMs.coerceAtLeast(nextStart + 100L)
    return copy(startMs = nextStart, endMs = nextEnd)
}

private fun String.findBalancedSplitIndex(): Int {
    val middle = length / 2
    val left = lastIndexOf(' ', startIndex = middle)
    val right = indexOf(' ', startIndex = middle)
    return when {
        left >= 4 -> left
        right in 1 until lastIndex -> right
        else -> middle
    }
}
