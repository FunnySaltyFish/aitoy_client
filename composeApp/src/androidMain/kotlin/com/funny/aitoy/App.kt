package com.funny.aitoy

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.funny.aitoy.ble.BleConnectionState
import com.funny.aitoy.ble.BleProtocolStatus
import com.funny.aitoy.ble.ProtocolAttemptStatus
import com.funny.aitoy.ble.ScannedBleDevice
import com.funny.aitoy.ble.ToyControlStyle
import com.funny.aitoy.chat.ChatScreen
import com.funny.aitoy.chat.ChatViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal val Ink = Color(0xFF110D12)
internal val Velvet = Color(0xFF1B151B)
internal val VelvetLight = Color(0xFF2A202A)
internal val Rose = Color(0xFFF0A8BE)
internal val RoseDeep = Color(0xFFE56F98)
internal val Honey = Color(0xFFFFD5A3)
internal val Mint = Color(0xFF9BE7C7)
internal val TextMain = Color(0xFFFFF4F7)
internal val TextSoft = Color(0xFFD7C2CA)
internal val Line = Color(0xFF41323D)
internal val Danger = Color(0xFFFF5E76)

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
                item { RemotePager(vm) }
                item { ConnectFlow(vm) }
                item { AiCompanion(vm) }
                item { HelpSettingsPanel(vm) }
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
                    "已连接，可以按当前设备能力控制。"
                }
            } else {
                "选择一台设备连接，已连接的设备可以随时切换控制。"
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
                    Text("断开当前")
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
private fun DeviceStatePill(state: ToyRuntimeState) {
    val color = when (state) {
        ToyRuntimeState.Connected -> Mint
        ToyRuntimeState.Connecting -> Honey
        ToyRuntimeState.Failed -> Danger
        ToyRuntimeState.Offline -> TextSoft
    }
    Text(
        text = state.label,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

@Composable
private fun RemotePager(vm: BridgeViewModel) {
    val connectedToys = vm.managedToys
        .filter { it.runtimeState == ToyRuntimeState.Connected }
        .sortedWith(
            compareByDescending<ManagedToy> { it.saved }
                .thenBy { it.name }
                .thenBy { it.address }
        )
    if (connectedToys.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { connectedToys.size })
    val scope = rememberCoroutineScope()
    val addressKey = connectedToys.joinToString("|") { it.address }
    val selectedPage = connectedToys.indexOfFirst { it.address == vm.selectedAddress }
        .takeIf { it >= 0 }
        ?: 0

    LaunchedEffect(addressKey, vm.selectedAddress) {
        if (pagerState.currentPage != selectedPage) {
            pagerState.animateScrollToPage(selectedPage)
        }
    }
    LaunchedEffect(pagerState, addressKey) {
        snapshotFlow { pagerState.settledPage }
            .collect { page ->
                connectedToys.getOrNull(page)
                    ?.address
                    ?.takeIf { it != vm.selectedAddress }
                    ?.let(vm::selectDevice)
            }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        ScrollableTabRow(
            selectedTabIndex = selectedPage,
            containerColor = Ink,
            contentColor = Rose,
            edgePadding = 8.dp,
        ) {
            connectedToys.forEachIndexed { index, toy ->
                Tab(
                    selected = index == selectedPage,
                    onClick = {
                        vm.selectDevice(toy.address)
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                    text = {
                        Text(
                            toy.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            key = { page -> connectedToys[page].address },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 360.dp),
        ) { page ->
            val toy = connectedToys[page]
            Box(Modifier.padding(12.dp)) {
                ControlRoom(vm, toy)
            }
        }
    }
}

@Composable
private fun ConnectFlow(vm: BridgeViewModel) {
    Panel(title = "设备列表", icon = Icons.Outlined.BluetoothSearching) {
        Text(
            "可以继续连接更多设备；已连接的设备会出现在上方遥控器里。",
            color = TextSoft,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        vm.errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Danger)
        }
        ManagedDevices(vm)
        DeviceList(vm.devices, vm.highlightedDeviceAddress, vm.toyRuntimeStates, vm::connect)
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
private fun ManagedDevices(vm: BridgeViewModel) {
    if (vm.managedToys.isEmpty()) return
    Spacer(Modifier.height(18.dp))
    Text("我的设备", color = Honey, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    vm.managedToys.forEach { toy ->
        ManagedDeviceRow(vm, toy)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ManagedDeviceRow(vm: BridgeViewModel, toy: ManagedToy) {
    val remembered = RememberedToy(
        name = toy.name,
        address = toy.address,
        protocolName = toy.protocolName,
        lastSeenAt = 0L,
    )
    val connected = toy.runtimeState == ToyRuntimeState.Connected
    val current = toy.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (current) Color(0xFF2C2F2A) else if (connected) Color(0xFF23362E) else Color(0xFF241B24))
            .border(1.dp, if (current) Honey else if (connected) Color(0x6638F2A1) else Line, RoundedCornerShape(16.dp))
            .clickable { if (connected) vm.selectDevice(toy.address) else vm.connectRemembered(remembered) }
            .padding(13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                toy.name,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            DeviceStatePill(toy.runtimeState)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            toy.protocolName,
            color = TextSoft,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            toy.address,
            color = TextSoft,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { if (connected) vm.selectDevice(toy.address) else vm.connectRemembered(remembered) },
                colors = ButtonDefaults.buttonColors(containerColor = RoseDeep, contentColor = Color.White),
            ) {
                Text(if (current) "控制中" else if (connected) "控制" else "连接")
            }
            if (connected) {
                OutlinedButton(onClick = { vm.stopDevice(toy.address) }) {
                    Text("停止")
                }
            }
            Spacer(Modifier.weight(1f))
            if (toy.saved) {
                TextButton(onClick = { vm.editRememberedDevice(remembered) }) {
                    Text("备注")
                }
                TextButton(onClick = { vm.deleteRememberedDevice(remembered) }) {
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun DeviceList(
    devices: List<ScannedBleDevice>,
    selectedAddress: String,
    states: Map<String, ToyRuntimeState>,
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
            runtimeState = states[device.address],
            onConnect = { onConnect(device) },
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DeviceRow(
    device: ScannedBleDevice,
    selected: Boolean,
    runtimeState: ToyRuntimeState?,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    device.name,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                runtimeState?.let { DeviceStatePill(it) }
            }
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
                else -> "可尝试连接"
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
                    runtimeState == ToyRuntimeState.Connected -> "控制"
                    runtimeState == ToyRuntimeState.Connecting -> "连接中"
                    device.broadcastProtocolName.isNotBlank() -> "连接"
                    device.connectable -> "连接"
                    else -> "尝试连接"
                }
            )
        }
    }
}

@Composable
private fun ControlRoom(vm: BridgeViewModel, toy: ManagedToy? = null) {
    val address = toy?.address ?: vm.selectedAddress
    val status = if (address.isBlank()) vm.protocolStatus else vm.protocolStatusForAddress(address)
    val targetMode = if (address.isBlank()) vm.mode else vm.modeForAddress(address)
    val targetIntensity = if (address.isBlank()) vm.intensity else vm.intensityForAddress(address)
    Panel(title = toy?.name ?: "遥控器", icon = Icons.Outlined.AutoAwesome) {
        Text(
            text = when {
                !status.controllable -> "还没有可用的控制指令。"
                status.controlStyle == ToyControlStyle.ExclusivePatternOrIntensity ->
                    "节奏和强度是两套独立控制。"
                status.controlStyle == ToyControlStyle.CombinedPatternAndIntensity ->
                    "选择节奏并调节强度。"
                status.controlStyle == ToyControlStyle.PatternOnly ->
                    "选择一个预设节奏。"
                else -> "调节强度，设备会保持当前状态。"
            },
            color = if (status.controllable) TextSoft else Danger,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (address == vm.selectedAddress && vm.protocolAttemptStatus.title.isNotBlank()) {
            ProtocolAttemptCard(vm.protocolAttemptStatus)
        }
        Spacer(Modifier.height(16.dp))
        when (status.controlStyle) {
            ToyControlStyle.ExclusivePatternOrIntensity -> {
                PatternSelector(vm, address, status, targetMode)
                Spacer(Modifier.height(18.dp))
                IntensityControl(vm, address, status, targetIntensity)
            }
            ToyControlStyle.CombinedPatternAndIntensity -> {
                PatternSelector(vm, address, status, targetMode)
                Spacer(Modifier.height(14.dp))
                IntensityControl(vm, address, status, targetIntensity)
            }
            ToyControlStyle.PatternOnly -> PatternSelector(vm, address, status, targetMode)
            ToyControlStyle.IntensityOnly -> IntensityControl(vm, address, status, targetIntensity)
        }
        if (address == vm.selectedAddress && vm.controlTrialStarted && !vm.currentDeviceSaved) {
            Spacer(Modifier.height(8.dp))
            Text("保存这台设备？", color = TextMain, fontWeight = FontWeight.SemiBold)
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
                onClick = {
                    if (address.isBlank()) vm.stopDevice() else vm.stopDevice(address)
                },
                enabled = status.controllable,
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
private fun PatternSelector(
    vm: BridgeViewModel,
    address: String,
    status: BleProtocolStatus,
    targetMode: Int,
) {
    val maxMode = status.modeMax.coerceAtLeast(1)
    Text(status.modeLabel, color = TextMain, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..maxMode).chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { index ->
                    val selected = targetMode == index
                    Button(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mode_$index"),
                        onClick = {
                            if (address.isBlank()) vm.updateMode(index) else vm.updateMode(address, index)
                        },
                        enabled = status.controllable,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) RoseDeep else VelvetLight,
                            contentColor = if (selected) Color.White else TextMain,
                        ),
                    ) {
                        Text(index.toString())
                    }
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        if (address.isBlank()) vm.currentModeLabel() else vm.modeLabelForAddress(address),
        color = TextSoft,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun IntensityControl(
    vm: BridgeViewModel,
    address: String,
    status: BleProtocolStatus,
    targetIntensity: Int,
) {
    val maxIntensity = status.intensityMax.coerceAtLeast(1)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(status.intensityLabel, color = TextMain, fontWeight = FontWeight.SemiBold)
        Text("$targetIntensity/$maxIntensity", color = Rose, fontWeight = FontWeight.Bold)
    }
    Slider(
        value = targetIntensity.toFloat(),
        onValueChange = {
            if (address.isBlank()) vm.updateIntensity(it.roundToInt()) else vm.updateIntensity(address, it.roundToInt())
        },
        modifier = Modifier.testTag("intensity_slider"),
        valueRange = 0f..maxIntensity.toFloat(),
        steps = (maxIntensity - 1).coerceAtLeast(0),
        enabled = status.controllable,
    )
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
internal fun FlowStep(index: String, title: String, text: String) {
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
internal fun Panel(
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
internal fun SoftTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Honey, fontWeight = FontWeight.Bold, modifier = modifier)
    Spacer(Modifier.height(8.dp))
}

@Composable
internal fun InfoTip(title: String, text: String) {
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
internal fun FormField(label: String, value: String, onValueChange: (String) -> Unit) {
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
