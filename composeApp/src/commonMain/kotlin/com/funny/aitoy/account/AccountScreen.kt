package com.funny.aitoy.account

import ai_toy_bridge.composeapp.generated.resources.Res
import ai_toy_bridge.composeapp.generated.resources.account_header_bg
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.funny.aitoy.AiToyRoute
import com.funny.aitoy.core.navigation.LocalNavigator
import com.funny.aitoy.core.utils.nowMs
import com.funny.aitoy.network.api.service.Product
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun AccountScreen() {
    val homeVm = viewModel { AccountHomeViewModel() }
    val billingVm = viewModel { AccountBillingViewModel() }
    val navigator = LocalNavigator.current
    val user = homeVm.user
    val products by remember(homeVm) {
        derivedStateOf { homeVm.products.ifEmpty { defaultProducts() } }
    }
    val monthlyProducts by remember(homeVm) {
        derivedStateOf { products.filter { it.type == "monthly" } }
    }
    val addonProducts by remember(homeVm) {
        derivedStateOf { products.filter { it.type == "addon" } }
    }
    val selectedProduct = billingVm.selectedProduct
    val quantityCap = billingVm.quantityCap
    var launchAvatarPicker by remember { mutableStateOf<() -> Unit>({}) }

    LaunchedEffect(monthlyProducts, addonProducts, user.entitlement) {
        billingVm.syncAccountData(
            monthlyProducts = monthlyProducts,
            addonProducts = addonProducts,
        )
    }

    LaunchedEffect(quantityCap) {
        billingVm.syncQuantity()
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
            contentPadding = PaddingValues(bottom = if (selectedProduct != null) 148.dp else 24.dp),
        ) {
            item {
                AvatarPickerAndCropper(
                    vm = homeVm,
                    onLauncherReady = { launchAvatarPicker = it },
                )
                AccountHeader(
                    vm = homeVm,
                    onAvatarClick = launchAvatarPicker,
                    onRedeemClick = { navigator.navigate(AiToyRoute.Account.Redeem()) },
                )
            }
            item { SectionTitle("控制额度", "连接、待机和停止不消耗额度") }
            item { UsageSummaryCard(vm = homeVm, onDetailClick = { navigator.navigate(AiToyRoute.Account.Usage) }) }
            item { CampaignStrip(products = products) }
            item { SectionTitle("会员与加量", "按需要选择，不用提前囤积") }
            item {
                BillingPanel(
                    vm = billingVm,
                    monthlyProducts = monthlyProducts,
                    addonProducts = addonProducts,
                    purchaseMode = billingVm.purchaseMode,
                    selectedMonthlyId = billingVm.selectedMonthlyId,
                    selectedAddonId = billingVm.selectedAddonId,
                    selectedMonths = billingVm.selectedMonths,
                    selectedQuantity = billingVm.selectedQuantity,
                    quantityCap = quantityCap,
                    onModeChanged = billingVm::selectMode,
                    onMonthlySelected = billingVm::selectMonthly,
                    onAddonSelected = billingVm::selectAddon,
                    onMonthsChanged = billingVm::setMonths,
                    onQuantityChanged = billingVm::setQuantity,
                )
            }
            if (billingVm.message.isNotBlank()) {
                item { Text(billingVm.message, color = Honey, style = MaterialTheme.typography.bodyMedium) }
            }
            item { ResponsibleNotice() }
        }

        if (selectedProduct != null) {
            CheckoutBar(
                vm = billingVm,
                product = selectedProduct,
                mode = billingVm.purchaseMode,
                months = billingVm.selectedMonths,
                quantity = billingVm.selectedQuantity,
                quantityCap = quantityCap,
                agreementChecked = billingVm.agreementChecked,
                onAgreementChanged = billingVm::updateAgreementChecked,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun AvatarPickerAndCropper(vm: AccountHomeViewModel, onLauncherReady: (() -> Unit) -> Unit) {
    val scope = rememberCoroutineScope()
    val imageCropper = rememberImageCropper()
    val picker = rememberFilePickerLauncher(type = FileKitType.Image) { image ->
        if (image == null) return@rememberFilePickerLauncher
        scope.launch {
            when (val result = imageCropper.crop(image.toImageSrc())) {
                CropResult.Cancelled -> Unit
                CropError.LoadingError -> vm.showMessage("图片读取失败，请换一张再试。")
                CropError.SavingError -> vm.showMessage("图片处理失败，请换一张再试。")
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
private fun AccountHeader(
    vm: AccountHomeViewModel,
    onAvatarClick: () -> Unit,
    onRedeemClick: () -> Unit,
) {
    val user = vm.user
    var editingName by remember(user.uid) { mutableStateOf(false) }
    var nameDraft by remember(user.displayName, user.username) {
        mutableStateOf(displayName(user.displayName, user.username))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 2.dp)
            .height(190.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Velvet),
    ) {
        Image(
            painter = painterResource(Res.drawable.account_header_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Ink.copy(alpha = 0.92f),
                            Ink.copy(alpha = 0.52f),
                            Ink.copy(alpha = 0.18f),
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, Line.copy(alpha = 0.72f), RoundedCornerShape(22.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.Top) {
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
                                    vm.saveProfile(nameDraft)
                                    editingName = false
                                }),
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.titleLarge.copy(
                                    color = TextMain,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            IconButton(onClick = {
                                vm.saveProfile(nameDraft)
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
                            .background(Honey.copy(alpha = 0.14f), RoundedCornerShape(50))
                            .border(1.dp, Honey.copy(alpha = 0.34f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                IconButton(onClick = vm::refreshAccount, enabled = !vm.loading) {
                    Icon(Icons.Outlined.Refresh, null, tint = TextSoft)
                }
            }
            AccountHeroStats(vm, onRedeemClick)
        }
    }
    if (vm.loading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Rose)
    }
    if (vm.message.isNotBlank()) {
        Text(vm.message, color = Honey, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AccountHeroStats(vm: AccountHomeViewModel, onRedeemClick: () -> Unit) {
    val ent = vm.user.entitlement
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HeroPill(
            icon = { Icon(Icons.Outlined.Timer, null, tint = Rose, modifier = Modifier.size(16.dp)) },
            label = "可用 ${formatMinutes(totalRemainingSeconds(ent))}",
        )
        HeroPill(
            icon = { Icon(Icons.Outlined.EventAvailable, null, tint = Mint, modifier = Modifier.size(16.dp)) },
            label = "本月 ${formatMinutes(ent.aiQuotaSecondsRemaining)}",
        )
        HeroPill(
            icon = { Icon(Icons.Outlined.Shield, null, tint = Honey, modifier = Modifier.size(16.dp)) },
            label = "停止不消耗",
        )
        HeroPill(
            icon = { Icon(Icons.Outlined.CardGiftcard, null, tint = Mint, modifier = Modifier.size(16.dp)) },
            label = "兑换码",
            onClick = onRedeemClick,
        )
    }
}

@Composable
private fun HeroPill(icon: @Composable () -> Unit, label: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .background(Ink.copy(alpha = 0.58f), RoundedCornerShape(50))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(6.dp))
        Text(label, color = TextMain, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
private fun UsageSummaryCard(vm: AccountHomeViewModel, onDetailClick: () -> Unit) {
    val ent = vm.user.entitlement
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
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
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
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AccountMetric(
                title = "本月剩余",
                value = formatMinutes(ent.aiQuotaSecondsRemaining),
                accent = Rose,
                modifier = Modifier.weight(1f),
            )
            AccountMetric(
                title = "永久包",
                value = formatMinutes(ent.aiAddonSecondsRemaining),
                accent = Honey,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("只在 AI 实际控制时消耗，连接、待机和停止不消耗。", color = TextSoft, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AccountMetric(title: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .defaultMinSize(minWidth = 132.dp)
            .background(Color.White.copy(alpha = 0.055f), RoundedCornerShape(13.dp))
            .border(1.dp, accent.copy(alpha = 0.18f), RoundedCornerShape(13.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(7.dp))
            Text(title, color = TextSoft, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(6.dp))
        Text(value, color = TextMain, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextMain, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, color = TextSoft, style = MaterialTheme.typography.bodyMedium)
        }
        Icon(
            imageVector = if (title == "兑换与说明") Icons.Outlined.CardGiftcard else Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = Honey.copy(alpha = 0.82f),
        )
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
