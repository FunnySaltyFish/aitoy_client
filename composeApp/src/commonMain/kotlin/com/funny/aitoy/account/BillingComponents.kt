package com.funny.aitoy.account

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.funny.aitoy.core.model.Entitlement
import com.funny.aitoy.network.api.service.Product

@Composable
internal fun BillingPanel(
    vm: AccountBillingViewModel,
    monthlyProducts: List<Product>,
    addonProducts: List<Product>,
    purchaseMode: PurchaseMode,
    selectedMonthlyId: String,
    selectedAddonId: String,
    selectedMonths: Int,
    selectedQuantity: Int,
    quantityCap: Int,
    onModeChanged: (PurchaseMode) -> Unit,
    onMonthlySelected: (String) -> Unit,
    onAddonSelected: (String) -> Unit,
    onMonthsChanged: (Int) -> Unit,
    onQuantityChanged: (Int) -> Unit,
) {
    Panel(title = "会员与加量", action = "用完再买") {
        ModeTabs(purchaseMode, onModeChanged)
        Spacer(Modifier.height(14.dp))
        AnimatedContent(targetState = purchaseMode, label = "billing-mode") { mode ->
            Column {
                when (mode) {
                    PurchaseMode.Monthly -> {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(monthlyProducts) { product ->
                                MonthlyPlanCard(
                                    product = product,
                                    selected = product.id == selectedMonthlyId,
                                    currentLevel = vm.user.entitlement.membershipLevel,
                                    onClick = { onMonthlySelected(product.id) },
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        MonthSelector(selectedMonths, onMonthsChanged)
                    }
                    PurchaseMode.Addon -> {
                        val validDays = addonProducts.firstOrNull()?.validDays ?: 180
                        Text("有效期 $validDays 天", color = TextSoft, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(addonProducts) { product ->
                                AddonPackCard(
                                    product = product,
                                    selected = product.id == selectedAddonId,
                                    heldSeconds = vm.user.entitlement.aiAddonSecondsRemaining,
                                    onClick = { onAddonSelected(product.id) },
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        QuantitySelector(
                            quantity = selectedQuantity,
                            cap = quantityCap,
                            enabled = true,
                            onChanged = onQuantityChanged,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        PaymentSelector(vm)
        Spacer(Modifier.height(12.dp))
        BenefitRow()
    }
}

@Composable
internal fun Panel(title: String, action: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Velvet, RoundedCornerShape(16.dp))
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = TextMain, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Text(action, color = TextSoft, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun ModeTabs(mode: PurchaseMode, onChanged: (PurchaseMode) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Ink.copy(alpha = 0.4f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ModeTab("月卡", mode == PurchaseMode.Monthly, Modifier.weight(1f)) { onChanged(PurchaseMode.Monthly) }
        ModeTab("加量包", mode == PurchaseMode.Addon, Modifier.weight(1f)) { onChanged(PurchaseMode.Addon) }
    }
}

@Composable
private fun ModeTab(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) Ink else TextMain,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) Honey else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    )
}

@Composable
private fun MonthlyPlanCard(product: Product, selected: Boolean, currentLevel: String, onClick: () -> Unit) {
    val downgrade = isDowngrade(currentLevel, product.level)
    val canClick = product.purchasable && !downgrade
    PriceCardFrame(
        selected = selected,
        enabled = canClick,
        ribbon = when {
            product.highlight -> "推荐"
            downgrade -> "到期后可选"
            !product.purchasable -> "当前计划"
            else -> ""
        },
        onClick = onClick,
    ) {
        Text(product.name, color = if (downgrade) TextSoft else TextMain, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(10.dp))
        Text(formatMinuteNumber(product.aiControlSeconds), color = Honey, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineLarge)
        Text("分钟/月", color = TextSoft)
        Spacer(Modifier.weight(1f))
        PriceBlock(product)
    }
}

@Composable
private fun AddonPackCard(product: Product, selected: Boolean, heldSeconds: Int, onClick: () -> Unit) {
    val capReached = product.maxHoldSeconds > 0 && heldSeconds >= product.maxHoldSeconds
    PriceCardFrame(
        selected = selected,
        enabled = !capReached,
        ribbon = when {
            product.oneTimeLimit -> "限购"
            product.highlight -> "推荐"
            capReached -> "已达上限"
            else -> ""
        },
        onClick = onClick,
    ) {
        Text(product.name, color = TextMain, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(12.dp))
        Text(formatMinuteNumber(product.aiControlSeconds), color = Honey, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
        Text("分钟", color = TextSoft)
        Spacer(Modifier.weight(1f))
        PriceBlock(product)
        Text("有效期 ${product.validDays} 天", color = TextSoft, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PriceCardFrame(
    selected: Boolean,
    enabled: Boolean,
    ribbon: String,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .width(142.dp)
            .height(202.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2B2228), Color(0xFF211820))))
            .border(1.5.dp, if (selected) Honey else Line, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
        ) {
            content()
        }
        if (ribbon.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(topEnd = 8.dp, bottomStart = 8.dp))
                    .background(Honey)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(ribbon, color = Ink, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PriceBlock(product: Product) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text("¥${formatPrice(product.priceCents)}", color = TextMain, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
        if (product.type == "monthly" && product.priceCents > 0) {
            Text("/月", color = TextSoft, style = MaterialTheme.typography.labelSmall)
        }
    }
    if (product.originalPriceCents > product.priceCents) {
        Text(
            "¥${formatPrice(product.originalPriceCents)}",
            color = TextSoft,
            textDecoration = TextDecoration.LineThrough,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun MonthSelector(months: Int, onChanged: (Int) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(1 to "1 个月", 3 to "3 个月 9.5折", 12 to "12 个月 8.3折").forEach { (value, label) ->
            SelectorChip(label, months == value) { onChanged(value) }
        }
    }
}

@Composable
private fun QuantitySelector(quantity: Int, cap: Int, enabled: Boolean, onChanged: (Int) -> Unit) {
    val text = if (enabled) {
        if (cap <= 1) "当前只能购买 1 份" else "购买数量"
    } else {
        "选择加量包后可调整数量"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.045f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = TextSoft, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChanged((quantity - 1).coerceAtLeast(1)) }, enabled = enabled && quantity > 1) {
            Icon(Icons.Outlined.Remove, null, tint = TextSoft)
        }
        Text(quantity.toString(), color = TextMain, fontWeight = FontWeight.Black, modifier = Modifier.width(34.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { onChanged((quantity + 1).coerceAtMost(cap)) }, enabled = enabled && quantity < cap) {
            Icon(Icons.Outlined.Add, null, tint = if (enabled && quantity < cap) TextMain else TextSoft.copy(alpha = 0.38f))
        }
    }
}

@Composable
internal fun SelectorChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) Ink else TextMain,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Honey else VelvetLight)
            .border(1.dp, if (selected) Honey else Line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
private fun PaymentSelector(vm: AccountBillingViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PayTypeChip("支付宝", selected = vm.selectedPayType == "alipay", modifier = Modifier.weight(1f)) { vm.setPayType("alipay") }
        PayTypeChip("微信支付", selected = vm.selectedPayType == "wxpay", modifier = Modifier.weight(1f)) { vm.setPayType("wxpay") }
    }
}

@Composable
private fun PayTypeChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Honey.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.045f))
            .border(1.dp, if (selected) Honey else Line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(1.dp, if (selected) Honey else TextSoft, CircleShape)
                .background(if (selected) Honey else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Outlined.Done, null, tint = Ink, modifier = Modifier.size(12.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(text, color = TextMain, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BenefitRow() {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BenefitChip(Icons.Outlined.AccessTime, "更长 AI 控制")
        BenefitChip(Icons.Outlined.AutoAwesome, "新适配优先体验")
        BenefitChip(Icons.Outlined.Shield, "问题优先排查")
        BenefitChip(Icons.Outlined.CheckCircle, "多设备控制权益")
    }
}

@Composable
private fun BenefitChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.055f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Rose, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = TextSoft, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun ResponsibleNotice() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Honey.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .border(1.dp, Honey.copy(alpha = 0.26f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Info, null, tint = Honey)
        Spacer(Modifier.width(10.dp))
        Column {
            Text("理性消费，用完再买", color = Honey, fontWeight = FontWeight.Black)
            Text("单次购买和持有总量都有上限，避免不必要的囤积。", color = TextSoft, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun CheckoutBar(
    vm: AccountBillingViewModel,
    product: Product,
    mode: PurchaseMode,
    months: Int,
    quantity: Int,
    quantityCap: Int,
    agreementChecked: Boolean,
    onAgreementChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val boundedQuantity = quantity.coerceIn(1, quantityCap.coerceAtLeast(1))
    val checkout = checkoutSummary(product, months, boundedQuantity)
    val downgrade = mode == PurchaseMode.Monthly && isDowngrade(vm.user.entitlement.membershipLevel, product.level)
    val canPay = agreementChecked && product.purchasable && !downgrade && !vm.loading && quantityCap > 0
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Velvet.copy(alpha = 0.98f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .border(1.dp, Line, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("已选择", color = TextSoft)
                    Spacer(Modifier.width(6.dp))
                    Text(product.name, color = TextMain, fontWeight = FontWeight.Black)
                    if (product.highlight) {
                        Spacer(Modifier.width(6.dp))
                        Text("推荐", color = Ink, fontWeight = FontWeight.Black, modifier = Modifier.background(Honey, RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
                val detail = if (mode == PurchaseMode.Monthly) {
                    "${formatMinutes(product.aiControlSeconds)} / 月"
                } else {
                    "本次增加 ${formatMinutes(checkout.seconds)}"
                }
                Text(detail, color = TextSoft, style = MaterialTheme.typography.bodyMedium)
            }
            Text("¥${formatPrice(checkout.payCents)}", color = TextMain, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
        }
        if (checkout.savedCents > 0) {
            Text("已优惠 ¥${formatPrice(checkout.savedCents)}", color = Mint, style = MaterialTheme.typography.labelMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = agreementChecked, onCheckedChange = onAgreementChanged)
            Text("我已阅读并同意", color = TextSoft, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = vm::openMembershipAgreement) {
                Text("会员购买协议", color = Rose)
            }
        }
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = { vm.startPurchase(product) },
            enabled = canPay,
            colors = ButtonDefaults.buttonColors(containerColor = Rose, contentColor = Ink, disabledContainerColor = Line),
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 46.dp),
        ) {
            Text(
                when {
                    downgrade -> "到期后可选"
                    !product.purchasable -> "当前计划"
                    !agreementChecked -> "请先同意协议"
                    else -> "确认支付 ¥${formatPrice(checkout.payCents)}"
                },
                fontWeight = FontWeight.Black,
            )
        }
        if (vm.pendingOrderNo.isNotBlank()) {
            TextButton(onClick = vm::refreshPendingOrder, enabled = !vm.loading, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Outlined.CheckCircle, null, tint = Rose)
                Spacer(Modifier.width(6.dp))
                Text("我已完成支付", color = Rose)
            }
        }
    }
}

internal fun quantityCap(product: Product, ent: Entitlement): Int {
    if (product.type != "addon") return 1
    val configured = product.maxQuantity.coerceAtLeast(1)
    if (product.maxHoldSeconds <= 0 || product.aiControlSeconds <= 0) return configured
    val remainingCapacity = (product.maxHoldSeconds - ent.aiAddonSecondsRemaining).coerceAtLeast(0)
    return minOf(configured, remainingCapacity / product.aiControlSeconds).coerceAtLeast(0)
}
