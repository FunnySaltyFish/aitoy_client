package com.funny.submaker.feature.auth.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.funny.submaker.feature.auth.components.AuthPerkItem
import com.funny.submaker.feature.auth.components.DefaultAuthPerks

@Composable
fun BetaWelcomeScreen(
    onEnterWorkspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ),
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text = "欢迎加入内测", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Founding Beta 已激活，以下特权已开放。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        DefaultAuthPerks.forEach { perk ->
            AuthPerkItem(title = perk.title, desc = perk.desc, badge = perk.badge)
        }

        Button(onClick = onEnterWorkspace, modifier = Modifier.fillMaxWidth()) {
            Text("进入工作台")
        }
    }
}
