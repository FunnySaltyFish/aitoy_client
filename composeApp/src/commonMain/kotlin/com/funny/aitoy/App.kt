package com.funny.aitoy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.funny.aitoy.ble.BleBroadcastProtocolRegistry
import com.funny.aitoy.ble.BleConnectionState
import com.funny.aitoy.ble.BleProtocolStatus
import com.funny.aitoy.ble.ProtocolAttemptStatus
import com.funny.aitoy.ble.ScannedBleDevice
import com.funny.aitoy.ble.SvakomV2HeatFunctionCode
import com.funny.aitoy.ble.ToyControlStyle
import com.funny.aitoy.ble.independentFunctionCode
import com.funny.aitoy.ble.independentFunctionModeMax
import com.funny.aitoy.core.navigation.Navigator
import com.funny.aitoy.core.navigation.NavigatorProvider
import com.funny.aitoy.core.navigation.rememberNavigator
import com.funny.aitoy.model.ManagedToy
import com.funny.aitoy.model.RememberedToy
import com.funny.aitoy.model.ToyRuntimeState
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
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

@Serializable
private sealed interface AiToyRoute : NavKey {
    @Serializable
    data object Device : AiToyRoute

    @Serializable
    data object Guide : AiToyRoute

    @Serializable
    data object Account : AiToyRoute
}

@Composable
fun App() {
    val vm = viewModel { BridgeViewModel() }
    val navigator = rememberNavigator(AiToyRoute.Device)
    val currentRoute = navigator.currentRoute
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
        NavigatorProvider(navigator) {
            Scaffold(
                containerColor = Ink,
                bottomBar = {
                    NavigationBar(containerColor = Velvet) {
                        NavigationItem(
                            navigator = navigator,
                            currentRoute = currentRoute,
                            route = AiToyRoute.Device,
                            icon = Icons.Outlined.SettingsRemote,
                            label = "设备",
                        )
                        NavigationItem(
                            navigator = navigator,
                            currentRoute = currentRoute,
                            route = AiToyRoute.Guide,
                            icon = Icons.Outlined.Link,
                            label = "教程",
                        )
                        NavigationItem(
                            navigator = navigator,
                            currentRoute = currentRoute,
                            route = AiToyRoute.Account,
                            icon = Icons.Outlined.AutoAwesome,
                            label = "我的",
                        )
                    }
                },
            ) { padding ->
                Surface(color = Ink, modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)) {
                    when (currentRoute) {
                        AiToyRoute.Device -> DeviceHome(vm)
                        AiToyRoute.Guide -> McpGuideScreen(vm)
                        AiToyRoute.Account -> AccountScreen(vm)
                    }
                }
                UpdateDialog(vm)
            }
        }
    }
}

@Composable
private fun NavigationItem(
    modifier: Modifier = Modifier,
    navigator: Navigator,
    currentRoute: AiToyRoute,
    route: AiToyRoute,
    icon: ImageVector,
    label: String,
) {
    val selected = currentRoute == route
    Column(
        modifier = modifier
            .clickable {
                if (!selected) navigator.replaceTop(route)
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) Rose else TextSoft,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            color = if (selected) Rose else TextSoft,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun RowScope.NavigationItem(
    navigator: Navigator,
    currentRoute: AiToyRoute,
    route: AiToyRoute,
    icon: ImageVector,
    label: String,
) {
    NavigationItem(
        modifier = Modifier.weight(1f),
        navigator = navigator,
        currentRoute = currentRoute,
        route = route,
        icon = icon,
        label = label,
    )
}

private val Navigator.currentRoute: AiToyRoute
    get() = backStack.lastOrNull() as? AiToyRoute ?: AiToyRoute.Device

@Composable
private fun McpGuideScreen(vm: BridgeViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Link,
                contentDescription = null,
                tint = Rose,
                modifier = Modifier
                    .background(Color(0x332A0F1A), CircleShape)
                    .padding(12.dp),
            )
            Text(
                text = "请前往任意支持 MCP 的软件使用，可查看如下教程",
                color = TextMain,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = vm::openTutorialDocument,
                colors = ButtonDefaults.buttonColors(containerColor = Rose, contentColor = Ink),
            ) {
                Icon(Icons.Outlined.Link, null)
                Spacer(Modifier.width(8.dp))
                Text("查看教程")
            }
            Text(
                text = "为保证连接稳定，请将此应用的后台电池设置为无限制，在最近任务中锁定应用，并打开通知权限。",
                color = TextSoft,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
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

private data class HeaderStatus(
    val text: String,
    val color: Color,
)

@Composable
private fun DeviceHeader(vm: BridgeViewModel) {
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
                tint = if (vm.connectionState == BleConnectionState.Ready) Mint else Rose,
                modifier = Modifier
                    .background(Color(0x332A0F1A), CircleShape)
                    .padding(8.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "AI Toy",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.width(8.dp))
                    VersionBadge(BridgePlatform.appVersionName)
                }
                Text(
                    "把小玩具交给你的 AI 伙伴",
                    color = TextSoft,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            StatusPill(vm.connectionState)
        }
        Spacer(Modifier.height(10.dp))
        HeaderStatusLine(headerStatus(vm))
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
private fun VersionBadge(versionName: String) {
    if (versionName.isBlank()) return
    Text(
        text = "v$versionName",
        color = Honey,
        modifier = Modifier
            .background(Honey.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

private fun headerStatus(vm: BridgeViewModel): HeaderStatus {
    val attempt = vm.protocolAttemptStatus
    val operationalHint = vm.busyHint.takeIf {
        it.isNotBlank() &&
            !it.startsWith("正在为你连接") &&
            !it.contains("连接不上")
    }.orEmpty()
    return when {
        vm.connectionState == BleConnectionState.Connecting ->
            HeaderStatus(vm.busyHint.ifBlank { "正在连接设备，请保持手机靠近。" }, Honey)
        vm.connectionState == BleConnectionState.Discovering || attempt.active ->
            HeaderStatus(attempt.message.ifBlank { attempt.title.ifBlank { "正在确认设备能力。" } }, Honey)
        vm.connectionState == BleConnectionState.Error ->
            HeaderStatus(vm.busyHint.ifBlank { attempt.message.ifBlank { "设备连接不上，请确认已开机并靠近手机。" } }, Danger)
        vm.connectionState == BleConnectionState.Disconnecting ->
            HeaderStatus("正在断开当前设备。", Honey)
        vm.connectionState == BleConnectionState.Ready && operationalHint.isNotBlank() ->
            HeaderStatus(operationalHint, if (operationalHint.contains("失败")) Danger else Mint)
        vm.connectionState == BleConnectionState.Ready && vm.protocolStatus.verifiedControl ->
            HeaderStatus("已连接，可以开始使用。", Mint)
        vm.connectionState == BleConnectionState.Ready ->
            HeaderStatus("已连接，可以按当前设备能力控制。", Mint)
        vm.scanning ->
            HeaderStatus("正在寻找附近设备，选择你的设备连接。", Honey)
        vm.busyHint.isNotBlank() ->
            HeaderStatus(vm.busyHint, Honey)
        else ->
            HeaderStatus("选择一台设备连接，已保存设备可直接连接。", TextSoft)
    }
}

@Composable
private fun HeaderStatusLine(status: HeaderStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(status.color.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .border(1.dp, status.color.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(status.color, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = status.text,
            color = TextMain,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
private fun BatteryPill(percent: Int) {
    Text(
        text = "电量 ${percent.coerceIn(1, 100)}%",
        color = Mint,
        modifier = Modifier
            .background(Mint.copy(alpha = 0.12f), RoundedCornerShape(50))
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
        CachitoQuickList(vm)
        DeviceList(vm)
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
    val remembered = vm.rememberedDevices.firstOrNull { it.address == toy.address }
        ?: RememberedToy(
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
            toy.batteryPercent?.let {
                Spacer(Modifier.width(8.dp))
                BatteryPill(it)
            }
            Spacer(Modifier.width(8.dp))
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
            }
            TextButton(onClick = { vm.deleteManagedDevice(toy.address) }) {
                Text("删除")
            }
        }
    }
}

@Composable
private fun CachitoQuickList(vm: BridgeViewModel) {
    val devices = vm.cachitoQuickDevices
    if (devices.isEmpty()) return
    Spacer(Modifier.height(14.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF211A22))
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text("Cachito 专属入口", color = Honey, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "如果附近列表一直找不到，可以直接选择型号。",
            color = TextSoft,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(10.dp))
        devices.forEach { device ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B151D), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        device.name,
                        color = TextMain,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "可直接尝试控制",
                        color = TextSoft,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                val runtimeState = vm.runtimeStateForAddress(device.address)
                if (runtimeState != ToyRuntimeState.Offline) {
                    DeviceStatePill(runtimeState)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { vm.connect(device) },
                    enabled = device.controllable,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoseDeep,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        when (runtimeState) {
                            ToyRuntimeState.Connected -> "控制"
                            ToyRuntimeState.Connecting -> "连接中"
                            else -> "连接"
                        }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DeviceList(vm: BridgeViewModel) {
    val devices = vm.devices
    if (devices.isEmpty()) return
    val expanded = vm.nearbyDevicesExpanded
    Spacer(Modifier.height(14.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF211A22))
            .border(1.dp, Line, RoundedCornerShape(16.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { vm.toggleNearbyDevicesExpanded() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("附近发现", color = Honey, fontWeight = FontWeight.Bold)
                Text(
                    "${devices.size} 台设备${if (expanded) "" else "，点击展开"}",
                    color = TextSoft,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = TextSoft,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) {
                devices.forEach { device ->
                    DeviceRow(
                        device = device,
                        selected = device.address == vm.selectedAddress &&
                            (
                                vm.connectionState == BleConnectionState.Connecting ||
                                    vm.connectionState == BleConnectionState.Discovering ||
                                    vm.connectionState == BleConnectionState.Ready ||
                                    vm.connectionState == BleConnectionState.Disconnecting
                                ),
                        runtimeState = vm.runtimeStateForAddress(device.address),
                        onConnect = { vm.connect(device) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: ScannedBleDevice,
    selected: Boolean,
    runtimeState: ToyRuntimeState?,
    onConnect: () -> Unit,
) {
    val isUnconfirmedCachitoBroadcast = device.broadcastProtocolName.isBlank() &&
        BleBroadcastProtocolRegistry.isUnconfirmedCachitoBroadcastDevice(device)
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
                isUnconfirmedCachitoBroadcast -> "发现可能兼容的设备，型号待确认"
                device.controllable && device.connectable -> "可连接"
                device.controllable -> "可尝试识别"
                else -> "暂不支持连接"
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
                    device.controllable && device.connectable -> "连接"
                    device.controllable -> "尝试识别"
                    isUnconfirmedCachitoBroadcast -> "尝试识别"
                    else -> "暂不支持"
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
    val batteryPercent = if (address.isBlank()) null else vm.batteryPercentForAddress(address)
    Panel(title = toy?.name ?: "遥控器", icon = Icons.Outlined.AutoAwesome) {
        batteryPercent?.let {
            BatteryPill(it)
            Spacer(Modifier.height(10.dp))
        }
        Text(
            text = when {
                !status.controllable -> "还没有可用的控制指令。"
                status.controlStyle == ToyControlStyle.ExclusivePatternOrIntensity ->
                    "节奏和强度一次只能选择一种。"
                status.controlStyle == ToyControlStyle.DualIntensityOnly ->
                    "分别调节两个部位。"
                status.controlStyle == ToyControlStyle.PatternAndDualIntensity ->
                    "选择节奏，也可以分别调节两个部位。"
                status.controlStyle == ToyControlStyle.CombinedPatternAndIntensity ->
                    "选择节奏并调节强度。"
                status.controlStyle == ToyControlStyle.IndependentFunctions ->
                    "按当前设备支持的功能分别控制。"
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
            ToyControlStyle.DualIntensityOnly -> DualIntensityControl(vm, address, status)
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
            ToyControlStyle.PatternAndDualIntensity -> {
                PatternSelector(vm, address, status, targetMode)
                Spacer(Modifier.height(14.dp))
                DualIntensityControl(vm, address, status)
            }
            ToyControlStyle.IndependentFunctions -> MultiFunctionControl(vm, address, status)
            ToyControlStyle.PatternOnly -> PatternSelector(vm, address, status, targetMode)
            ToyControlStyle.IntensityOnly -> IntensityControl(vm, address, status, targetIntensity)
        }
        if (address == vm.selectedAddress && vm.controlTrialStarted && !vm.currentDeviceSaved) {
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF211A22))
                    .border(1.dp, Line, RoundedCornerShape(16.dp))
                    .padding(12.dp),
            ) {
                Text("设备有反应吗？", color = TextMain, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ControlFeedbackChoice(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("control_confirm_works"),
                        icon = Icons.Outlined.CheckCircle,
                        title = "能控制",
                        description = "保存到我的设备，后续可直接连接。",
                        color = Mint,
                        enabled = vm.protocolStatus.controllable,
                        onClick = vm::confirmCurrentDeviceWorks,
                    )
                    ControlFeedbackChoice(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Autorenew,
                        title = "没反应",
                        description = "继续尝试其他设备类型，直到没有可选项。",
                        color = Rose,
                        enabled = true,
                        onClick = vm::reportCurrentDeviceNotWorking,
                    )
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
private fun ControlFeedbackChoice(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    description: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (enabled) color else TextSoft
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(contentColor.copy(alpha = 0.10f))
            .border(1.dp, contentColor.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                title,
                color = TextMain,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            description,
            color = TextSoft,
            style = MaterialTheme.typography.bodySmall,
        )
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
    Spacer(Modifier.height(6.dp))
    Text(
        if (status.controlStyle == ToyControlStyle.ExclusivePatternOrIntensity) {
            "${status.intensityLabel}会切换到单独控制，选择${status.modeLabel}或点击立即停止即可退出。"
        } else {
            "${status.intensityLabel}会保持当前设置，调为 0 或点击立即停止即可停下。"
        },
        color = TextSoft,
        style = MaterialTheme.typography.bodySmall,
    )
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
private fun DualIntensityControl(
    vm: BridgeViewModel,
    address: String,
    status: BleProtocolStatus,
) {
    val names = status.channelNames.ifEmpty { listOf("内侧", "外侧") }
    val values = listOf(
        if (address.isBlank()) vm.intensity else vm.intensityForAddress(address),
        if (address.isBlank()) vm.secondaryIntensity else vm.secondaryIntensityForAddress(address),
    )
    val maxIntensity = status.intensityMax.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        names.take(2).forEachIndexed { index, name ->
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, color = TextMain, fontWeight = FontWeight.SemiBold)
                    Text("${values[index]}/$maxIntensity", color = Rose, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = values[index].toFloat(),
                    onValueChange = {
                        vm.updateChannelIntensity(address, index, it.roundToInt())
                    },
                    modifier = Modifier.testTag("channel_${index + 1}_intensity_slider"),
                    valueRange = 0f..maxIntensity.toFloat(),
                    steps = (maxIntensity - 1).coerceAtLeast(0),
                    enabled = status.controllable,
                )
            }
        }
    }
}

@Composable
private fun MultiFunctionControl(
    vm: BridgeViewModel,
    address: String,
    status: BleProtocolStatus,
) {
    val motionFeatures = status.features.filterNot { it.type == "heat" }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        motionFeatures.forEach { feature ->
            MultiFunctionFeatureControl(
                vm = vm,
                address = address,
                status = status,
                title = feature.label.ifBlank { feature.type },
                functionCode = status.independentFunctionCode(feature),
                modeMax = status.independentFunctionModeMax(feature),
                maxIntensity = feature.max.coerceAtLeast(1),
            )
        }
        if (status.features.any { it.type == "heat" }) {
            var enabled by remember(address, status.id) { mutableStateOf(false) }
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("svakom_plus_heat"),
                onClick = {
                    enabled = !enabled
                    vm.updateIndependentFunction(
                        address = address,
                        functionCode = SvakomV2HeatFunctionCode,
                        mode = 1,
                        intensity = if (enabled) 1 else 0,
                    )
                },
                enabled = status.controllable,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (enabled) Honey else VelvetLight,
                    contentColor = if (enabled) Ink else TextMain,
                ),
            ) {
                Text(if (enabled) "关闭加热" else "开启加热")
            }
        }
    }
}

@Composable
private fun MultiFunctionFeatureControl(
    vm: BridgeViewModel,
    address: String,
    status: BleProtocolStatus,
    title: String,
    functionCode: Int,
    modeMax: Int,
    maxIntensity: Int,
) {
    var selectedMode by remember(address, status.id, functionCode) { mutableIntStateOf(0) }
    var intensity by remember(address, status.id, functionCode) { mutableIntStateOf(0) }
    // 伸缩、拍打没有强度滑杆：官方只按模式驱动、强度固定，UI 上点模式即启动、再点即停。
    val strengthAdjustable = maxIntensity > 1

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = TextMain, fontWeight = FontWeight.SemiBold)
            Text(
                if (strengthAdjustable) "$intensity/$maxIntensity"
                else if (selectedMode > 0) "模式 $selectedMode" else "已停止",
                color = Rose,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(10.dp))
        (1..modeMax.coerceAtLeast(1)).chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { index ->
                    val selected = selectedMode == index
                    Button(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("svakom_plus_${functionCode}_mode_$index"),
                        onClick = {
                            if (strengthAdjustable) {
                                selectedMode = index
                                if (intensity <= 0) intensity = maxIntensity
                                vm.updateIndependentFunction(address, functionCode, selectedMode, intensity)
                            } else {
                                // 模式驱动：再次点击当前模式即停止；否则切换到该模式并以固定强度启动。
                                if (selected) {
                                    selectedMode = 0
                                    intensity = 0
                                } else {
                                    selectedMode = index
                                    intensity = 1
                                }
                                vm.updateIndependentFunction(address, functionCode, selectedMode, intensity)
                            }
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
            Spacer(Modifier.height(8.dp))
        }
        if (strengthAdjustable) {
            Slider(
                value = intensity.toFloat(),
                onValueChange = {
                    intensity = it.roundToInt()
                    if (intensity > 0 && selectedMode <= 0) selectedMode = 1
                    vm.updateIndependentFunction(address, functionCode, selectedMode, intensity)
                },
                modifier = Modifier.testTag("svakom_plus_${functionCode}_intensity"),
                valueRange = 0f..maxIntensity.toFloat(),
                steps = (maxIntensity - 1).coerceAtLeast(0),
                enabled = status.controllable,
            )
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
    onTitleClick: (() -> Unit)? = null,
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
                style = MaterialTheme.typography.titleLarge,
                modifier = if (onTitleClick == null) {
                    Modifier
                } else {
                    Modifier.clickable { onTitleClick() }
                },
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
