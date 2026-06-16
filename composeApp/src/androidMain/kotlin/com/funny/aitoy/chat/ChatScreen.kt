package com.funny.aitoy.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.funny.aitoy.BridgeViewModel
import com.mikepenz.markdown.m3.Markdown

private val Ink = Color(0xFF110D12)
private val Velvet = Color(0xFF1B151B)
private val VelvetLight = Color(0xFF2A202A)
private val Rose = Color(0xFFF0A8BE)
private val RoseDeep = Color(0xFFE56F98)
private val Honey = Color(0xFFFFD5A3)
private val Mint = Color(0xFF9BE7C7)
private val TextMain = Color(0xFFFFF4F7)
private val TextSoft = Color(0xFFD7C2CA)
private val Line = Color(0xFF41323D)
private val Danger = Color(0xFFFF5E76)

@Composable
fun ChatScreen(
    vm: ChatViewModel,
    bridgeVm: BridgeViewModel,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.scrollToItem(vm.messages.lastIndex)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        ChatTopBar(vm)
        AnimatedVisibility(vm.showSettings) {
            ChatSettings(vm)
        }
        ToolList(bridgeVm)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(vm.messages, key = { it.id }) { message ->
                MessageBubble(message)
            }
        }
        if (vm.statusText.isNotBlank()) {
            Text(
                text = vm.statusText,
                color = Honey,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        ChatInput(vm)
    }
}

@Composable
private fun ChatTopBar(vm: ChatViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("对话控制", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text("使用你的模型和内置设备工具", color = TextSoft)
        }
        IconButton(onClick = { vm.showSettings = !vm.showSettings }) {
            Icon(Icons.Outlined.Settings, contentDescription = "设置", tint = Rose)
        }
        IconButton(onClick = vm::clearConversation) {
            Icon(Icons.Outlined.Delete, contentDescription = "清空", tint = TextSoft)
        }
    }
}

@Composable
private fun ChatSettings(vm: ChatViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 10.dp)
            .background(Velvet, RoundedCornerShape(22.dp))
            .border(1.dp, Line, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("模型设置", color = Honey, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = vm.apiKey,
            onValueChange = { vm.apiKey = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API Key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedTextField(
            value = vm.baseUrl,
            onValueChange = { vm.baseUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Base URL") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = vm.model,
                onValueChange = { vm.model = it },
                modifier = Modifier.weight(1f),
                label = { Text("模型") },
                singleLine = true,
            )
            OutlinedTextField(
                value = vm.temperatureText,
                onValueChange = { vm.temperatureText = it },
                modifier = Modifier.width(112.dp),
                label = { Text("温度") },
                singleLine = true,
            )
        }
        OutlinedTextField(
            value = vm.extraParamsJson,
            onValueChange = { vm.extraParamsJson = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(116.dp),
            label = { Text("额外参数 JSON") },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
        OutlinedTextField(
            value = vm.systemPrompt,
            onValueChange = { vm.systemPrompt = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp),
            label = { Text("助手指令") },
        )
    }
}

@Composable
private fun ToolList(bridgeVm: BridgeViewModel) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .animateContentSize()
            .background(Color(0xFF211A22), RoundedCornerShape(18.dp))
            .border(1.dp, Line, RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("可用工具", color = Honey, fontWeight = FontWeight.Bold)
                if (!expanded) {
                    Text(
                        "${bridgeVm.builtInTools.size} 个工具",
                        color = TextSoft,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起工具" else "展开工具",
                    tint = Rose,
                )
            }
        }
        AnimatedVisibility(expanded) {
            Column {
                Spacer(Modifier.height(8.dp))
                bridgeVm.builtInTools.forEach { tool ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            tool.title,
                            color = TextMain,
                            modifier = Modifier.width(92.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            tool.description,
                            color = TextSoft,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == ChatRole.User
    val background = when (message.role) {
        ChatRole.User -> Color(0xFF3A2535)
        ChatRole.Tool -> Color(0xFF1C2A25)
        ChatRole.Assistant -> VelvetLight
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.86f else 0.94f)
                .background(background, RoundedCornerShape(18.dp))
                .border(
                    1.dp,
                    if (message.role == ChatRole.Tool) Color(0xFF356852) else Line,
                    RoundedCornerShape(18.dp)
                )
                .padding(14.dp),
        ) {
            Text(
                text = when (message.role) {
                    ChatRole.User -> "你"
                    ChatRole.Assistant -> "助手"
                    ChatRole.Tool -> message.toolName ?: "工具"
                },
                color = if (message.role == ChatRole.Tool) Mint else Rose,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(6.dp))
            if (message.role == ChatRole.Assistant) {
                Markdown(message.content.ifBlank { " " })
            } else {
                Text(message.content, color = TextMain)
            }
        }
    }
}

@Composable
private fun ChatInput(vm: ChatViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink)
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = vm.input,
            onValueChange = { vm.input = it },
            modifier = Modifier.weight(1f),
            label = { Text("输入消息") },
            minLines = 1,
            maxLines = 4,
        )
        Spacer(Modifier.width(10.dp))
        if (vm.streaming) {
            Button(
                onClick = vm::stopStreaming,
                colors = ButtonDefaults.buttonColors(containerColor = Danger, contentColor = Color.White),
            ) {
                Icon(Icons.Outlined.StopCircle, null)
            }
        } else {
            Button(
                onClick = vm::send,
                colors = ButtonDefaults.buttonColors(containerColor = RoseDeep, contentColor = Color.White),
            ) {
                Icon(Icons.Outlined.Send, null)
            }
        }
    }
}
