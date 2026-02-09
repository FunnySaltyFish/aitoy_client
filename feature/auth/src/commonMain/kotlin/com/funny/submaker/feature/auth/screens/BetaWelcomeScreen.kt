package com.funny.submaker.feature.auth.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.funny.submaker.feature.auth.components.AuthPerkItem
import com.funny.submaker.feature.auth.components.AuthPanelShape
import com.funny.submaker.feature.auth.components.DefaultAuthPerks
import com.funny.submaker.feature.auth.components.rememberAuthUiTokens

@Composable
fun BetaWelcomeScreen(
    onEnterWorkspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAuthUiTokens()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.pageBackground)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "欢迎加入",
            style = MaterialTheme.typography.headlineMedium,
            color = tokens.titleColor,
        )
        Text(
            text = "SUBTITLE PRO BETA PROGRAM",
            style = MaterialTheme.typography.labelLarge,
            color = tokens.subtleTextColor,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, tokens.panelBorder, AuthPanelShape)
                .background(tokens.panelColor, AuthPanelShape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Founding\nBeta Member",
                style = MaterialTheme.typography.headlineSmall,
                color = tokens.titleColor,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "EXCLUSIVE ACCESS",
                style = MaterialTheme.typography.labelLarge,
                color = tokens.pillContent,
            )
            Text(
                text = "MEMBER ID",
                style = MaterialTheme.typography.labelMedium,
                color = tokens.subtleTextColor,
            )
            Text(
                text = "#0821",
                style = MaterialTheme.typography.headlineMedium,
                color = tokens.titleColor,
            )
        }

        Text(
            text = "您的特权 / YOUR PERKS",
            style = MaterialTheme.typography.labelLarge,
            color = tokens.subtleTextColor,
        )
        DefaultAuthPerks.forEach { perk ->
            AuthPerkItem(
                title = perk.title,
                desc = perk.desc,
                badge = perk.badge,
            )
        }

        Button(
            onClick = onEnterWorkspace,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text("进入工作台  →")
        }
    }
}
