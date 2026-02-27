package com.funny.submaker.feature.asr.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.funny.submaker.feature.asr.AsrRoundButtonShape
import com.funny.submaker.feature.asr.rememberAsrUiTokens

@Composable
fun AsrWizardBottomActions(
    primaryLabel: String,
    primaryIcon: ImageVector,
    onPrimaryClick: () -> Unit,
    secondaryLabel: String,
    secondaryIcon: ImageVector,
    onSecondaryClick: () -> Unit,
    modifier: Modifier = Modifier,
    elevatedStyle: Boolean = false,
) {
    val tokens = rememberAsrUiTokens()
    val containerColor = if (elevatedStyle) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        shadowElevation = if (elevatedStyle) 12.dp else 0.dp,
        shape = if (elevatedStyle) RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 24.dp
        ) else RoundedCornerShape(0.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (elevatedStyle) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .background(
                            tokens.mutedText.copy(alpha = 0.3f),
                            RoundedCornerShape(999.dp)
                        ),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = if (elevatedStyle) 20.dp else 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onSecondaryClick,
                    shape = AsrRoundButtonShape,
                    modifier = Modifier.weight(1f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        tokens.secondaryButtonBorder
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.secondaryButtonText),
                ) {
                    Icon(
                        imageVector = secondaryIcon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(text = secondaryLabel)
                }
                Button(
                    onClick = onPrimaryClick,
                    shape = AsrRoundButtonShape,
                    modifier = Modifier.weight(2f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.primaryButtonContainer,
                        contentColor = tokens.primaryButtonText,
                    ),
                ) {
                    Icon(
                        imageVector = primaryIcon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(text = primaryLabel)
                }
            }
        }
    }
}

@Composable
fun AsrFieldLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val tokens = rememberAsrUiTokens()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = tokens.sectionLabel,
            style = MaterialTheme.typography.labelSmall,
        )
        trailing?.invoke()
    }
}

@Composable
fun AsrInfoCard(
    title: String,
    content: String,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable () -> Unit,
) {
    val tokens = rememberAsrUiTokens()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(tokens.infoCardContainer, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(modifier = Modifier.padding(top = 1.dp)) {
            leadingIcon()
        }
        androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = tokens.bodyText,
            )
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.mutedText,
            )
        }
    }
}
