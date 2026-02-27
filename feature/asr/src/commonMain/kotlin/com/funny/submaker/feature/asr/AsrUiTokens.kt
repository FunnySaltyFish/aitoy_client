package com.funny.submaker.feature.asr

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class AsrUiTokens(
    val pageBackground: Brush,
    val topBarBorder: Color,
    val titleText: Color,
    val bodyText: Color,
    val mutedText: Color,
    val activeStepContainer: Color,
    val activeStepText: Color,
    val inactiveStepContainer: Color,
    val inactiveStepText: Color,
    val uploadCardContainer: Color,
    val uploadCardBorder: Color,
    val uploadIconContainer: Color,
    val recentCardContainer: Brush,
    val recentCardLeading: Color,
    val selectedCardContainer: Brush,
    val secondaryButtonBorder: Color,
    val secondaryButtonText: Color,
    val primaryButtonContainer: Color,
    val primaryButtonText: Color,
    val chipContainer: Color,
    val chipText: Color,
    val sectionLabel: Color,
    val panelContainer: Color,
    val inputCardContainer: Color,
    val inputLeadingContainer: Color,
    val modeSelectedContainer: Color,
    val modeSelectedText: Color,
    val modelSelectedContainer: Color,
    val modelSelectedText: Color,
    val infoCardContainer: Color,
    val switchTrack: Color,
    val switchThumb: Color,
    val sliderTrack: Color,
)

val AsrTopBarHeight = 64.dp
val AsrRoundButtonShape = RoundedCornerShape(999.dp)
val AsrCardShape = RoundedCornerShape(22.dp)
val AsrUploadShape = RoundedCornerShape(26.dp)
val AsrStepShape = CircleShape

private val LocalAsrUiTokens = staticCompositionLocalOf<AsrUiTokens> {
    error("AsrUiTokens not provided")
}

@Composable
fun ProvideAsrUiTokens(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tokens = remember(scheme) {
        AsrUiTokens(
            pageBackground = Brush.verticalGradient(
                colors = listOf(
                    scheme.background,
                    scheme.surface,
                ),
            ),
            topBarBorder = scheme.outlineVariant.copy(alpha = 0.34f),
            titleText = scheme.onSurface,
            bodyText = scheme.onSurface,
            mutedText = scheme.onSurfaceVariant,
            activeStepContainer = scheme.primary,
            activeStepText = scheme.onPrimary,
            inactiveStepContainer = scheme.surfaceContainerHigh,
            inactiveStepText = scheme.onSurfaceVariant,
            uploadCardContainer = scheme.surfaceContainerLow,
            uploadCardBorder = scheme.outline.copy(alpha = 0.72f),
            uploadIconContainer = scheme.primary.copy(alpha = 0.55f),
            recentCardContainer = Brush.horizontalGradient(
                colors = listOf(
                    scheme.surfaceContainerLow,
                    scheme.surfaceContainer,
                ),
            ),
            recentCardLeading = scheme.surfaceContainerHighest,
            selectedCardContainer = Brush.horizontalGradient(
                colors = listOf(
                    scheme.surfaceContainerLow,
                    scheme.surfaceContainerHigh,
                ),
            ),
            secondaryButtonBorder = scheme.outline,
            secondaryButtonText = scheme.onSurfaceVariant,
            primaryButtonContainer = scheme.primaryContainer,
            primaryButtonText = scheme.onPrimaryContainer,
            chipContainer = scheme.surfaceContainerHighest,
            chipText = scheme.onSurfaceVariant,
            sectionLabel = scheme.primary,
            panelContainer = scheme.surfaceContainer,
            inputCardContainer = scheme.surfaceContainer,
            inputLeadingContainer = scheme.surfaceContainerHighest,
            modeSelectedContainer = scheme.surfaceContainerHigh,
            modeSelectedText = scheme.primary,
            modelSelectedContainer = scheme.primaryContainer,
            modelSelectedText = scheme.onPrimaryContainer,
            infoCardContainer = scheme.secondaryContainer.copy(alpha = 0.2f),
            switchTrack = scheme.surfaceContainerHighest,
            switchThumb = scheme.onSurface,
            sliderTrack = scheme.outline.copy(alpha = 0.44f),
        )
    }
    CompositionLocalProvider(LocalAsrUiTokens provides tokens, content = content)
}

@Composable
fun rememberAsrUiTokens(): AsrUiTokens {
    return LocalAsrUiTokens.current
}
