package com.funny.aitoy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.funny.aitoy.network.api.service.Product
import kotlin.math.roundToInt

@Composable
internal fun AccountScreen(vm: BridgeViewModel) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { AccountHeader(vm) }
        item { UsagePanel(vm) }
        item { ProductPanel(vm) }
        item { ProfilePanel(vm) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun AccountHeader(vm: BridgeViewModel) {
    val user = vm.accountUser
    Panel(title = "我的账号", icon = Icons.Outlined.AccountCircle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(Rose.copy(alpha = 0.18f), CircleShape)
                    .border(1.dp, Rose.copy(alpha = 0.38f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = displayInitial(user.displayName.ifBlank { user.username }),
                    color = Rose,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = user.displayName.ifBlank { user.username.ifBlank { "AI Toy 用户" } },
                    color = TextMain,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = user.entitlement.membershipName.ifBlank { "免费版" },
                    color = Honey,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            OutlinedButton(onClick = vm::refreshAccount, enabled = !vm.accountLoading) {
                Icon(Icons.Outlined.Refresh, null)
                Spacer(Modifier.width(6.dp))
                Text("刷新")
            }
        }
        if (vm.accountLoading) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Rose)
        }
        if (vm.accountMessage.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(vm.accountMessage, color = Honey)
        }
    }
}

@Composable
private fun UsagePanel(vm: BridgeViewModel) {
    val ent = vm.accountUser.entitlement
    val total = ent.aiQuotaSecondsMonthly.coerceAtLeast(1)
    val used = ent.aiQuotaSecondsUsed.coerceAtLeast(0)
    val remaining = ent.aiQuotaSecondsRemaining.coerceAtLeast(0)
    val progress = (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    Panel(title = "AI 控制时长", icon = Icons.Outlined.AutoAwesome) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = formatMinutes(remaining),
                color = TextMain,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.width(8.dp))
            Text("本月剩余", color = TextSoft, modifier = Modifier.padding(bottom = 5.dp))
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = if (remaining > 0) Mint else Danger,
            trackColor = Color(0x33FFFFFF),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "本月额度 ${formatMinutes(total)}，已用 ${formatMinutes(used)}。连接待机和停止控制不消耗。",
            color = TextSoft,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ProductPanel(vm: BridgeViewModel) {
    Panel(title = "会员", icon = Icons.Outlined.ShoppingCart) {
        PayTypeSelector(vm)
        Spacer(Modifier.height(12.dp))
        if (vm.memberProducts.isEmpty()) {
            Text("正在加载会员方案。", color = TextSoft)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = vm::refreshProducts) {
                Text("重新加载")
            }
        } else {
            vm.memberProducts.forEach { product ->
                ProductRow(product, vm)
                Spacer(Modifier.height(10.dp))
            }
        }
        if (vm.pendingOrderNo.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = vm::refreshPendingOrder, enabled = !vm.accountLoading) {
                Icon(Icons.Outlined.CheckCircle, null)
                Spacer(Modifier.width(6.dp))
                Text("我已完成支付")
            }
        }
    }
}

@Composable
private fun PayTypeSelector(vm: BridgeViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PayTypeChip("支付宝", selected = vm.selectedPayType == "alipay") {
            vm.selectedPayType = "alipay"
        }
        PayTypeChip("微信", selected = vm.selectedPayType == "wxpay") {
            vm.selectedPayType = "wxpay"
        }
    }
}

@Composable
private fun PayTypeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) Ink else TextMain,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Honey else VelvetLight)
            .border(1.dp, if (selected) Honey else Line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun ProductRow(product: Product, vm: BridgeViewModel) {
    val border = if (product.highlight) Rose else Line
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (product.highlight) Color(0xFF2A202A) else Color(0xFF241B24))
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(product.name, color = TextMain, fontWeight = FontWeight.Bold)
                    if (product.highlight) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "推荐",
                            color = Ink,
                            modifier = Modifier
                                .background(Rose, RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Text(
                    text = "${formatMinutes(product.aiControlSeconds)} AI 控制时长 / 月",
                    color = Honey,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (product.description.isNotBlank()) {
                    Text(product.description, color = TextSoft, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = { vm.startMembershipPurchase(product.id) },
                enabled = !vm.accountLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Rose, contentColor = Ink),
            ) {
                Text("¥${formatPrice(product.priceCents)}")
            }
        }
    }
}

@Composable
private fun ProfilePanel(vm: BridgeViewModel) {
    Panel(title = "资料", icon = Icons.Outlined.AccountCircle) {
        OutlinedTextField(
            value = vm.profileNameDraft,
            onValueChange = { vm.profileNameDraft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("昵称") },
            singleLine = true,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = vm.profileAvatarDraft,
            onValueChange = { vm.profileAvatarDraft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("头像地址") },
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = vm::saveProfile,
            enabled = !vm.accountLoading,
            colors = ButtonDefaults.buttonColors(containerColor = Rose, contentColor = Ink),
        ) {
            Text("保存资料")
        }
        Spacer(Modifier.height(12.dp))
        Text("账号编号", color = TextSoft, style = MaterialTheme.typography.labelMedium)
        Text(
            vm.accountUser.uid.ifBlank { "同步后显示" },
            color = TextSoft,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun displayInitial(value: String): String =
    value.trim().firstOrNull()?.toString()?.uppercase().orEmpty().ifBlank { "A" }

private fun formatMinutes(seconds: Int): String {
    val minutes = (seconds / 60f).roundToInt().coerceAtLeast(0)
    return "${minutes} 分钟"
}

private fun formatPrice(priceCents: Int): String =
    if (priceCents % 100 == 0) {
        (priceCents / 100).toString()
    } else {
        "${priceCents / 100}.${(priceCents % 100).toString().padStart(2, '0')}"
    }
