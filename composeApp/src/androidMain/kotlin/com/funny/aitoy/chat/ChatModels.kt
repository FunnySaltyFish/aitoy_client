package com.funny.aitoy.chat

import kotlinx.serialization.Serializable

enum class ChatRole(val apiName: String) {
    User("user"),
    Assistant("assistant"),
    Tool("tool"),
}

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val content: String,
    val toolName: String? = null,
    val toolCallId: String? = null,
)

data class ToyToolResult(
    val ok: Boolean,
    val message: String,
)

data class ToyTool(
    val name: String,
    val title: String,
    val description: String,
)

@Serializable
data class ChatExtraParams(
    val maxTokens: Int? = null,
    val topP: Double? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val parallelToolCalls: Boolean? = null,
)
