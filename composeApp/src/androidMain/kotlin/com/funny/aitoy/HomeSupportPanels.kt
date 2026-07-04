package com.funny.aitoy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.funny.aitoy.core.kmp.ToastType
import com.funny.aitoy.core.kmp.toast
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
        Text(
            "保存设备后，手机会在设备就绪时自动上线；也可以在这里手动管理。",
            color = TextSoft,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        CompanionAction(
            icon = Icons.Outlined.PowerSettingsNew,
            title = "手机上线",
            text = "让 AI 工具看到这台手机，并控制当前可用设备。",
            trailing = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = vm::connectRelay) {
                        Text("上线给 AI")
                    }
                    if (vm.relayState != "未连接") {
                        OutlinedButton(onClick = vm::disconnectRelay) {
                            Text("停止上线")
                        }
                    }
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        CompanionAction(
            icon = Icons.Outlined.ContentCopy,
            title = "MCP 配置",
            text = "复制后粘贴到 AI 工具的连接设置中；同一份配置可以长期使用。",
            trailing = {
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(mcpConfig))
                        toast("MCP 配置已复制，请参考文档进一步配置", ToastType.Success)
                    },
                ) {
                    Icon(Icons.Outlined.ContentCopy, null)
                    Spacer(Modifier.width(8.dp))
                    Text("复制配置")
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "当前状态：${vm.relayState}",
            color = if (vm.relayState == "已在线") Mint else TextSoft,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CompanionAction(
    icon: ImageVector,
    title: String,
    text: String,
    trailing: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF211A22), RoundedCornerShape(16.dp))
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(13.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = Honey)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextMain, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(
                    text,
                    color = TextSoft,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        trailing()
    }
}

@Composable
internal fun HelpSettingsPanel(vm: BridgeViewModel) {
    Panel(
        title = "帮助与设置",
        icon = Icons.Outlined.Link,
        onTitleClick = vm::onHelpSettingsTitleClick,
    ) {
        BackgroundStabilityGuide(vm)
        Spacer(Modifier.height(16.dp))
        SoftTitle("常用入口")
        SettingsAction(
            icon = Icons.Outlined.Link,
            title = "教程文档",
            text = "查看连接步骤、支持范围和常见问题。",
            onClick = vm::openTutorialDocument,
        )
        SettingsAction(
            icon = Icons.Outlined.Chat,
            title = "交流群",
            text = "设备不兼容或连接不稳定时，可以提交信息。",
            onClick = vm::openCommunityGroup,
        )
        SettingsAction(
            icon = Icons.Outlined.ContentCopy,
            title = "下载页",
            text = "把安装入口发给另一台手机。",
            onClick = vm::openAppDownloadPage,
        )
        TextButton(onClick = { vm.showGuide = !vm.showGuide }) {
            Text(if (vm.showGuide) "收起使用流程" else "查看使用流程")
        }
        AnimatedVisibility(vm.showGuide) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowStep("1", "连接设备", "点击寻找设备，选择要连接的小玩具。")
                FlowStep("2", "确认可用", "从低强度开始测试，确认正常后保存到我的设备。")
                FlowStep("3", "交给 AI", "已保存设备就绪后，手机会自动上线。")
            }
        }
        DeveloperSettings(vm)
    }
}

@Composable
private fun BackgroundStabilityGuide(vm: BridgeViewModel) {
    SoftTitle("后台稳定运行")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StabilityAction(
            icon = Icons.Outlined.BatterySaver,
            title = "后台无限制",
            text = "允许应用在锁屏和切到其他应用后继续运行。",
            actionText = "去设置",
            onClick = vm::openBatteryConnectionSettings,
        )
        StabilityAction(
            icon = Icons.Outlined.Lock,
            title = "应用锁定",
            text = "打开最近任务，锁定 AI Toy，避免被系统清理。",
            actionText = "手动完成",
            onClick = null,
        )
        StabilityAction(
            icon = Icons.Outlined.NotificationsActive,
            title = "通知权限",
            text = "允许连接通知显示，后台连接状态会更稳定。",
            actionText = "通知设置",
            onClick = vm::openNotificationSettings,
        )
    }
}

@Composable
private fun StabilityAction(
    icon: ImageVector,
    title: String,
    text: String,
    actionText: String,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF211A22), RoundedCornerShape(16.dp))
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Rose)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextMain, fontWeight = FontWeight.Bold)
            Text(
                text,
                color = TextSoft,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.width(10.dp))
        if (onClick == null) {
            Text(
                text = actionText,
                color = Honey,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else {
            OutlinedButton(onClick = onClick) {
                Text(actionText)
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
private fun DeveloperSettings(vm: BridgeViewModel) {
    if (!vm.developerMode && vm.baseUrlMessage.isBlank()) return
    Spacer(Modifier.height(18.dp))
    if (!vm.developerMode) {
        Text(vm.baseUrlMessage, color = Honey)
        return
    }
    SoftTitle("开发者设置")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Settings, contentDescription = null, tint = Honey)
        Spacer(Modifier.width(8.dp))
        Text(
            "服务地址会影响更新检查、手机上线和 AI 连接。修改后建议重新上线。",
            color = TextSoft,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = vm::useProductionBaseUrl) { Text("线上地址") }
        OutlinedButton(onClick = vm::useLocalBaseUrl) { Text("本地地址") }
    }
    Spacer(Modifier.height(10.dp))
    FormField("服务地址", vm.baseUrlDraft) { vm.baseUrlDraft = it }
    Button(
        onClick = vm::saveBaseUrl,
        colors = ButtonDefaults.buttonColors(containerColor = Rose, contentColor = Ink),
    ) {
        Text("保存地址")
    }
    Spacer(Modifier.height(14.dp))
    FormField("用户 Token", vm.userTokenDraft) { vm.userTokenDraft = it }
    Button(
        onClick = vm::saveUserToken,
        colors = ButtonDefaults.buttonColors(containerColor = Rose, contentColor = Ink),
    ) {
        Text("保存 Token")
    }
    if (vm.baseUrlMessage.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(vm.baseUrlMessage, color = Honey)
    }
    Spacer(Modifier.height(6.dp))
    Text("当前：${vm.serverUrl}", color = TextSoft, fontFamily = FontFamily.Monospace)
    Text("当前 Token：${vm.userToken}", color = TextSoft, fontFamily = FontFamily.Monospace)
}
