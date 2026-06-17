package com.funny.aitoy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.funny.aitoy.ble.BleConnectionState
import com.funny.aitoy.ble.ProtocolAttemptStatus
import com.funny.aitoy.ble.ScannedBleDevice
import com.funny.aitoy.chat.ChatScreen
import com.funny.aitoy.chat.ChatViewModel
import com.funny.aitoy.network.OkHttpUtils
import kotlin.math.roundToInt

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
fun App(initialImportLink: String? = null) {
    val vm = viewModel { BridgeViewModel() }
    val chatVm = viewModel { ChatViewModel(vm) }
    var selectedTab by remember { mutableIntStateOf(0) }
    LaunchedEffect(initialImportLink) {
        vm.importFromLink(initialImportLink)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.resumeRelay()
    }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Rose,
            onPrimary = Ink,
            background = Ink,
            surface = Velvet,
            onBackground = TextMain,
            onSurface = TextMain,
            error = Danger,
        ),
    ) {
        Scaffold(
            containerColor = Ink,
            bottomBar = {
                NavigationBar(containerColor = Velvet) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Outlined.SettingsRemote, null) },
                        label = { Text("设备") },
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Outlined.Chat, null) },
                        label = { Text("对话") },
                    )
                }
            },
        ) { padding ->
            Surface(color = Ink, modifier = Modifier
                .fillMaxSize()
                .padding(padding)) {
                if (selectedTab == 0) {
                    DeviceHome(vm)
                } else {
                    ChatScreen(vm = chatVm, bridgeVm = vm)
                }
            }
            UpdateDialog(vm)
        }
    }
}

@Composable
private fun DeviceHome(vm: BridgeViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true }
            .testTag("aitoy_root")
            .statusBarsPadding(),
    ) {
        Box(Modifier.padding(horizontal = 16.dp)) {
            DeviceHeader(vm)
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { CrashNotice(vm) }
            if (vm.updateState.forceUpdate) {
                item { Spacer(Modifier.height(8.dp)) }
            } else {
                if (vm.connectionState != BleConnectionState.Ready) {
                    item { ConnectFlow(vm) }
                } else {
                    item { ControlRoom(vm) }
                }
                item { AiCompanion(vm) }
                item { GuidePanel(vm) }
                item { AdvancedPanel(vm) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        DeviceRemarkDialog(vm)
    }
}

@Composable
private fun CrashNotice(vm: BridgeViewModel) {
    if (vm.crashNotice.isBlank()) return
    Panel(title = "已恢复", icon = Icons.Outlined.AutoAwesome) {
        Text(vm.crashNotice, color = TextMain, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = vm::dismissCrashNotice) {
            Text("知道了")
        }
    }
}

@Composable
private fun UpdateDialog(vm: BridgeViewModel) {
    val state = vm.updateState
    if (!state.updateAvailable && !state.forceUpdate) return
    var dismissedUpdateKey by remember { mutableStateOf("") }
    val updateKey = "${state.latestVersionName}|${state.apkUrl}|${state.downloadPageUrl}"
    if (!state.forceUpdate && !vm.updateDownloading && dismissedUpdateKey == updateKey) return
    AlertDialog(
        onDismissRequest = {
            if (!state.forceUpdate && !vm.updateDownloading) {
                dismissedUpdateKey = updateKey
            }
        },
        title = {
            Text(if (state.forceUpdate) "需要更新" else "发现新版本")
        },
        text = {
            Column {
                Text(
                    text = if (state.forceUpdate) {
                        "当前版本需要更新后继续使用。"
                    } else {
                        "有新版本可用，建议更新后获得更稳定的连接体验。"
                    },
                    color = TextMain,
                    fontWeight = FontWeight.Bold,
                )
                if (state.latestVersionName.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("最新版本 ${state.latestVersionName}", color = TextSoft)
                }
                if (state.fileSizeBytes > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text("安装包 ${formatBytes(state.fileSizeBytes)}", color = TextSoft)
                }
                if (state.updateLog.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(state.updateLog, color = TextSoft)
                }
                if (state.message.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(state.message, color = Honey)
                }
                if (vm.updateDownloading) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { vm.updateDownloadProgress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("正在下载 ${vm.updateDownloadProgress}%", color = TextSoft)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!state.forceUpdate && !vm.updateDownloading) {
                    TextButton(onClick = { dismissedUpdateKey = updateKey }) {
                        Text("稍后")
                    }
                }
                if (!vm.updateDownloading) {
                    OutlinedButton(
                        onClick = vm::openUpdateInBrowser,
                        enabled = state.apkUrl.isNotBlank() || state.downloadPageUrl.isNotBlank(),
                    ) {
                        Text("浏览器下载")
                    }
                }
                Button(
                    onClick = vm::downloadAndInstallUpdate,
                    enabled = state.apkUrl.isNotBlank() && !vm.updateDownloading,
                    colors = ButtonDefaults.buttonColors(containerColor = Rose, contentColor = Ink),
                ) {
                    Text(if (vm.updateDownloading) "下载中" else "下载并安装")
                }
            }
        },
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
    return "${bytes / 1024 / 1024} MB"
}

@Composable
private fun DeviceHeader(vm: BridgeViewModel) {
    val ready = vm.connectionState == BleConnectionState.Ready
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2D1E29), Color(0xFF1A131A)),
                ),
                RoundedCornerShape(20.dp),
            )
            .border(1.dp, Color(0xFF543949), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Favorite,
                contentDescription = null,
                tint = if (ready) Mint else Rose,
                modifier = Modifier
                    .background(Color(0x332A0F1A), CircleShape)
                    .padding(8.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "AI Toy",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "把小玩具交给你的 AI 伙伴",
                    color = TextSoft,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            StatusPill(vm.connectionState)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (ready) {
                if (vm.protocolStatus.verifiedControl) {
                    "已连接，可以开始使用。"
                } else {
                    "已连接。控制效果还需要试一下。"
                }
            } else {
                "先连接设备，下面会显示当前尝试到哪一步。"
            },
            color = TextMain,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (vm.busyHint.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(vm.busyHint, color = Honey)
        }
        if (!ready && vm.protocolAttemptStatus.title.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            ProtocolAttemptInline(vm.protocolAttemptStatus)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                modifier = Modifier.testTag("scan_toggle"),
                onClick = vm::toggleScan,
                colors = ButtonDefaults.buttonColors(containerColor = Rose, contentColor = Ink),
            ) {
                Icon(Icons.Outlined.BluetoothSearching, null)
                Spacer(Modifier.width(8.dp))
                Text(if (vm.scanning) "停止寻找" else "寻找设备")
            }
            if (vm.connectionState != BleConnectionState.Idle) {
                OutlinedButton(onClick = vm::disconnect) {
                    Text("断开")
                }
            }
        }
    }
}

@Composable
private fun StatusPill(state: BleConnectionState) {
    val ready = state == BleConnectionState.Ready
    Text(
        text = state.label,
        color = if (ready) Mint else TextSoft,
        modifier = Modifier
            .background(if (ready) Color(0x2238F2A1) else Color(0x22FFFFFF), RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 7.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun ConnectFlow(vm: BridgeViewModel) {
    Panel(title = "连接设备", icon = Icons.Outlined.BluetoothSearching) {
        FlowStep(
            index = "1",
            title = "让手机靠近小玩具",
            text = "保持设备开机，允许查找附近设备。",
        )
        FlowStep(
            index = "2",
            title = "优先自动识别",
            text = "能识别的设备会直接进入控制页；识别不了，再进入高级工具。",
        )
        Spacer(Modifier.height(8.dp))
        vm.errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Danger)
        }
        RecentDevices(vm)
        DeviceList(vm.devices, vm.selectedAddress, vm::connect)
    }
}

@Composable
private fun ProtocolAttemptInline(status: ProtocolAttemptStatus) {
    val accent = when {
        status.success -> Mint
        status.active -> Honey
        else -> Danger
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF221922))
            .border(1.dp, Color(0x3341323D), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(status.title, color = accent, fontWeight = FontWeight.SemiBold)
        if (status.message.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                status.message,
                color = TextSoft,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (status.active && status.total > 1) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { status.currentIndex.toFloat() / status.total.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = Honey,
                trackColor = Color(0x33FFFFFF),
            )
        }
    }
}

@Composable
private fun ProtocolAttemptCard(status: ProtocolAttemptStatus) {
    if (status.title.isBlank() && status.message.isBlank() && status.failedNames.isEmpty()) return
    val accent = when {
        status.success -> Mint
        status.active -> Honey
        else -> Danger
    }
    Spacer(Modifier.height(12.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF241B24))
            .border(1.dp, Color(0x3341323D), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text(
            text = status.title,
            color = accent,
            fontWeight = FontWeight.Bold,
        )
        if (status.message.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(status.message, color = TextSoft, style = MaterialTheme.typography.bodySmall)
        }
        if (status.active && status.total > 1) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { status.currentIndex.toFloat() / status.total.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = Honey,
                trackColor = Color(0x33FFFFFF),
            )
        }
        if (status.failedNames.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "未通过：" + status.failedNames.joinToString("、"),
                color = TextSoft,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RecentDevices(vm: BridgeViewModel) {
    if (vm.rememberedDevices.isEmpty()) return
    Spacer(Modifier.height(18.dp))
    Text("常用设备", color = Honey, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    vm.rememberedDevices.take(3).forEach { toy ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF241B24))
                .padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { vm.connectRemembered(toy) },
            ) {
                Text(toy.name, fontWeight = FontWeight.SemiBold)
                Text(toy.protocolName, color = TextSoft, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { vm.editRememberedDevice(toy) }) {
                Text("备注")
            }
            TextButton(onClick = { vm.deleteRememberedDevice(toy) }) {
                Text("删除")
            }
            TextButton(onClick = { vm.connectRemembered(toy) }) {
                Text("连接")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DeviceList(
    devices: List<ScannedBleDevice>,
    selectedAddress: String,
    onConnect: (ScannedBleDevice) -> Unit,
) {
    if (devices.isEmpty()) return
    Spacer(Modifier.height(14.dp))
    Text("附近发现", color = Honey, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    devices.forEach { device ->
        DeviceRow(
            device = device,
            selected = selectedAddress == device.address,
            onConnect = { onConnect(device) },
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DeviceRow(
    device: ScannedBleDevice,
    selected: Boolean,
    onConnect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) Color(0xFF3A2535) else Color(0xFF211A22),
                RoundedCornerShape(18.dp)
            )
            .border(1.dp, if (selected) Rose else Line, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                device.name,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${device.address}  ${device.rssi} dBm",
                color = TextSoft,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val hint = when {
                device.broadcastProtocolName.isNotBlank() -> "已识别：${device.broadcastProtocolName}"
                device.connectable -> "可连接"
                else -> "未识别广播"
            }
            Text(
                hint,
                color = TextSoft,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Button(
            modifier = Modifier.testTag("device_connect_${device.address.replace(":", "_")}"),
            onClick = onConnect,
            enabled = device.controllable,
            colors = ButtonDefaults.buttonColors(
                containerColor = RoseDeep,
                contentColor = Color.White
            ),
        ) {
            Text(
                when {
                    device.broadcastProtocolName.isNotBlank() -> "广播测试"
                    device.connectable -> "连接"
                    else -> "不可连接"
                }
            )
        }
    }
}

@Composable
private fun ControlRoom(vm: BridgeViewModel) {
    Panel(title = "轻柔控制", icon = Icons.Outlined.AutoAwesome) {
        Text(
            text = when {
                !vm.protocolStatus.controllable ->
                    "这个设备还需要补充指令。可以打开高级工具，导入别人分享的指令，或按教程采集数据。"

                !vm.protocolStatus.verifiedControl ->
                    "已连上设备，但控制效果还没确认。请从低强度开始试一下；如果没反应，可以在高级工具里调整或导入指令。"

                else ->
                    "从低强度开始测试，确认有反应后再慢慢调整。"
            },
            color = if (vm.protocolStatus.controllable) TextSoft else Danger,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        if (vm.protocolStatus.supportsMode) {
            Text(vm.currentModeLabel(), color = TextSoft)
            Slider(
                value = vm.mode.toFloat(),
                onValueChange = { vm.updateMode(it.roundToInt()) },
                modifier = Modifier.testTag("mode_slider"),
                valueRange = 1f..maxOf(1, vm.protocolStatus.modeMax).toFloat(),
                steps = (vm.protocolStatus.modeMax - 2).coerceAtLeast(0),
                enabled = vm.protocolStatus.controllable,
            )
        }
        Text("强度 ${vm.intensity}", color = TextSoft)
        Slider(
            value = vm.intensity.toFloat(),
            onValueChange = { vm.updateIntensity(it.roundToInt()) },
            modifier = Modifier.testTag("intensity_slider"),
            valueRange = 1f..maxOf(1, vm.protocolStatus.intensityMax).toFloat(),
            steps = (vm.protocolStatus.intensityMax - 2).coerceAtLeast(0),
            enabled = vm.protocolStatus.controllable,
        )
        if (vm.controlTrialStarted && !vm.currentDeviceSaved) {
            Spacer(Modifier.height(8.dp))
            Text("刚才的控制是否正常？", color = TextMain, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("control_confirm_works"),
                    onClick = vm::confirmCurrentDeviceWorks,
                    enabled = vm.protocolStatus.controllable,
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                ) {
                    Text("正常")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = vm::reportCurrentDeviceNotWorking,
                ) {
                    Text("没反应")
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("control_stop"),
                onClick = vm::stopDevice,
                enabled = vm.protocolStatus.controllable,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Danger,
                    contentColor = Color.White
                ),
            ) {
                Icon(Icons.Outlined.StopCircle, null)
                Spacer(Modifier.width(8.dp))
                Text("立即停止")
            }
        }
    }
}

@Composable
private fun DeviceRemarkDialog(vm: BridgeViewModel) {
    if (!vm.showDeviceRemarkDialog) return
    AlertDialog(
        onDismissRequest = vm::dismissDeviceRemarkDialog,
        title = { Text("保存到我的设备") },
        text = {
            Column {
                Text("给这个设备起个容易认的名字。")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = vm.deviceRemarkDraft,
                    onValueChange = { vm.deviceRemarkDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("设备备注") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = vm::saveDeviceRemark) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = vm::dismissDeviceRemarkDialog) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun AiCompanion(vm: BridgeViewModel) {
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
                "让 AI 工具控制已连接的默认设备。",
                color = TextSoft,
                modifier = Modifier.weight(1f),
            )
            InfoTip(
                title = "手机上线",
                text = "手机上线后，复制配置到支持 MCP 的 AI 工具。AI 只能控制当前默认设备，你可以随时在这里下线或立即停止。",
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
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
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = vm::openAppDownloadPage) {
            Icon(Icons.Outlined.Link, null)
            Spacer(Modifier.width(8.dp))
            Text("分享应用下载页")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = vm::openBatteryConnectionSettings) {
            Icon(Icons.Outlined.PowerSettingsNew, null)
            Spacer(Modifier.width(8.dp))
            Text("保持后台连接")
        }
        Text(
            text = "如果切到后台后经常掉线，请在系统设置里将本应用的电池使用方式设为不限制。",
            color = TextSoft,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun GuidePanel(vm: BridgeViewModel) {
    val clipboard = LocalClipboardManager.current
    Panel(title = "使用教程", icon = Icons.Outlined.Link) {
        Text("如果自动连接不成功，可以导入别人分享的指令，或按教程采集连接信息。", color = TextSoft)
        Spacer(Modifier.height(10.dp))
        Row {
            TextButton(onClick = { vm.showGuide = !vm.showGuide }) {
                Text(if (vm.showGuide) "收起教程摘要" else "查看教程摘要")
            }
        }
        AnimatedVisibility(vm.showGuide) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowStep("A", "自动连接", "先寻找设备并连接，应用会优先尝试内置协议。")
                FlowStep("B", "导入指令", "如果别人已经适配过同款设备，粘贴分享链接后重新连接。")
                FlowStep(
                    "C",
                    "采集信息",
                    "仍失败时，按群内教程采集设备连接信息，方便补充适配。"
                )
                OutlinedButton(
                    onClick = {
                        if (vm.communityCode.isBlank()) {
                            vm.fetchCommunityCode()
                        } else {
                            clipboard.setText(AnnotatedString(vm.communityCode))
                        }
                    },
                ) {
                    Icon(Icons.Outlined.ContentCopy, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (vm.communityCode.isBlank()) "加入内测群" else "复制群聊口令")
                }
                if (vm.communityMessage.isNotBlank()) {
                    Text(vm.communityMessage, color = Honey)
                }
            }
        }
    }
}

@Composable
private fun AdvancedPanel(vm: BridgeViewModel) {
    Panel(title = "高级工具", icon = Icons.Outlined.Tune) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("普通连接不需要改这里。", color = TextSoft, modifier = Modifier.weight(1f))
            InfoTip(
                title = "高级工具",
                text = "这里用于导入同款设备的指令，或在工作人员协助下补充适配信息。看不懂时保持默认即可。",
            )
        }
        TextButton(onClick = { vm.showAdvanced = !vm.showAdvanced }) {
            Text(if (vm.showAdvanced) "收起高级工具" else "打开高级工具")
        }
        AnimatedVisibility(vm.showAdvanced) {
            Column {
                TemplateLibrary(vm)
                TemplateEditor(vm)
                ShareTools(vm)
                LogSection(vm)
                DeveloperSettings(vm)
            }
        }
    }
}

@Composable
private fun TemplateLibrary(vm: BridgeViewModel) {
    Spacer(Modifier.height(18.dp))
    SoftTitle("模板库")
    Text(
        "如果自动连接失败，可以先选择同款或相近设备模板，再重新连接。",
        color = TextSoft,
    )
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
        Checkbox(
            checked = vm.manualControlEnabled,
            onCheckedChange = { vm.manualControlEnabled = it })
        Text("使用这组手动指令", color = TextSoft)
    }
}

@Composable
private fun ShareTools(vm: BridgeViewModel) {
    Spacer(Modifier.height(18.dp))
    SoftTitle("导入与分享")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = vm::shareCurrentTemplate) { Text("分享当前指令") }
    }
    if (vm.shareMessage.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        SelectionContainer {
            Text(vm.shareMessage, color = Honey, fontFamily = FontFamily.Monospace)
        }
    }
    Spacer(Modifier.height(10.dp))
    FormField("分享链接或口令", vm.importCode) { vm.importCode = it }
    OutlinedButton(onClick = vm::importSharedTemplate) { Text("导入指令") }
    if (vm.importMessage.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        SelectionContainer {
            Text(vm.importMessage, color = Honey)
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
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        items(vm.logs) { line ->
            Text(
                line,
                color = Color(0xFFE0D1D7),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun FlowStep(index: String, title: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .background(Color(0x33F0A8BE), CircleShape)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(index, color = Rose, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = TextMain, fontWeight = FontWeight.Bold)
            Text(text, color = TextSoft)
        }
    }
}

@Composable
private fun Panel(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Velvet, RoundedCornerShape(18.dp))
            .border(1.dp, Line, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Rose)
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                color = TextMain,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun SoftTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Honey, fontWeight = FontWeight.Bold, modifier = modifier)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun InfoTip(title: String, text: String) {
    var open by remember { mutableIntStateOf(0) }
    IconButton(onClick = { open += 1 }) {
        Icon(Icons.Outlined.HelpOutline, contentDescription = title, tint = Honey)
    }
    if (open > 0) {
        AlertDialog(
            onDismissRequest = { open = 0 },
            confirmButton = {
                TextButton(onClick = { open = 0 }) { Text("知道了") }
            },
            title = { Text(title) },
            text = { Text(text) },
        )
    }
}

@Composable
private fun FormField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        label = { Text(label) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
    )
}
