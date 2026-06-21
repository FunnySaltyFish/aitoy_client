package com.funny.aitoy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.funny.aitoy.ble.BleConnectionState
import com.funny.aitoy.network.OkHttpUtils

@Composable
internal fun AiCompanion(vm: BridgeViewModel) {
    val clipboard = LocalClipboardManager.current
    val mcpUrl = OkHttpUtils.externalUrl("mcp", vm.serverUrl)
    val mcpConfig = """
        {
          "mcpServers": {
            "ai-toy": {
              "url": "$mcpUrl",
              "headers": {
                "X-User-Token": "${vm.userToken}"
              }
            }
          }
        }
    """.trimIndent()
    Panel(title = "AI 伙伴", icon = Icons.Outlined.WifiTethering) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "连接并保存设备后，手机会自动上线。你也可以在这里手动管理在线状态。",
                color = TextSoft,
                modifier = Modifier.weight(1f),
            )
            InfoTip(
                title = "手机上线",
                text = "上线后，AI 工具可以控制当前默认设备。你可以随时下线或在控制页立即停止。",
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = vm::connectRelay) { Text("手机上线") }
            if (vm.relayState != "未连接") {
                OutlinedButton(onClick = vm::disconnectRelay) { Text("下线") }
            }
            Text(vm.relayState, color = if (vm.relayState == "已在线") Mint else TextSoft)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            enabled = vm.relayState == "已在线",
            onClick = { clipboard.setText(AnnotatedString(mcpConfig)) },
        ) {
            Icon(Icons.Outlined.ContentCopy, null)
            Spacer(Modifier.width(8.dp))
            Text("复制 MCP 配置")
        }
    }
}

@Composable
internal fun HelpSettingsPanel(vm: BridgeViewModel) {
    Panel(title = "帮助与设置", icon = Icons.Outlined.Link) {
        Text(
            "教程、加群、下载和后台连接都在这里。",
            color = TextSoft,
        )
        Spacer(Modifier.height(10.dp))
        SettingsAction(
            icon = Icons.Outlined.Link,
            title = "打开教程文档",
            text = "查看连接、适配和常见问题。",
            onClick = vm::openTutorialDocument,
        )
        SettingsAction(
            icon = Icons.Outlined.Chat,
            title = "加入交流群",
            text = "设备不兼容时，可以在群里提交信息。",
            onClick = vm::openCommunityGroup,
        )
        SettingsAction(
            icon = Icons.Outlined.ContentCopy,
            title = "分享应用下载页",
            text = "把安装入口发给另一台手机。",
            onClick = vm::openAppDownloadPage,
        )
        SettingsAction(
            icon = Icons.Outlined.PowerSettingsNew,
            title = "保持后台连接",
            text = "允许后台运行，并建议在最近任务中锁定应用。",
            onClick = vm::openBatteryConnectionSettings,
        )
        TextButton(onClick = { vm.showGuide = !vm.showGuide }) {
            Text(if (vm.showGuide) "收起使用流程" else "查看使用流程")
        }
        AnimatedVisibility(vm.showGuide) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowStep("A", "连接设备", "先寻找设备并连接，应用会优先尝试自动识别。")
                FlowStep("B", "确认可用", "从低强度开始，正常后保存到我的设备。")
                FlowStep("C", "自动上线", "已保存设备连接成功后，手机会自动上线给 AI 使用。")
            }
        }
    }
}

@Composable
private fun SettingsAction(
    icon: ImageVector,
    title: String,
    text: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(
                text,
                color = TextSoft,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun AdvancedPanel(vm: BridgeViewModel) {
    Panel(title = "分析与适配", icon = Icons.Outlined.Tune) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "自动连接没反应时，从这里导入指令、重新连接，再保存可用设备。",
                color = TextSoft,
                modifier = Modifier.weight(1f),
            )
            InfoTip(
                title = "分析与适配",
                text = "这里用于导入同款设备的指令，或在工作人员协助下补充适配信息。能正常连接时不用修改。",
            )
        }
        Spacer(Modifier.height(10.dp))
        AnalysisStatus(vm)
        TextButton(onClick = { vm.showAdvanced = !vm.showAdvanced }) {
            Text(if (vm.showAdvanced) "收起分析工具" else "打开分析工具")
        }
        AnimatedVisibility(vm.showAdvanced) {
            Column {
                AnalysisFlow(vm)
                ShareTools(vm)
                TemplateLibrary(vm)
                TemplateEditor(vm)
                LogSection(vm)
                DeveloperSettings(vm)
            }
        }
    }
}

@Composable
private fun AnalysisStatus(vm: BridgeViewModel) {
    val connected = vm.connectionState == BleConnectionState.Ready
    val title = when {
        vm.manualControlEnabled -> "当前会使用手动指令"
        connected -> "当前使用自动识别"
        else -> "先寻找并连接设备"
    }
    val detail = when {
        vm.manualControlEnabled && connected -> "改动指令后，需要重新连接才会生效。"
        vm.manualControlEnabled -> "选择设备后，会按这组指令控制。"
        connected -> "如果控制没反应，可以导入或选择同款指令。"
        else -> "自动识别失败后，再回到这里导入指令。"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF211A22), RoundedCornerShape(14.dp))
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Text(title, color = if (vm.manualControlEnabled) Honey else TextMain, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(detail, color = TextSoft, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        if (vm.manualControlEnabled) {
            Button(
                onClick = vm::reconnectWithCurrentTemplate,
                colors = ButtonDefaults.buttonColors(containerColor = Rose, contentColor = Ink),
            ) {
                Text(if (connected) "重新连接" else "选择设备")
            }
        } else {
            OutlinedButton(onClick = { if (!vm.scanning) vm.toggleScan() }) {
                Text(if (vm.scanning) "正在寻找设备" else "寻找设备")
            }
        }
    }
}

@Composable
private fun AnalysisFlow(vm: BridgeViewModel) {
    Spacer(Modifier.height(12.dp))
    SoftTitle("推荐流程")
    FlowStep("1", "导入或选择指令", "有分享链接就先导入；没有链接，再从模板库选择相近设备。")
    Spacer(Modifier.height(8.dp))
    FlowStep("2", "重新连接", "连接后从低强度开始，确认设备是否有反应。")
    Spacer(Modifier.height(8.dp))
    FlowStep("3", "保存可用设备", "确认正常后保存备注，下次连接会自动上线。")
    if (vm.importMessage.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        Text(vm.importMessage, color = Honey)
    }
}

@Composable
private fun TemplateLibrary(vm: BridgeViewModel) {
    Spacer(Modifier.height(18.dp))
    SoftTitle("模板库")
    Text("如果自动连接失败，可以先选择同款或相近设备模板，再重新连接。", color = TextSoft)
    TextButton(onClick = { vm.showTemplateLibrary = !vm.showTemplateLibrary }) {
        Text(if (vm.showTemplateLibrary) "收起模板" else "选择模板")
    }
    AnimatedVisibility(vm.showTemplateLibrary) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            vm.protocolPresets.forEach { preset ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF211A22), RoundedCornerShape(16.dp))
                        .border(1.dp, Line, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                ) {
                    Text(preset.name, fontWeight = FontWeight.Bold, color = TextMain)
                    Spacer(Modifier.height(4.dp))
                    Text(preset.description, color = TextSoft)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { vm.applyPreset(preset) }) {
                        Text("使用这个模板")
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateEditor(vm: BridgeViewModel) {
    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("手动指令", color = Honey, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        InfoTip(
            title = "手动指令",
            text = "仅在工作人员或群友提供指令时填写。填好后开启“使用这组手动指令”，再重新连接设备。",
        )
    }
    Spacer(Modifier.height(8.dp))
    FormField("设备服务编号", vm.serviceUuid) { vm.serviceUuid = it }
    FormField("写入通道编号", vm.writeUuid) { vm.writeUuid = it }
    FormField("通知通道编号", vm.notifyUuid) { vm.notifyUuid = it }
    FormField("启动指令", vm.commandTemplate) { vm.commandTemplate = it }
    FormField("停止指令", vm.stopTemplate) { vm.stopTemplate = it }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = vm.writeWithResponse, onCheckedChange = { vm.writeWithResponse = it })
        Text("等待设备确认写入", color = TextSoft)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = vm.manualControlEnabled, onCheckedChange = { vm.manualControlEnabled = it })
        Text("使用这组手动指令", color = TextSoft)
    }
}

@Composable
private fun ShareTools(vm: BridgeViewModel) {
    Spacer(Modifier.height(18.dp))
    SoftTitle("导入与分享")
    FormField("分享链接或口令", vm.importCode) { vm.importCode = it }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = vm::importSharedTemplate) { Text("导入指令") }
        OutlinedButton(onClick = vm::shareCurrentTemplate) { Text("分享当前指令") }
    }
    if (vm.shareMessage.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        SelectionContainer {
            Text(vm.shareMessage, color = Honey, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun DeveloperSettings(vm: BridgeViewModel) {
    if (!vm.developerMode && vm.baseUrlMessage.isBlank()) return
    Spacer(Modifier.height(18.dp))
    if (!vm.developerMode) {
        Text(vm.baseUrlMessage, color = Honey)
        return
    }
    SoftTitle("开发者设置")
    Text("服务地址会影响更新检查、分享导入和手机上线。新请求会立即使用新地址；手机已经上线时，请重新上线。", color = TextSoft)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = vm::useProductionBaseUrl) { Text("线上地址") }
        OutlinedButton(onClick = vm::useLocalBaseUrl) { Text("本地地址") }
    }
    Spacer(Modifier.height(10.dp))
    FormField("服务地址", vm.baseUrlDraft) { vm.baseUrlDraft = it }
    Button(onClick = vm::saveBaseUrl) { Text("保存地址") }
    Spacer(Modifier.height(14.dp))
    FormField("用户 Token", vm.userTokenDraft) { vm.userTokenDraft = it }
    Button(onClick = vm::saveUserToken) { Text("保存 Token") }
    if (vm.baseUrlMessage.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(vm.baseUrlMessage, color = Honey)
    }
    Spacer(Modifier.height(6.dp))
    Text("当前：${vm.serverUrl}", color = TextSoft, fontFamily = FontFamily.Monospace)
    Text("当前 Token：${vm.userToken}", color = TextSoft, fontFamily = FontFamily.Monospace)
}

@Composable
private fun LogSection(vm: BridgeViewModel) {
    val listState = rememberLazyListState()
    LaunchedEffect(vm.logs.size) {
        if (vm.logs.isNotEmpty()) listState.scrollToItem(vm.logs.lastIndex)
    }
    Spacer(Modifier.height(18.dp))
    SoftTitle("调试日志", modifier = Modifier.clickable { vm.onDebugLogTitleClick() })
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text("最近 ${vm.logs.size} 条", color = TextSoft)
        TextButton(onClick = vm::clearLogs) { Text("清空") }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("protocol_logs")
            .height(240.dp)
            .background(Color(0xFF0B090C), RoundedCornerShape(14.dp))
            .padding(10.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (vm.logs.isEmpty()) {
            item {
                Text(
                    "连接过程会显示在这里。",
                    color = TextSoft,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        items(vm.logs) { line ->
            Text(
                line,
                color = Color(0xFFE0D1D7),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
