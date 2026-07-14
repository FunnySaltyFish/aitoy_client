package com.funny.aitoy.account

import androidx.compose.ui.graphics.Color
import com.funny.aitoy.core.model.Entitlement
import com.funny.aitoy.core.utils.nowMs
import com.funny.aitoy.network.api.service.Product
import kotlin.math.roundToInt

internal val Ink = Color(0xFF120C12)
internal val Velvet = Color(0xFF211820)
internal val VelvetLight = Color(0xFF2A2028)
internal val Line = Color(0xFF4A3745)
internal val TextMain = Color(0xFFF8EEF5)
internal val TextSoft = Color(0xFFC8B6C1)
internal val Rose = Color(0xFFF49ABC)
internal val RoseDeep = Color(0xFFB85576)
internal val Honey = Color(0xFFFFD39A)
internal val Mint = Color(0xFF76E0C4)
internal val Danger = Color(0xFFFF8E8E)

internal enum class PurchaseMode { Monthly, Addon }

internal data class CheckoutSummary(
    val payCents: Int,
    val originalCents: Int,
    val savedCents: Int,
    val seconds: Int,
)

internal fun checkoutSummary(product: Product, months: Int, quantity: Int): CheckoutSummary {
    return if (product.type == "addon") {
        val boundedQuantity = quantity.coerceIn(1, product.maxQuantity.coerceAtLeast(1))
        val pay = product.priceCents * boundedQuantity
        val original = product.originalPriceCents.coerceAtLeast(product.priceCents) * boundedQuantity
        CheckoutSummary(
            payCents = pay,
            originalCents = original,
            savedCents = (original - pay).coerceAtLeast(0),
            seconds = product.aiControlSeconds * boundedQuantity,
        )
    } else {
        val boundedMonths = months.coerceIn(1, product.maxQuantity.coerceAtLeast(1))
        val monthDiscountPermille = when {
            boundedMonths >= 12 -> 830
            boundedMonths >= 3 -> 950
            else -> 1000
        }
        val original = product.originalPriceCents.coerceAtLeast(product.priceCents) * boundedMonths
        val campaignPrice = product.priceCents * boundedMonths
        val pay = (campaignPrice * monthDiscountPermille / 1000f).roundToInt()
        CheckoutSummary(
            payCents = pay,
            originalCents = original,
            savedCents = (original - pay).coerceAtLeast(0),
            seconds = product.aiControlSeconds * boundedMonths,
        )
    }
}

internal fun defaultProducts(): List<Product> = listOf(
    Product(
        id = "free_month",
        type = "monthly",
        level = "free",
        name = "免费版",
        priceCents = 0,
        aiControlSeconds = 40 * 60,
        purchasable = false,
        tagline = "低频够用",
    ),
    Product(
        id = "lite_month",
        type = "monthly",
        level = "lite",
        name = "轻享月卡",
        priceCents = 490,
        aiControlSeconds = 120 * 60,
        maxQuantity = 12,
        tagline = "轻度体验",
    ),
    Product(
        id = "standard_month",
        type = "monthly",
        level = "standard",
        name = "标准月卡",
        priceCents = 990,
        originalPriceCents = 1490,
        aiControlSeconds = 300 * 60,
        maxQuantity = 12,
        tagline = "多数用户会选它",
        highlight = true,
    ),
    Product(
        id = "support_month",
        type = "monthly",
        level = "support",
        name = "支持月卡",
        priceCents = 1990,
        originalPriceCents = 2990,
        aiControlSeconds = 900 * 60,
        maxQuantity = 12,
        tagline = "高频与支持者",
    ),
    Product(
        id = "play_month",
        type = "monthly",
        level = "play",
        name = "畅玩月卡",
        priceCents = 2990,
        originalPriceCents = 3990,
        aiControlSeconds = 3000 * 60,
        maxQuantity = 12,
        tagline = "大流量安心用",
    ),
    Product(
        id = "addon_trial",
        type = "addon",
        level = "addon",
        name = "体验包",
        priceCents = 190,
        aiControlSeconds = 60 * 60,
        validDays = 180,
        maxQuantity = 1,
        maxHoldSeconds = 3000 * 60,
        oneTimeLimit = true,
        tagline = "新用户体验",
    ),
    Product(
        id = "addon_small",
        type = "addon",
        level = "addon",
        name = "小加量",
        priceCents = 690,
        aiControlSeconds = 100 * 60,
        validDays = 180,
        maxQuantity = 10,
        maxHoldSeconds = 3000 * 60,
        tagline = "轻量补充",
    ),
    Product(
        id = "addon_standard",
        type = "addon",
        level = "addon",
        name = "标准加量",
        priceCents = 1690,
        aiControlSeconds = 300 * 60,
        validDays = 180,
        maxQuantity = 3,
        maxHoldSeconds = 3000 * 60,
        tagline = "按需加量",
        highlight = true,
    ),
    Product(
        id = "addon_large",
        type = "addon",
        level = "addon",
        name = "大加量",
        priceCents = 4990,
        aiControlSeconds = 1000 * 60,
        validDays = 180,
        maxQuantity = 1,
        maxHoldSeconds = 3000 * 60,
        tagline = "更低单价",
    ),
)

internal fun totalRemainingSeconds(entitlement: Entitlement): Int =
    entitlement.aiTotalSecondsRemaining.takeIf { it > 0 }
        ?: (entitlement.aiQuotaSecondsRemaining + entitlement.aiAddonSecondsRemaining).coerceAtLeast(0)

internal fun formatMinutes(seconds: Int): String {
    val minutes = (seconds / 60f).roundToInt().coerceAtLeast(0)
    return "${minutes} 分钟"
}

internal fun formatMinuteNumber(seconds: Int): String =
    (seconds / 60f).roundToInt().coerceAtLeast(0).toString()

internal fun formatPrice(priceCents: Int): String =
    if (priceCents % 100 == 0) {
        (priceCents / 100).toString()
    } else {
        "${priceCents / 100}.${(priceCents % 100).toString().padStart(2, '0')}"
    }

internal fun levelRank(level: String): Int = when (level) {
    "lite" -> 1
    "standard" -> 2
    "support" -> 3
    "play" -> 4
    else -> 0
}

internal fun isDowngrade(currentLevel: String, targetLevel: String): Boolean =
    levelRank(currentLevel) > levelRank(targetLevel)

internal fun displayName(displayName: String, username: String): String =
    displayName.ifBlank { username.ifBlank { "AI Toy 用户" } }.take(12)

internal fun displayInitial(value: String): String =
    value.trim().firstOrNull()?.toString()?.uppercase().orEmpty().ifBlank { "A" }

internal fun activeCampaign(product: Product): Boolean =
    product.campaignEndsAtMs > nowMs() && product.originalPriceCents > product.priceCents
