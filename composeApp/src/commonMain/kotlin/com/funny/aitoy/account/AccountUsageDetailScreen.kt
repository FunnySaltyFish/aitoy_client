package com.funny.aitoy.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.funny.aitoy.core.navigation.LocalNavigator

@Composable
internal fun AccountUsageDetailScreen() {
    val vm = viewModel { AccountUsageViewModel() }
    val navigator = LocalNavigator.current
    val ent = vm.user.entitlement
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PageHeader(title = "控制明细", onBack = { navigator.popBackStack() })
        }
        item {
            Panel(title = "额度汇总", action = "按实际控制结算") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryMetric(
                        "本月剩余",
                        formatMinutes(ent.aiQuotaSecondsRemaining),
                        Modifier.weight(1f),
                    )
                    SummaryMetric(
                        "加量包",
                        formatMinutes(ent.aiAddonSecondsRemaining),
                        Modifier.weight(1f),
                    )
                    SummaryMetric(
                        "总可用",
                        formatMinutes(totalRemainingSeconds(ent)),
                        Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "只在 AI 实际控制时消耗，连接、待机和停止不消耗。",
                    color = TextSoft,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            Panel(title = "记录", action = "近期") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("全部", "今天", "本周").forEachIndexed { index, text ->
                        SelectorChip(text, index == 0) {}
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.045f), RoundedCornerShape(14.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("暂无可查看的控制记录", color = TextMain, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("产生 AI 控制消耗后，会在这里按天和会话展示。", color = TextSoft, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
internal fun PageHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = TextMain)
        }
        Spacer(Modifier.width(8.dp))
        Text(title, color = TextMain, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.055f), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text(label, color = TextSoft, style = MaterialTheme.typography.labelMedium)
        Text(value, color = TextMain, fontWeight = FontWeight.Black, maxLines = 1)
    }
}
