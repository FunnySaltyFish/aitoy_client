package com.funny.aitoy.chat

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.funny.aitoy.BridgeViewModel
import com.funny.aitoy.core.prefs.DataSaverUtils
import com.funny.aitoy.core.utils.JsonX
import com.funny.data_saver.core.mutableDataSaverStateOf
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.core.JsonValue
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam
import com.openai.models.chat.completions.ChatCompletionToolMessageParam
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
import com.openai.models.chat.completions.ChatCompletionChunk
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

class ChatViewModel(
    private val bridgeVm: BridgeViewModel,
) : ViewModel() {
    val messages = mutableStateListOf<ChatMessage>()

    var input by mutableStateOf("")
    var streaming by mutableStateOf(false)
        private set
    var statusText by mutableStateOf("")
        private set
    var showSettings by mutableStateOf(false)

    var apiKey: String by mutableDataSaverStateOf(DataSaverUtils, "CHAT_OPENAI_API_KEY", "")
    var baseUrl: String by mutableDataSaverStateOf(DataSaverUtils, "CHAT_OPENAI_BASE_URL", "https://api.openai.com/v1")
    var model: String by mutableDataSaverStateOf(DataSaverUtils, "CHAT_OPENAI_MODEL", "gpt-4o-mini")
    var temperatureText: String by mutableDataSaverStateOf(DataSaverUtils, "CHAT_OPENAI_TEMPERATURE", "0.7")
    var extraParamsJson: String by mutableDataSaverStateOf(
        DataSaverUtils,
        "CHAT_OPENAI_EXTRA_PARAMS",
        "{\n  \"max_tokens\": 800,\n  \"parallel_tool_calls\": false\n}",
    )
    var systemPrompt: String by mutableDataSaverStateOf(
        DataSaverUtils,
        "CHAT_OPENAI_SYSTEM_PROMPT",
        "你是用户的设备助手。回答简洁、温和。需要控制设备时使用可用工具；优先选择低强度，单次持续不超过 15 秒。",
    )

    private val history = mutableListOf<ChatCompletionMessageParam>()
    private var runningJob: Job? = null
    private var activeClient: OpenAIClient? = null

    init {
        messages += ChatMessage(
            id = nextId(),
            role = ChatRole.Assistant,
            content = "连接设备后，可以直接告诉我想怎样调整。我会根据可用工具完成操作。",
        )
    }

    fun send() {
        val text = input.trim()
        if (text.isBlank() || streaming) return
        if (apiKey.isBlank()) {
            statusText = "请先填写 API Key"
            showSettings = true
            return
        }
        input = ""
        statusText = ""
        messages += ChatMessage(nextId(), ChatRole.User, text)
        history += userMessage(text)
        val assistantId = nextId()
        messages += ChatMessage(assistantId, ChatRole.Assistant, "")
        runningJob = viewModelScope.launch {
            streaming = true
            runCatching {
                val answer = withContext(Dispatchers.IO) {
                    runChatLoop(assistantId)
                }
                replaceAssistantText(assistantId, answer)
                statusText = ""
            }.onFailure { e ->
                Log.e(TAG, "Chat request failed: ${e::class.simpleName} — ${e.message}", e)
                val current = messages.firstOrNull { it.id == assistantId }?.content.orEmpty()
                val errorSuffix = "\n\n[错误: ${e.message ?: "请求未完成"}]"
                replaceAssistantText(assistantId, current.ifBlank { errorSuffix.trimStart() } + if (current.isNotBlank()) errorSuffix else "")
                statusText = e.message ?: "请求没有完成"
            }
            streaming = false
        }
    }

    fun stopStreaming() {
        runningJob?.cancel()
        runningJob = null
        streaming = false
        statusText = "已停止生成"
    }

    fun clearConversation() {
        runningJob?.cancel()
        history.clear()
        messages.clear()
        messages += ChatMessage(
            id = nextId(),
            role = ChatRole.Assistant,
            content = "对话已清空。你可以继续描述想要的动作。",
        )
        streaming = false
        statusText = ""
    }

    private suspend fun runChatLoop(assistantId: String): String {
        val client = OpenAIOkHttpClient.builder()
            .apiKey(apiKey.trim())
            .baseUrl(normalizeBaseUrl())
            .build()
        activeClient = client
        val paramsBuilder = baseParamsBuilder()
        val output = StringBuilder()
        repeat(MAX_TOOL_ROUNDS) { round ->
            withContext(Dispatchers.Main) { statusText = if (round == 0) "正在思考..." else "正在整理结果..." }
            val result = streamOnce(client, paramsBuilder, assistantId, output)
            if (result.toolCalls.isEmpty()) {
                history += assistantMessage(output.toString())
                return output.toString()
            }
            Log.d(TAG, "round=$round toolCalls=${result.toolCalls.map { "${it.name}(${it.arguments})" }}")
            // Build assistant history entry with tool calls so the model has context.
            // ChatCompletionMessageToolCall is a union type; use addToolCall(FunctionToolCall) directly.
            val assistantParamBuilder = ChatCompletionAssistantMessageParam.builder()
                .apply { if (output.isNotEmpty()) content(output.toString()) }
            result.toolCalls.forEach { call ->
                assistantParamBuilder.addToolCall(
                    ChatCompletionMessageFunctionToolCall.builder()
                        .id(call.id)
                        .type(JsonValue.from("function"))
                        .function(
                            ChatCompletionMessageFunctionToolCall.Function.builder()
                                .name(call.name)
                                .arguments(call.arguments)
                                .build()
                        )
                        .build()
                )
            }
            paramsBuilder.addMessage(
                ChatCompletionMessageParam.ofAssistant(assistantParamBuilder.build())
            )
            result.toolCalls.forEach { toolCall ->
                val toolResult = when (toolCall.name) {
                    GetToyStatus::class.java.simpleName -> bridgeVm.getToyStatusForChat()
                    SetToyVibration::class.java.simpleName -> {
                        val obj = JsonX.json.decodeFromString<kotlinx.serialization.json.JsonObject>(toolCall.arguments)
                        val intensity = obj["intensity"]?.jsonPrimitive?.intOrNull ?: 15
                        val mode = obj["mode"]?.jsonPrimitive?.intOrNull ?: 1
                        val durationSec = obj["durationSec"]?.jsonPrimitive?.intOrNull
                            ?: obj["duration_sec"]?.jsonPrimitive?.intOrNull ?: 6
                        bridgeVm.setToyVibrationForChat(intensity, mode, durationSec)
                    }
                    StopToy::class.java.simpleName -> bridgeVm.stopToyForChat(all = false)
                    StopAllToys::class.java.simpleName -> bridgeVm.stopToyForChat(all = true)
                    else -> ToyToolResult(false, "暂不支持这个操作。")
                }
                withContext(Dispatchers.Main) {
                    messages += ChatMessage(
                        id = nextId(),
                        role = ChatRole.Tool,
                        content = toolResult.message,
                        toolName = "设备工具",
                    )
                    statusText = "工具已完成"
                }
                paramsBuilder.addMessage(
                    ChatCompletionToolMessageParam.builder()
                        .toolCallId(toolCall.id)
                        .contentAsJson(toolResult)
                        .build(),
                )
            }
            currentCoroutineContext().ensureActive()
        }
        return output.toString().ifBlank { "已完成。" }
    }

    /** Manually accumulates tool-call deltas from chunks, bypassing ChatCompletionAccumulator
     *  which fails on providers (e.g. DeepSeek) that omit the final usage chunk. */
    private suspend fun streamOnce(
        client: OpenAIClient,
        paramsBuilder: ChatCompletionCreateParams.Builder,
        assistantId: String,
        output: StringBuilder,
    ): StreamResult {
        // index → mutable builder for each parallel tool call
        val toolBuilders = mutableMapOf<Int, ToolCallBuilder>()
        val coroutineContext = currentCoroutineContext()
        var chunkIndex = 0
        var finishReason: String? = null
        Log.d(TAG, "streamOnce: start streaming")
        client.chat().completions().createStreaming(paramsBuilder.build()).use { streamResponse ->
            val iter = streamResponse.stream().iterator()
            while (iter.hasNext()) {
                coroutineContext.ensureActive()
                val chunk = iter.next()
                chunk.choices().forEach { choice ->
                    choice.finishReason().ifPresent { finishReason = it.toString() }
                    val delta = choice.delta()
                    // Accumulate text content.
                    delta.content().ifPresent { part ->
                        if (part.isNotEmpty()) output.append(part)
                    }
                    // Accumulate tool call deltas.
                    delta.toolCalls().ifPresent { toolCallDeltas ->
                        toolCallDeltas.forEach { tc ->
                            val idx = tc.index().toInt()
                            val b = toolBuilders.getOrPut(idx) { ToolCallBuilder() }
                            tc.id().ifPresent { b.id = it }
                            tc.function().ifPresent { fn ->
                                fn.name().ifPresent { b.name += it }
                                fn.arguments().ifPresent { b.args.append(it) }
                            }
                        }
                    }
                }
                // Update UI on main thread after each chunk.
                if (output.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        replaceAssistantText(assistantId, output.toString())
                    }
                }
                chunkIndex++
            }
        }
        val toolCalls = toolBuilders.entries
            .sortedBy { it.key }
            .map { (_, b) -> AccumulatedToolCall(b.id, b.name, b.args.toString()) }
        Log.d(TAG, "streamOnce: ended after $chunkIndex chunks, text=${output.length}, toolCalls=${toolCalls.size}, finishReason=$finishReason")
        return StreamResult(output.toString(), toolCalls)
    }

    private fun baseParamsBuilder(): ChatCompletionCreateParams.Builder {
        val extra = parseExtraParams()
        return ChatCompletionCreateParams.builder()
            .model(model.trim().ifBlank { "gpt-4o-mini" })
            .messages(buildMessages())
            .temperature(temperatureText.toDoubleOrNull()?.coerceIn(0.0, 2.0) ?: 0.7)
            .apply {
                extra.maxTokens?.let { maxCompletionTokens(it.toLong()) }
                extra.topP?.let { topP(it) }
                extra.frequencyPenalty?.let { frequencyPenalty(it) }
                extra.presencePenalty?.let { presencePenalty(it) }
                extra.parallelToolCalls?.let { parallelToolCalls(it) }
                addTool(GetToyStatus::class.java)
                addTool(SetToyVibration::class.java)
                addTool(StopToy::class.java)
                addTool(StopAllToys::class.java)
                extra.additionalProperties.forEach { (key, value) ->
                    putAdditionalBodyProperty(key, JsonValue.from(value.toString()))
                }
            }
    }

    private fun buildMessages(): List<ChatCompletionMessageParam> = buildList {
        val prompt = systemPrompt.trim()
        if (prompt.isNotBlank()) add(systemMessage(prompt))
        addAll(history.takeLast(24))
    }

    private fun parseExtraParams(): ParsedExtraParams {
        val text = extraParamsJson.trim()
        if (text.isBlank()) return ParsedExtraParams()
        val json = JsonX.json.decodeFromString<JsonObject>(text)
        return ParsedExtraParams(
            maxTokens = json["maxTokens"]?.jsonPrimitive?.intOrNull
                ?: json["max_tokens"]?.jsonPrimitive?.intOrNull,
            topP = json["topP"]?.jsonPrimitive?.doubleOrNull
                ?: json["top_p"]?.jsonPrimitive?.doubleOrNull,
            frequencyPenalty = json["frequencyPenalty"]?.jsonPrimitive?.doubleOrNull
                ?: json["frequency_penalty"]?.jsonPrimitive?.doubleOrNull,
            presencePenalty = json["presencePenalty"]?.jsonPrimitive?.doubleOrNull
                ?: json["presence_penalty"]?.jsonPrimitive?.doubleOrNull,
            parallelToolCalls = json["parallelToolCalls"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                ?: json["parallel_tool_calls"]?.jsonPrimitive?.content?.toBooleanStrictOrNull(),
            additionalProperties = json.filterKeys { it !in KNOWN_EXTRA_KEYS },
        )
    }

    private fun normalizeBaseUrl(): String {
        val value = baseUrl.trim().trimEnd('/')
        require(value.startsWith("http://") || value.startsWith("https://")) {
            "Base URL 需要以 http 或 https 开头"
        }
        return value
    }

    private fun systemMessage(text: String): ChatCompletionMessageParam =
        ChatCompletionMessageParam.ofSystem(
            ChatCompletionSystemMessageParam.builder().content(text).build(),
        )

    private fun userMessage(text: String): ChatCompletionMessageParam =
        ChatCompletionMessageParam.ofUser(
            ChatCompletionUserMessageParam.builder().content(text).build(),
        )

    private fun assistantMessage(text: String): ChatCompletionMessageParam =
        ChatCompletionMessageParam.ofAssistant(
            ChatCompletionAssistantMessageParam.builder().content(text).build(),
        )

    private fun replaceAssistantText(id: String, content: String) {
        val index = messages.indexOfFirst { it.id == id }
        if (index >= 0) messages[index] = messages[index].copy(content = content)
    }

    private fun nextId(): String = UUID.randomUUID().toString()

    override fun onCleared() {
        runningJob?.cancel()
        activeClient = null
    }

    class GetToyStatus {
        @JsonPropertyDescription("固定填写 true。")
        @JvmField
        var current: Boolean = true
    }

    class SetToyVibration {
        @JsonPropertyDescription("强度百分比，范围 0 到 100。")
        @JvmField
        var intensity: Int = 15

        @JsonPropertyDescription("节奏编号，从 1 开始。")
        @JvmField
        var mode: Int = 1

        @JsonPropertyDescription("持续秒数，范围 1 到 15。")
        @JvmField
        var durationSec: Int = 6
    }

    class StopToy {
        @JsonPropertyDescription("固定填写 true。")
        @JvmField
        var now: Boolean = true
    }

    class StopAllToys {
        @JsonPropertyDescription("固定填写 true。")
        @JvmField
        var now: Boolean = true
    }

    private data class StreamResult(
        val text: String,
        val toolCalls: List<AccumulatedToolCall>,
    )

    private data class AccumulatedToolCall(val id: String, val name: String, val arguments: String)

    private class ToolCallBuilder {
        var id: String = ""
        var name: String = ""
        val args = StringBuilder()
    }

    private data class ParsedExtraParams(
        val maxTokens: Int? = null,
        val topP: Double? = null,
        val frequencyPenalty: Double? = null,
        val presencePenalty: Double? = null,
        val parallelToolCalls: Boolean? = null,
        val additionalProperties: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    )

    companion object {
        private const val TAG = "ChatViewModel"
        private const val MAX_TOOL_ROUNDS = 4
        private val KNOWN_EXTRA_KEYS = setOf(
            "maxTokens",
            "max_tokens",
            "topP",
            "top_p",
            "frequencyPenalty",
            "frequency_penalty",
            "presencePenalty",
            "presence_penalty",
            "parallelToolCalls",
            "parallel_tool_calls",
        )
    }
}
