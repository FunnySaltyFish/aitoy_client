package com.funny.submaker.ui.toast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.funny.submaker.core.kmp.GlobalToastCenter
import com.funny.submaker.core.kmp.ToastItem
import com.funny.submaker.core.kmp.ToastType
import kotlinx.coroutines.delay

@Composable
fun GlobalToastOverlay(modifier: Modifier = Modifier) {
    val items = GlobalToastCenter.items
    if (items.isEmpty()) return

    Column(
        modifier =
            modifier
                .padding(top = 16.dp, end = 16.dp)
                .widthIn(max = 420.dp)
                .zIndex(100f),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.End,
    ) {
        items.forEach { item ->
            key(item.id) {
                DesktopToastCard(
                    item = item,
                    onDismiss = { GlobalToastCenter.dismiss(item.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DesktopToastCard(
    item: ToastItem,
    onDismiss: () -> Unit,
) {
    var visible by remember(item.id) { mutableStateOf(true) }
    val colors = rememberToastColors(item.type)
    val dismissState =
        rememberSwipeToDismissBoxState(
            positionalThreshold = { totalDistance -> totalDistance * 0.3f },
            confirmValueChange = { value ->
                if (value != SwipeToDismissBoxValue.Settled) {
                    visible = false
                }
                true
            },
        )

    LaunchedEffect(item.id) {
        delay(item.type.autoDismissMs)
        visible = false
    }

    LaunchedEffect(visible) {
        if (!visible) {
            delay(160)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(
            animationSpec = spring(dampingRatio = 0.82f),
            initialScale = 0.96f
        ),
        exit = fadeOut() + scaleOut(targetScale = 0.96f),
    ) {
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = true,
            enableDismissFromEndToStart = true,
            backgroundContent = {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(colors.dismissBg)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "松手关闭",
                        color = colors.dismissText,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = colors.container,
                contentColor = colors.content,
                tonalElevation = 6.dp,
                shadowElevation = 10.dp,
            ) {
                Row(
                    modifier = Modifier.padding(
                        start = 14.dp,
                        end = 8.dp,
                        top = 12.dp,
                        bottom = 12.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(colors.accent),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = item.type.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.label,
                        )
                        Text(
                            text = item.message,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { visible = false }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "关闭提示",
                            tint = colors.icon,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberToastColors(type: ToastType): ToastColors {
    val scheme = MaterialTheme.colorScheme
    return when (type) {
        ToastType.Success ->
            ToastColors(
                container = scheme.secondaryContainer.copy(alpha = 0.92f),
                content = scheme.onSecondaryContainer,
                accent = scheme.secondary,
                label = scheme.onSecondaryContainer,
                icon = scheme.onSecondaryContainer.copy(alpha = 0.78f),
                dismissBg = scheme.secondaryContainer.copy(alpha = 0.52f),
                dismissText = scheme.onSecondaryContainer,
            )

        ToastType.Warning ->
            ToastColors(
                container = Color(0xFF3C2C12),
                content = Color(0xFFF8E3B3),
                accent = Color(0xFFF2C35D),
                label = Color(0xFFFFD98A),
                icon = Color(0xFFF8E3B3).copy(alpha = 0.84f),
                dismissBg = Color(0xFF5A421A),
                dismissText = Color(0xFFFBE9BE),
            )

        ToastType.Error ->
            ToastColors(
                container = scheme.errorContainer.copy(alpha = 0.9f),
                content = scheme.onErrorContainer,
                accent = scheme.error,
                label = scheme.error,
                icon = scheme.onErrorContainer.copy(alpha = 0.82f),
                dismissBg = scheme.error.copy(alpha = 0.22f),
                dismissText = scheme.onErrorContainer,
            )

        ToastType.Info ->
            ToastColors(
                container = scheme.primaryContainer.copy(alpha = 0.9f),
                content = scheme.onPrimaryContainer,
                accent = scheme.primary,
                label = scheme.primary,
                icon = scheme.onPrimaryContainer.copy(alpha = 0.82f),
                dismissBg = scheme.primary.copy(alpha = 0.18f),
                dismissText = scheme.onPrimaryContainer,
            )

        ToastType.Default ->
            ToastColors(
                container = scheme.surfaceContainerHigh.copy(alpha = 0.95f),
                content = scheme.onSurface,
                accent = scheme.primary.copy(alpha = 0.9f),
                label = scheme.onSurfaceVariant,
                icon = scheme.onSurfaceVariant.copy(alpha = 0.8f),
                dismissBg = scheme.surfaceContainerHighest.copy(alpha = 0.75f),
                dismissText = scheme.onSurfaceVariant,
            )
    }
}

private val ToastType.title: String
    get() =
        when (this) {
            ToastType.Default -> "提示"
            ToastType.Success -> "成功"
            ToastType.Warning -> "注意"
            ToastType.Error -> "错误"
            ToastType.Info -> "信息"
        }

private val ToastType.autoDismissMs: Long
    get() =
        when (this) {
            ToastType.Error -> 4200L
            ToastType.Warning -> 3600L
            else -> 2800L
        }

private data class ToastColors(
    val container: Color,
    val content: Color,
    val accent: Color,
    val label: Color,
    val icon: Color,
    val dismissBg: Color,
    val dismissText: Color,
)
