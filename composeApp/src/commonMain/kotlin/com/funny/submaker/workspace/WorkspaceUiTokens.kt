package com.funny.submaker.workspace

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class WorkspaceUiTokens(
    val pageBackground: Brush,
    val sidePanelColor: Color,
    val sidePanelBorder: Color,
    val navigationContainer: Color,
    val heroCardColor: Color,
    val heroAccent: Color,
    val heroAccentText: Color,
    val listCardColor: Color,
    val listCardBorder: Color,
    val selectedCardBorder: Color,
    val titleColor: Color,
    val bodyColor: Color,
    val weakTextColor: Color,
    val mutedContainer: Color,
    val mutedContainerText: Color,
    val primaryChipContainer: Color,
    val primaryChipText: Color,
    val successContainer: Color,
    val successText: Color,
    val warningContainer: Color,
    val warningText: Color,
    val dangerContainer: Color,
    val dangerText: Color,
)

val WorkspacePanelShape = RoundedCornerShape(28.dp)
val WorkspaceCardShape = RoundedCornerShape(20.dp)
val WorkspaceChipShape = RoundedCornerShape(999.dp)

@Composable
fun rememberWorkspaceUiTokens(): WorkspaceUiTokens {
    val scheme = MaterialTheme.colorScheme
    return WorkspaceUiTokens(
        pageBackground = Brush.verticalGradient(
            colors = listOf(
                scheme.background,
                scheme.surfaceContainerLowest,
            ),
        ),
        sidePanelColor = scheme.surface,
        sidePanelBorder = scheme.outlineVariant,
        navigationContainer = scheme.surfaceContainerLow,
        heroCardColor = scheme.primaryContainer.copy(alpha = 0.54f),
        heroAccent = scheme.primary,
        heroAccentText = scheme.onPrimary,
        listCardColor = scheme.surfaceContainerLow,
        listCardBorder = scheme.outlineVariant.copy(alpha = 0.74f),
        selectedCardBorder = scheme.primary,
        titleColor = scheme.onSurface,
        bodyColor = scheme.onSurface,
        weakTextColor = scheme.onSurfaceVariant,
        mutedContainer = scheme.surfaceContainerHighest,
        mutedContainerText = scheme.onSurfaceVariant,
        primaryChipContainer = scheme.secondaryContainer,
        primaryChipText = scheme.onSecondaryContainer,
        successContainer = scheme.tertiaryContainer,
        successText = scheme.onTertiaryContainer,
        warningContainer = scheme.secondaryContainer,
        warningText = scheme.onSecondaryContainer,
        dangerContainer = scheme.errorContainer,
        dangerText = scheme.onErrorContainer,
    )
}
