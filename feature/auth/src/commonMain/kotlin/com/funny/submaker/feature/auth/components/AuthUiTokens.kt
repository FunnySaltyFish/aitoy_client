package com.funny.submaker.feature.auth.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class AuthUiTokens(
    val pageBackground: Brush,
    val panelColor: Color,
    val panelBorder: Color,
    val panelTint: Brush,
    val titleColor: Color,
    val subtitleColor: Color,
    val subtleTextColor: Color,
    val otpActiveBorder: Color,
    val otpInactiveBorder: Color,
    val otpFilledBackground: Color,
    val listItemContainer: Color,
    val listLeadingContainer: Color,
    val listLeadingContent: Color,
    val pillContainer: Color,
    val pillContent: Color,
)

val AuthPanelShape = RoundedCornerShape(24.dp)
val AuthCardShape = RoundedCornerShape(16.dp)

@Composable
fun rememberAuthUiTokens(): AuthUiTokens {
    val scheme = MaterialTheme.colorScheme
    return AuthUiTokens(
        pageBackground = Brush.verticalGradient(
            colors = listOf(scheme.background, scheme.surface),
        ),
        panelColor = scheme.surface,
        panelBorder = scheme.outlineVariant,
        panelTint = Brush.linearGradient(
            colors = listOf(
                scheme.primary.copy(alpha = 0.14f),
                scheme.surface.copy(alpha = 0f),
            ),
        ),
        titleColor = scheme.onSurface,
        subtitleColor = scheme.onSurfaceVariant,
        subtleTextColor = scheme.onSurfaceVariant.copy(alpha = 0.82f),
        otpActiveBorder = scheme.primary,
        otpInactiveBorder = scheme.outline,
        otpFilledBackground = scheme.primaryContainer.copy(alpha = 0.22f),
        listItemContainer = scheme.surfaceContainerLow,
        listLeadingContainer = scheme.primaryContainer.copy(alpha = 0.72f),
        listLeadingContent = scheme.onPrimaryContainer,
        pillContainer = scheme.primaryContainer.copy(alpha = 0.55f),
        pillContent = scheme.primary,
    )
}
