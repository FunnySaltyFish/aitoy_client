package com.funny.submaker.feature.auth.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun AuthPerkItem(
    title: String,
    desc: String,
    badge: String,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAuthUiTokens()
    ListItem(
        modifier = modifier
            .clip(AuthCardShape),
        colors = ListItemDefaults.colors(
            containerColor = tokens.listItemContainer,
            headlineColor = tokens.titleColor,
            supportingColor = tokens.subtitleColor,
        ),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(tokens.listLeadingContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelLarge,
                    color = tokens.listLeadingContent,
                )
            }
        },
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
            )
        },
        supportingContent = {
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
            )
        },
    )
}
