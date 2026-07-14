package com.funny.aitoy

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.attafitamim.krop.core.crop.CircleCropShape
import com.attafitamim.krop.core.crop.CropError
import com.attafitamim.krop.core.crop.CropResult
import com.attafitamim.krop.core.crop.RectCropShape
import com.attafitamim.krop.core.crop.crop
import com.attafitamim.krop.core.crop.cropperStyle
import com.attafitamim.krop.core.crop.rememberImageCropper
import com.attafitamim.krop.filekit.ImageFormat
import com.attafitamim.krop.filekit.encodeToByteArray
import com.attafitamim.krop.filekit.toImageSrc
import com.attafitamim.krop.ui.ImageCropperDialog
import com.funny.aitoy.account.BillingPanel
import com.funny.aitoy.account.CheckoutBar
import com.funny.aitoy.account.Honey
import com.funny.aitoy.account.Ink
import com.funny.aitoy.account.Line
import com.funny.aitoy.account.Mint
import com.funny.aitoy.account.Panel
import com.funny.aitoy.account.PurchaseMode
import com.funny.aitoy.account.ResponsibleNotice
import com.funny.aitoy.account.Rose
import com.funny.aitoy.account.RoseDeep
import com.funny.aitoy.account.SelectorChip
import com.funny.aitoy.account.TextMain
import com.funny.aitoy.account.TextSoft
import com.funny.aitoy.account.Velvet
import com.funny.aitoy.account.activeCampaign
import com.funny.aitoy.account.defaultProducts
import com.funny.aitoy.account.displayInitial
import com.funny.aitoy.account.displayName
import com.funny.aitoy.account.formatMinuteNumber
import com.funny.aitoy.account.formatMinutes
import com.funny.aitoy.account.formatPrice
import com.funny.aitoy.account.quantityCap
import com.funny.aitoy.account.totalRemainingSeconds
import com.funny.aitoy.core.utils.nowMs
import com.funny.aitoy.network.api.service.Product
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun AccountScreen(vm: BridgeViewModel) {
    var showUsageDetail by remember { mutableStateOf(false) }
    val products = vm.memberProducts.ifEmpty { defaultProducts() }
    val monthlyProducts = products.filter { it.type == "monthly" }
    val addonProducts = products.filter { it.type == "addon" }
    var purchaseMode by remember { mutableStateOf(PurchaseMode.Monthly) }
    var selectedMonthlyId by remember { mutableStateOf("") }
    var selectedAddonId by remember { mutableStateOf("") }
    var selectedMonths by remember { mutableIntStateOf(1) }
    var selectedQuantity by remember { mutableIntStateOf(1) }
    var agreementChecked by remember { mutableStateOf(false) }
    var launchAvatarPicker by remember { mutableStateOf<() -> Unit>({}) }

    LaunchedEffect(products) {
        if (selectedMonthlyId.isBlank() || monthlyProducts.none { it.id == selectedMonthlyId }) {
            selectedMonthlyId = monthlyProducts.firstOrNull { it.highlight }?.id
                ?: monthlyProducts.firstOrNull { it.purchasable }?.id
                        ?: monthlyProducts.firstOrNull()?.id.orEmpty()
        }
        if (selectedAddonId.isBlank() || addonProducts.none { it.id == selectedAddonId }) {
            selectedAddonId = addonProducts.firstOrNull { it.highlight }?.id
                ?: addonProducts.firstOrNull()?.id.orEmpty()
        }
    }

    if (showUsageDetail) {
        UsageDetailScreen(vm = vm, onBack = { showUsageDetail = false })
        return
    }

    val selectedProduct = when (purchaseMode) {
        PurchaseMode.Monthly -> monthlyProducts.firstOrNull { it.id == selectedMonthlyId }
        PurchaseMode.Addon -> addonProducts.firstOrNull { it.id == selectedAddonId }
    } ?: products.firstOrNull()
    val quantityCap = selectedProduct?.let { quantityCap(it, vm.accountUser.entitlement) } ?: 1
    LaunchedEffect(selectedProduct?.id, quantityCap) {
        selectedQuantity = selectedQuantity.coerceIn(1, quantityCap.coerceAtLeast(1))
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                AvatarPickerAndCropper(
                    vm = vm,
                    onLauncherReady = { launchAvatarPicker = it },
                )
                AccountHeader(vm = vm, onAvatarClick = launchAvatarPicker)
            }
            item { UsageSummaryCard(vm = vm, onDetailClick = { showUsageDetail = true }) }
            item { RedeemCodePanel(vm = vm) }
            item { CampaignStrip(products = products) }
            item {
                BillingPanel(
                    vm = vm,
                    monthlyProducts = monthlyProducts,
                    addonProducts = addonProducts,
                    purchaseMode = purchaseMode,
                    selectedMonthlyId = selectedMonthlyId,
                    selectedAddonId = selectedAddonId,
                    selectedMonths = selectedMonths,
                    selectedQuantity = selectedQuantity,
                    quantityCap = quantityCap,
                    onModeChanged = { purchaseMode = it },
                    onMonthlySelected = {
                        purchaseMode = PurchaseMode.Monthly
                        selectedMonthlyId = it
                    },
                    onAddonSelected = {
                        purchaseMode = PurchaseMode.Addon
                        selectedAddonId = it
                    },
                    onMonthsChanged = { selectedMonths = it },
                    onQuantityChanged = { selectedQuantity = it },
                )
            }
            item { ResponsibleNotice() }
            item { Spacer(Modifier.height(132.dp)) }
        }

        if (selectedProduct != null) {
            CheckoutBar(
                vm = vm,
                product = selectedProduct,
                mode = purchaseMode,
                months = selectedMonths,
                quantity = selectedQuantity,
                quantityCap = quantityCap,
                agreementChecked = agreementChecked,
                onAgreementChanged = { agreementChecked = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun AvatarPickerAndCropper(vm: BridgeViewModel, onLauncherReady: (() -> Unit) -> Unit) {
    val scope = rememberCoroutineScope()
    val imageCropper = rememberImageCropper()
    val picker = rememberFilePickerLauncher(type = FileKitType.Image) { image ->
        if (image == null) return@rememberFilePickerLauncher
        scope.launch {
            when (val result = imageCropper.crop(image.toImageSrc())) {
                CropResult.Cancelled -> Unit
                CropError.LoadingError -> vm.showAccountMessage("图片读取失败，请换一张再试。")
                CropError.SavingError -> vm.showAccountMessage("图片处理失败，请换一张再试。")
                is CropResult.Success -> {
                    val bytes = result.bitmap.encodeToByteArray(ImageFormat.JPEG, quality = 88)
                    vm.uploadAvatar(image.name.ifBlank { "avatar.jpg" }, bytes)
                }
            }
        }
    }

    LaunchedEffect(picker) {
        onLauncherReady { picker.launch() }
    }

    imageCropper.cropState?.let { state ->
        ImageCropperDialog(
            state = state,
            style = cropperStyle(
                rectColor = Rose,
                overlay = Ink.copy(alpha = 0.72f),
                shapes = listOf(CircleCropShape, RectCropShape),
            ),
            dialogShape = RoundedCornerShape(18.dp),
        )
    }
}

@Composable
private fun RedeemCodePanel(vm: BridgeViewModel) {
    Panel(title = "兑换码", action = "朋友赠送或活动发放") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = vm.redeemCodeDraft,
                onValueChange = { vm.redeemCodeDraft = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(32) },
                singleLine = true,
                placeholder = { Text("输入兑换码", color = TextSoft) },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = vm::redeemCode,
                enabled = !vm.accountLoading && vm.redeemCodeDraft.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Honey, contentColor = Ink, disabledContainerColor = Line),
                shape = RoundedCornerShape(50),
            ) {
                Text("兑换", fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("兑换成功后，额度会自动加入当前账号。", color = TextSoft, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AccountHeader(vm: BridgeViewModel, onAvatarClick: () -> Unit) {
    val user = vm.accountUser
    var editingName by remember(user.uid) { mutableStateOf(false) }
    var nameDraft by remember(user.displayName, user.username) {
        mutableStateOf(displayName(user.displayName, user.username))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarBox(
            name = displayName(user.displayName, user.username),
            avatarUrl = user.avatarUrl,
            onClick = onAvatarClick,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            if (editingName) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it.take(12) },
                        singleLine = true,
                        keyboardActions = KeyboardActions(onDone = {
                            vm.profileNameDraft = nameDraft.trim()
                            vm.saveProfile()
                            editingName = false
                        }),
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            color = TextMain,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    IconButton(onClick = {
                        vm.profileNameDraft = nameDraft.trim()
                        vm.saveProfile()
                        editingName = false
                    }) {
                        Icon(Icons.Outlined.Done, null, tint = Mint)
                    }
                    IconButton(onClick = {
                        nameDraft = displayName(user.displayName, user.username)
                        editingName = false
                    }) {
                        Icon(Icons.Outlined.Close, null, tint = TextSoft)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayName(user.displayName, user.username),
                        color = TextMain,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    IconButton(onClick = { editingName = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.Edit, null, tint = TextSoft)
                    }
                }
            }
            Text(
                text = user.entitlement.membershipName.ifBlank { "免费版" },
                color = Honey,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Honey.copy(alpha = 0.12f), RoundedCornerShape(50))
                    .border(1.dp, Honey.copy(alpha = 0.32f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        IconButton(onClick = vm::refreshAccount, enabled = !vm.accountLoading) {
            Icon(Icons.Outlined.Refresh, null, tint = TextSoft)
        }
    }
    if (vm.accountLoading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Rose)
    }
    if (vm.accountMessage.isNotBlank()) {
        Text(vm.accountMessage, color = Honey, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AvatarBox(name: String, avatarUrl: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Rose.copy(alpha = 0.18f))
            .border(1.dp, Rose.copy(alpha = 0.64f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarUrl.isNotBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(displayInitial(name), color = Rose, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineLarge)
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(26.dp)
                .clip(CircleShape)
                .background(Ink)
                .border(1.dp, Rose.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.PhotoCamera, null, tint = TextMain, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun UsageSummaryCard(vm: BridgeViewModel, onDetailClick: () -> Unit) {
    val ent = vm.accountUser.entitlement
    val total = (ent.aiQuotaSecondsMonthly + ent.aiAddonSecondsRemaining).coerceAtLeast(1)
    val remaining = totalRemainingSeconds(ent)
    val used = (total - remaining).coerceAtLeast(0)
    val progress by animateFloatAsState((used.toFloat() / total).coerceIn(0f, 1f), label = "usage-progress")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(Color(0xFF241C23), Color(0xFF171017))), RoundedCornerShape(16.dp))
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("总可用", color = TextSoft, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(formatMinuteNumber(remaining), color = TextMain, fontWeight = FontWeight.Black, style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.width(7.dp))
                    Text("分钟", color = TextMain, modifier = Modifier.padding(bottom = 9.dp))
                }
            }
            OutlinedButton(onClick = onDetailClick, shape = RoundedCornerShape(50)) {
                Text("明细")
                Icon(Icons.Outlined.KeyboardArrowRight, null)
            }
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = Rose,
            trackColor = Honey.copy(alpha = 0.35f),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BalanceDot("本月剩余 ${formatMinutes(ent.aiQuotaSecondsRemaining)}", Rose)
            BalanceDot("永久包 ${formatMinutes(ent.aiAddonSecondsRemaining)}", Honey)
        }
        Spacer(Modifier.height(6.dp))
        Text("只在 AI 实际控制时消耗，连接、待机和停止不消耗。", color = TextSoft, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BalanceDot(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, color = TextSoft, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CampaignStrip(products: List<Product>) {
    val activeProduct = products.firstOrNull { activeCampaign(it) }
    var now by remember { mutableStateOf(nowMs()) }
    LaunchedEffect(activeProduct?.campaignEndsAtMs) {
        while (activeProduct != null) {
            now = nowMs()
            delay(1000)
        }
    }
    val pulse by rememberInfiniteTransition(label = "campaign-pulse").animateFloat(
        initialValue = 0.38f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "campaign-alpha",
    )
    val leftMs = ((activeProduct?.campaignEndsAtMs ?: 0L) - now).coerceAtLeast(0)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(RoseDeep.copy(alpha = 0.35f), Honey.copy(alpha = 0.16f))))
            .border(1.dp, Rose.copy(alpha = if (activeProduct != null) pulse else 0.24f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.AutoAwesome, null, tint = Rose)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(activeProduct?.campaignTitle?.ifBlank { "首发优惠" } ?: "首发会员计划", color = TextMain, fontWeight = FontWeight.Black)
            Text(
                if (activeProduct != null) "限时低至 ¥${formatPrice(activeProduct.priceCents)} / 月" else "免费额度每月刷新，按需选择即可。",
                color = TextSoft,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (activeProduct != null) CountdownCells(leftMs)
    }
}

@Composable
private fun CountdownCells(leftMs: Long) {
    val totalSeconds = (leftMs / 1000).toInt()
    val days = totalSeconds / 86_400
    val hours = totalSeconds / 3_600 % 24
    val minutes = totalSeconds / 60 % 60
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        TimeCell(days, "天")
        TimeCell(hours, "时")
        TimeCell(minutes, "分")
    }
}

@Composable
private fun TimeCell(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value.toString().padStart(2, '0'),
            color = TextMain,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            modifier = Modifier.background(Ink.copy(alpha = 0.62f), RoundedCornerShape(7.dp)).padding(horizontal = 6.dp, vertical = 4.dp),
        )
        Text(label, color = TextSoft, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun UsageDetailScreen(vm: BridgeViewModel, onBack: () -> Unit) {
    val ent = vm.accountUser.entitlement
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(modifier = Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack, shape = RoundedCornerShape(50)) { Text("返回") }
                Spacer(Modifier.width(12.dp))
                Text("控制明细", color = TextMain, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
            }
        }
        item {
            Panel(title = "额度汇总", action = "按实际控制结算") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryMetric(
                        "本月剩余",
                        formatMinutes(ent.aiQuotaSecondsRemaining),
                        Modifier.weight(1f)
                    )
                    SummaryMetric(
                        "永久包",
                        formatMinutes(ent.aiAddonSecondsRemaining),
                        Modifier.weight(1f)
                    )
                    SummaryMetric(
                        "总可用",
                        formatMinutes(totalRemainingSeconds(ent)),
                        Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "明细页后续会展示每日汇总、每次 AI 控制会话和结算分钟数。",
                    color = TextSoft,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        item {
            Panel(title = "记录", action = "可筛选") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("全部", "今天", "本周").forEachIndexed { index, text ->
                        SelectorChip(text, index == 0) {}
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.045f), RoundedCornerShape(14.dp)).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("暂无可查看的控制记录", color = TextMain, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("产生 AI 控制消耗后，会在这里按天和会话展示。", color = TextSoft, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.055f), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text(label, color = TextSoft, style = MaterialTheme.typography.labelMedium)
        Text(value, color = TextMain, fontWeight = FontWeight.Black, maxLines = 1)
    }
}
