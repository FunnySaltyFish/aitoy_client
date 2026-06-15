package com.funny.aitoy

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.funny.aitoy.ble.BleConnectionState
import com.funny.aitoy.ble.ScannedBleDevice
import kotlin.math.roundToInt

private val Background = Color(0xFF100D13)
private val SurfaceColor = Color(0xFF1C171F)
private val Accent = Color(0xFFE9A9C0)
private val AccentStrong = Color(0xFFF5C4D5)
private val TextPrimary = Color(0xFFF8EEF2)
private val TextSecondary = Color(0xFFCABCC3)
private val Danger = Color(0xFFFF6B7A)

@Composable
fun App() {
    val vm = viewModel { BridgeViewModel() }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color(0xFF3C1724),
            background = Background,
            surface = SurfaceColor,
            onBackground = TextPrimary,
            onSurface = TextPrimary,
            error = Danger,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Background) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { Header(vm.connectionState) }
                item { RelaySection(vm) }
                item {
                    ScanSection(
                        vm = vm,
                        onScan = vm::toggleScan,
                        onDisconnect = vm::disconnect,
                    )
                }
                val visibleDevices = if (vm.selectedAddress.isBlank()) {
                    vm.devices
                } else {
                    vm.devices.filter { it.address == vm.selectedAddress }
                }
                if (visibleDevices.isNotEmpty()) {
                    items(visibleDevices, key = { it.address }) { device ->
                        DeviceCard(
                            device = device,
                            selected = vm.selectedAddress == device.address,
                            onConnect = { vm.connect(device) },
                        )
                    }
                }
                item { ProtocolSection(vm) }
                item { TestSection(vm) }
                item { LogSection(vm) }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun RelaySection(vm: BridgeViewModel) {
    val clipboard = LocalClipboardManager.current
    val mcpConfig = """
        {
          "mcpServers": {
            "ai-toy-bridge": {
              "command": "python",
              "args": ["mcp_server.py"],
              "env": {
                "BRIDGE_URL": "${vm.serverUrl}",
                "USER_TOKEN": "${vm.userToken}"
              }
            }
          }
        }
    """.trimIndent()
    SectionCard(title = "AI 连接") {
        Text(
            text = "这个身份会长期保存在本机。AI 工具只需配置一次。",
            color = TextSecondary,
        )
        Spacer(Modifier.height(12.dp))
        ProtocolField("服务器地址", vm.serverUrl) { vm.serverUrl = it }
        ProtocolField("User Token", vm.userToken) { vm.userToken = it }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = vm::connectRelay) { Text("连接服务器") }
            if (vm.relayState != "未连接") {
                OutlinedButton(onClick = vm::disconnectRelay) { Text("断开") }
            }
            Text(vm.relayState, color = TextSecondary)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { clipboard.setText(AnnotatedString(mcpConfig)) },
        ) {
            Text("复制 MCP 配置")
        }
    }
}

@Composable
private fun Header(state: BleConnectionState) {
    Column(modifier = Modifier.padding(top = 18.dp)) {
        Text(
            text = "AI Toy Bridge",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "先完成一次本地连接与控制测试。",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(12.dp))
        StatusPill(state)
    }
}

@Composable
private fun StatusPill(state: BleConnectionState) {
    val ready = state == BleConnectionState.Ready
    Box(
        modifier = Modifier
            .background(
                color = if (ready) Color(0xFF264A3A) else Color(0xFF342B38),
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = state.label,
            color = if (ready) Color(0xFFB9F3D2) else TextSecondary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun ScanSection(
    vm: BridgeViewModel,
    onScan: () -> Unit,
    onDisconnect: () -> Unit,
) {
    SectionCard(title = "附近设备") {
        Text(
            text = "首次使用请允许查找附近设备，并确认手机蓝牙已开启。",
            color = TextSecondary,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onScan) {
                Text(if (vm.scanning) "停止扫描" else "开始扫描")
            }
            if (vm.connectionState != BleConnectionState.Idle) {
                OutlinedButton(onClick = onDisconnect) { Text("断开连接") }
            }
        }
        vm.errorMessage?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun DeviceCard(
    device: ScannedBleDevice,
    selected: Boolean,
    onConnect: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFF352532) else SurfaceColor,
        ),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "${device.address}  ·  ${device.rssi} dBm" +
                        if (device.connectable) "" else "  ·  仅广播",
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(10.dp))
            Button(onClick = onConnect, enabled = device.connectable) {
                Text(if (device.connectable) "连接" else "仅广播")
            }
        }
    }
}

@Composable
private fun ProtocolSection(vm: BridgeViewModel) {
    SectionCard(title = "设备协议") {
        Text(
            text = "填写设备说明中对应的服务和写入特征。常见 FFE0 协议已作为默认值。",
            color = TextSecondary,
        )
        Spacer(Modifier.height(12.dp))
        ProtocolField("Service UUID", vm.serviceUuid) { vm.serviceUuid = it }
        ProtocolField("Write UUID", vm.writeUuid) { vm.writeUuid = it }
        ProtocolField("Notify UUID（可不填）", vm.notifyUuid) { vm.notifyUuid = it }
        ProtocolField("启动指令", vm.commandTemplate) { vm.commandTemplate = it }
        ProtocolField("停止指令", vm.stopTemplate) { vm.stopTemplate = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = vm.writeWithResponse,
                onCheckedChange = { vm.writeWithResponse = it },
            )
            Text("等待设备确认写入")
        }
    }
}

@Composable
private fun ProtocolField(label: String, value: String, onValueChange: (String) -> Unit) {
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

@Composable
private fun TestSection(vm: BridgeViewModel) {
    SectionCard(title = "控制测试") {
        Text("模式 ${vm.mode}", fontFamily = FontFamily.Monospace)
        Slider(
            value = vm.mode.toFloat(),
            onValueChange = { vm.mode = it.roundToInt() },
            valueRange = 1f..8f,
            steps = 6,
        )
        Text("强度 ${vm.intensity}", fontFamily = FontFamily.Monospace)
        Slider(
            value = vm.intensity.toFloat(),
            onValueChange = { vm.intensity = it.roundToInt() },
            valueRange = 1f..5f,
            steps = 3,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = vm::sendTest,
                enabled = vm.connectionState == BleConnectionState.Ready,
            ) {
                Text("轻轻试一下")
            }
            Button(
                onClick = vm::stopDevice,
                enabled = vm.connectionState == BleConnectionState.Ready,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Danger,
                    contentColor = Color.White,
                ),
            ) {
                Text("立即停止")
            }
        }
    }
}

@Composable
private fun LogSection(vm: BridgeViewModel) {
    val listState = rememberLazyListState()
    LaunchedEffect(vm.logs.size) {
        if (vm.logs.isNotEmpty()) listState.scrollToItem(vm.logs.lastIndex)
    }
    SectionCard(title = "调试日志") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("最近 ${vm.logs.size} 条", color = TextSecondary)
            OutlinedButton(onClick = vm::clearLogs) { Text("清空") }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(Color(0xFF0B090C), RoundedCornerShape(12.dp))
                .padding(10.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (vm.logs.isEmpty()) {
                item {
                    Text(
                        "扫描、连接和写入过程会显示在这里。",
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            items(vm.logs) { line ->
                Text(
                    line,
                    color = Color(0xFFD8CDD2),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                color = AccentStrong,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFF3A303D))
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
