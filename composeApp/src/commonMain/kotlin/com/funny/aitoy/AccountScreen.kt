package com.funny.aitoy

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShoppingCart
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
import com.funny.aitoy.core.model.Entitlement
import com.funny.aitoy.core.utils.nowMs
import com.funny.aitoy.network.api.service.Product
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun AccountScreen(vm: BridgeViewModel) {
    var showUsageDetail by remember { mutableStateOf(false) }
    var selectedProductId by remember(vm.memberProducts) {
        mutableStateOf(vm.memberProducts.firstOrNull { it.highlight }?.id ?: vm.memberProducts.firstOrNull()?.id.orEmpty())
    }
    var selectedMonths by remember { mutableIntStateOf(1) }

    if (showUsageDetail) {
        UsageDetailScreen(vm = vm, onBack = { showUsageDetail = false })
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            AvatarPickerAndCropper(vm)
            AccountHeader(vm)
        }
        item { UsageHero(vm, onDetailClick = { showUsageDetail = true }) }
        item {
            MembershipPanel(
                vm = vm,
                selectedProductId = selectedProductId,
                selectedMonths = selectedMonths,
                onProductSelected = { selectedProductId = it },
                onMonthsChanged = { selectedMonths = it },
            )
        }
        item { UsageDetailEntry(onClick = { showUsageDetail = true }) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun AvatarPickerAndCropper(vm: BridgeViewModel) {
    val scope = rememberCoroutineScope()
    val imageCropper = rememberImageCropper()
    val picker = rememberFilePickerLauncher(type = FileKitType.Image) { image ->
        if (image == null) return@rememberFilePickerLauncher
        scope.launch {
            val result = imageCropper.crop(image.toImageSrc())
            when (result) {
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

    AccountAvatarLauncher.current = { picker.launch() }

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

private object AccountAvatarLauncher {
    var current: () -> Unit = {}
}

@Composable
private fun AccountHeader(vm: BridgeViewModel) {
    val user = vm.accountUser
    var editingName by remember(user.uid) { mutableStateOf(false) }
    var nameDraft by remember(user.displayName, user.username) {
        mutableStateOf(displayName(user.displayName, user.username))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarBox(
            name = displayName(user.displayName, user.username),
            avatarUrl = user.avatarUrl,
            onClick = AccountAvatarLauncher.current,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "昵称最多 12 个字",
                    color = TextSoft,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        OutlinedButton(
            onClick = vm::refreshAccount,
            enabled = !vm.accountLoading,
            shape = RoundedCornerShape(50),
        ) {
            Icon(Icons.Outlined.Refresh, null)
            Spacer(Modifier.width(6.dp))
            Text("刷新")
        }
    }

    if (vm.accountLoading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Rose)
    }
    if (vm.accountMessage.isNotBlank()) {
        Text(
            text = vm.accountMessage,
            color = Honey,
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun AvatarBox(name: String, avatarUrl: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(78.dp)
            .clip(CircleShape)
            .background(Rose.copy(alpha = 0.18f))
            .border(1.dp, Rose.copy(alpha = 0.58f), CircleShape)
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
            Text(
                text = displayInitial(name),
                color = Rose,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.headlineLarge,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(28.dp)
                .clip(CircleShape)
                .background(Ink)
                .border(1.dp, Rose.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.PhotoCamera, null, tint = TextMain, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun UsageHero(vm: BridgeViewModel, onDetailClick: () -> Unit) {
    val ent = vm.accountUser.entitlement
    val total = ent.aiQuotaSecondsMonthly.coerceAtLeast(1)
    val used = ent.aiQuotaSecondsUsed.coerceAtLeast(0)
    val remaining = ent.aiQuotaSecondsRemaining.coerceAtLeast(0)
    val progress = (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(progress, label = "usage-progress")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF242126), Color(0xFF181318)),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
                RoundedCornerShape(18.dp),
            )
            .border(1.dp, Line, RoundedCornerShape(18.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UsageRing(
                progress = animatedProgress,
                label = formatMinutesNumber(remaining),
                subLabel = "分钟",
            )
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text("本月可用控制时长", color = TextSoft, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatMinutesNumber(remaining),
                        color = Honey,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.displaySmall,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("分钟", color = Honey, modifier = Modifier.padding(bottom = 8.dp))
                }
                Text(
                    text = "仅真实使用 AI 控制时消耗额度",
                    color = TextSoft,
                    style = MaterialTheme.typography.bodyMedium,
                )
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
            color = if (remaining > 0) Mint else Danger,
            trackColor = Color(0x33FFFFFF),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "本月额度 ${formatMinutes(total)}，已用 ${formatMinutes(used)}。连接、待机和停止控制不消耗。",
            color = TextSoft,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun UsageRing(progress: Float, label: String, subLabel: String) {
    Box(modifier = Modifier.size(118.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
            drawArc(
                color = Color.White.copy(alpha = 0.12f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
            drawArc(
                color = Mint,
                startAngle = -90f,
                sweepAngle = (1f - progress).coerceIn(0f, 1f) * 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = TextMain, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
            Text(subLabel, color = TextSoft, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MembershipPanel(
    vm: BridgeViewModel,
    selectedProductId: String,
    selectedMonths: Int,
    onProductSelected: (String) -> Unit,
    onMonthsChanged: (Int) -> Unit,
) {
    val products = vm.memberProducts.ifEmpty { defaultProducts() }
    val selected = products.firstOrNull { it.id == selectedProductId } ?: products.first()
    val currentLevel = vm.accountUser.entitlement.membershipLevel
    val checkout = remember(selected, selectedMonths) { checkoutSummary(selected, selectedMonths) }

    Panel(title = "会员套餐", icon = Icons.Outlined.ShoppingCart) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("升级后立即生效", color = TextSoft, modifier = Modifier.weight(1f))
            Text("剩余额度自动折算", color = Honey, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            products.forEach { product ->
                PlanCard(
                    product = product,
                    selected = product.id == selected.id,
                    currentLevel = currentLevel,
                    modifier = Modifier.weight(1f),
                    onClick = { onProductSelected(product.id) },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        UpgradeHint(currentLevel = currentLevel, selected = selected, ent = vm.accountUser.entitlement)
        Spacer(Modifier.height(12.dp))
        CampaignBanner(product = selected)
        Spacer(Modifier.height(14.dp))
        BenefitGrid()
        Spacer(Modifier.height(14.dp))
        MonthSelector(selectedMonths, onMonthsChanged)
        Spacer(Modifier.height(14.dp))
        PaymentSelector(vm)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("应付金额", color = TextSoft)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "¥${formatPrice(checkout.payCents)}",
                        color = Honey,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    if (checkout.savedCents > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "已优惠 ¥${formatPrice(checkout.savedCents)}",
                            color = Mint,
                            modifier = Modifier
                                .background(Mint.copy(alpha = 0.10f), RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            Button(
                onClick = { vm.startMembershipPurchase(selected.id, selectedMonths) },
                enabled = !vm.accountLoading && !isDowngrade(currentLevel, selected.level),
                colors = ButtonDefaults.buttonColors(containerColor = Honey, contentColor = Ink),
                shape = RoundedCornerShape(50),
            ) {
                Text(if (isDowngrade(currentLevel, selected.level)) "到期后可选" else "立即结算", fontWeight = FontWeight.Black)
            }
        }
        if (vm.pendingOrderNo.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = vm::refreshPendingOrder, enabled = !vm.accountLoading) {
                Icon(Icons.Outlined.CheckCircle, null)
                Spacer(Modifier.width(6.dp))
                Text("我已完成支付")
            }
        }
    }
}

@Composable
private fun PlanCard(product: Product, selected: Boolean, currentLevel: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val downgrade = isDowngrade(currentLevel, product.level)
    val border = when {
        selected -> Honey
        product.highlight -> Rose
        else -> Line
    }
    Column(
        modifier = modifier
            .height(174.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0xFF2C2527) else Color(0xFF211B20))
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable(enabled = !downgrade, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(product.name, color = TextMain.copy(alpha = if (downgrade) 0.55f else 1f), fontWeight = FontWeight.Black)
                if (product.highlight) {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "推荐",
                        color = Ink,
                        modifier = Modifier
                            .background(Rose, RoundedCornerShape(50))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatMinutes(product.aiControlSeconds),
                color = Honey.copy(alpha = if (downgrade) 0.55f else 1f),
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleLarge,
            )
            Text("AI 控制 / 月", color = TextSoft, style = MaterialTheme.typography.labelMedium)
        }
        Column {
            if (product.discountLabel.isNotBlank()) {
                Text(product.discountLabel, color = Mint, style = MaterialTheme.typography.labelMedium)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("¥${formatPrice(product.priceCents)}", color = TextMain, fontWeight = FontWeight.Black)
                Text(" / 月", color = TextSoft, style = MaterialTheme.typography.labelSmall)
            }
            if (product.originalPriceCents > product.priceCents) {
                Text(
                    "¥${formatPrice(product.originalPriceCents)}",
                    color = TextSoft,
                    textDecoration = TextDecoration.LineThrough,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (downgrade) {
                Text("到期后可选", color = TextSoft, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun UpgradeHint(currentLevel: String, selected: Product, ent: Entitlement) {
    val text = when {
        isDowngrade(currentLevel, selected.level) -> "当前套餐高于所选套餐，降配需要等当前会员到期后重新购买。"
        currentLevel == selected.level && currentLevel != "free" -> "续费会顺延会员有效期，下一周期继续使用 ${selected.name}。"
        currentLevel != "free" -> "升级会把原套餐剩余时间按价值折算，新套餐立即生效，越早升级越划算。"
        else -> "首次开通后立即获得更长 AI 控制时长，免费版额度仍保留基础体验。"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Honey.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Info, null, tint = Honey)
        Spacer(Modifier.width(8.dp))
        Text(text, color = TextSoft, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CampaignBanner(product: Product) {
    var now by remember { mutableStateOf(nowMs()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = nowMs()
            delay(1000)
        }
    }
    val leftMs = (product.campaignEndsAtMs - now).coerceAtLeast(0)
    val pulse by rememberInfiniteTransition(label = "campaign-pulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "campaign-border",
    )
    val active = product.campaignEndsAtMs > now && product.originalPriceCents > product.priceCents
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(RoseDeep.copy(alpha = 0.38f), Honey.copy(alpha = 0.18f))))
            .border(1.dp, Rose.copy(alpha = if (active) pulse else 0.25f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = product.campaignTitle.ifBlank { "限时创始会员优惠" },
                color = TextMain,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = if (active) "${product.name} 限时优惠，活动结束后恢复原价。" else "当前暂无额外活动，套餐仍可正常购买。",
                color = TextSoft,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        CountdownCells(leftMs)
    }
}

@Composable
private fun CountdownCells(leftMs: Long) {
    val totalSeconds = (leftMs / 1000).toInt()
    val days = totalSeconds / 86_400
    val hours = totalSeconds / 3_600 % 24
    val minutes = totalSeconds / 60 % 60
    val seconds = totalSeconds % 60
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        TimeCell(days, "天")
        TimeCell(hours, "时")
        TimeCell(minutes, "分")
        TimeCell(seconds, "秒")
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
            modifier = Modifier
                .background(Ink.copy(alpha = 0.62f), RoundedCornerShape(8.dp))
                .padding(horizontal = 7.dp, vertical = 5.dp),
        )
        Text(label, color = TextSoft, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun BenefitGrid() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BenefitChip(Icons.Outlined.AccessTime, "更长 AI 控制", Modifier.weight(1f))
            BenefitChip(Icons.Outlined.AutoAwesome, "新适配优先体验", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BenefitChip(Icons.Outlined.Shield, "问题优先排查", Modifier.weight(1f))
            BenefitChip(Icons.Outlined.CheckCircle, "多设备控制权益", Modifier.weight(1f))
        }
    }
}

@Composable
private fun BenefitChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.055f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Rose, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = TextSoft, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun MonthSelector(months: Int, onChanged: (Int) -> Unit) {
    Column {
        Text("购买时长", color = TextSoft)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to "1 个月", 3 to "3 个月 9.5折", 12 to "12 个月 8.3折").forEach { (value, label) ->
                Text(
                    label,
                    color = if (months == value) Ink else TextMain,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (months == value) Honey else VelvetLight)
                        .border(1.dp, if (months == value) Honey else Line, RoundedCornerShape(12.dp))
                        .clickable { onChanged(value) }
                        .padding(vertical = 11.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChanged((months - 1).coerceAtLeast(1)) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Remove, null, tint = TextSoft)
            }
            Text("$months 个月", color = TextMain, fontWeight = FontWeight.Bold)
            IconButton(onClick = { onChanged((months + 1).coerceAtMost(12)) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Add, null, tint = TextSoft)
            }
        }
    }
}

@Composable
private fun PaymentSelector(vm: BridgeViewModel) {
    Column {
        Text("支付方式", color = TextSoft)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PayTypeChip("支付宝", selected = vm.selectedPayType == "alipay", modifier = Modifier.weight(1f)) {
                vm.selectedPayType = "alipay"
            }
            PayTypeChip("微信支付", selected = vm.selectedPayType == "wxpay", modifier = Modifier.weight(1f)) {
                vm.selectedPayType = "wxpay"
            }
        }
    }
}

@Composable
private fun PayTypeChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Honey.copy(alpha = 0.16f) else VelvetLight)
            .border(1.dp, if (selected) Honey else Line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
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
private fun UsageDetailEntry(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Velvet)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.AccessTime, null, tint = Rose, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("控制明细", color = TextMain, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            Text("查看每日和每次会话的消耗记录", color = TextSoft, style = MaterialTheme.typography.bodyMedium)
        }
        Icon(Icons.Outlined.KeyboardArrowRight, null, tint = TextSoft)
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
            Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onBack, shape = RoundedCornerShape(50)) {
                    Text("返回")
                }
                Spacer(Modifier.width(12.dp))
                Text("控制明细", color = TextMain, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
            }
        }
        item {
            Panel(title = "本月汇总", icon = Icons.Outlined.AccessTime) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryMetric("已用", formatMinutes(ent.aiQuotaSecondsUsed), Modifier.weight(1f))
                    SummaryMetric("剩余", formatMinutes(ent.aiQuotaSecondsRemaining), Modifier.weight(1f))
                    SummaryMetric("总额", formatMinutes(ent.aiQuotaSecondsMonthly), Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "明细页会展示每日汇总、每次 AI 控制会话、结算分钟数和扣减原因。连接待机、手动控制和停止控制不计入消耗。",
                    color = TextSoft,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            Panel(title = "记录", icon = Icons.Outlined.Info) {
                UsageFilterRow()
                Spacer(Modifier.height(12.dp))
                EmptyUsageRecords()
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

@Composable
private fun UsageFilterRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("全部", "今天", "本周").forEachIndexed { index, text ->
            Text(
                text = text,
                color = if (index == 0) Ink else TextMain,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(if (index == 0) Honey else VelvetLight, RoundedCornerShape(50))
                    .border(1.dp, if (index == 0) Honey else Line, RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun EmptyUsageRecords() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.045f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("暂无可查看的控制记录", color = TextMain, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "产生 AI 控制消耗后，会在这里按天和会话展示。",
            color = TextSoft,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private data class CheckoutSummary(val payCents: Int, val savedCents: Int)

private fun checkoutSummary(product: Product, months: Int): CheckoutSummary {
    val monthDiscountPermille = when {
        months >= 12 -> 830
        months >= 3 -> 950
        else -> 1000
    }
    val original = product.originalPriceCents.coerceAtLeast(product.priceCents) * months
    val campaignPrice = product.priceCents * months
    val pay = (campaignPrice * monthDiscountPermille / 1000f).roundToInt()
    return CheckoutSummary(payCents = pay, savedCents = (original - pay).coerceAtLeast(0))
}

private fun defaultProducts(): List<Product> = listOf(
    Product(
        id = "lite_month",
        level = "lite",
        name = "轻享版",
        priceCents = 490,
        originalPriceCents = 490,
        aiControlSeconds = 120 * 60,
        description = "适合偶尔使用 AI 控制。",
        tagline = "轻度体验",
    ),
    Product(
        id = "standard_month",
        level = "standard",
        name = "标准版",
        priceCents = 990,
        originalPriceCents = 1490,
        discountLabel = "立省 ¥5",
        campaignTitle = "标准版限时优惠",
        campaignEndsAtMs = nowMs() + 2L * 24 * 60 * 60 * 1000 + 8L * 60 * 60 * 1000,
        aiControlSeconds = 300 * 60,
        description = "适合日常使用，推荐选择。",
        tagline = "多数用户会选它",
        highlight = true,
    ),
    Product(
        id = "support_month",
        level = "support",
        name = "支持版",
        priceCents = 1990,
        originalPriceCents = 2990,
        discountLabel = "立省 ¥10",
        campaignTitle = "支持版创始优惠",
        campaignEndsAtMs = nowMs() + 2L * 24 * 60 * 60 * 1000 + 8L * 60 * 60 * 1000,
        aiControlSeconds = 900 * 60,
        description = "适合高频使用，也支持持续适配。",
        tagline = "高频与支持者",
    ),
)

private fun displayName(displayName: String, username: String): String =
    displayName.ifBlank { username.ifBlank { "AI Toy 用户" } }.take(12)

private fun displayInitial(value: String): String =
    value.trim().firstOrNull()?.toString()?.uppercase().orEmpty().ifBlank { "A" }

private fun formatMinutes(seconds: Int): String {
    val minutes = (seconds / 60f).roundToInt().coerceAtLeast(0)
    return "${minutes} 分钟"
}

private fun formatMinutesNumber(seconds: Int): String =
    (seconds / 60f).roundToInt().coerceAtLeast(0).toString()

private fun formatPrice(priceCents: Int): String =
    if (priceCents % 100 == 0) {
        (priceCents / 100).toString()
    } else {
        "${priceCents / 100}.${(priceCents % 100).toString().padStart(2, '0')}"
    }

private fun levelRank(level: String): Int = when (level) {
    "lite" -> 1
    "standard" -> 2
    "support" -> 3
    else -> 0
}

private fun isDowngrade(currentLevel: String, targetLevel: String): Boolean =
    levelRank(currentLevel) > levelRank(targetLevel)
