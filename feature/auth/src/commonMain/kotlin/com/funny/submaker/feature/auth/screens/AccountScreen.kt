package com.funny.submaker.feature.auth.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.funny.submaker.core.prefs.SubMakerPrefs
import com.funny.submaker.feature.auth.AuthViewModel
import kotlin.math.max

@Composable
fun AccountScreen(
    vm: AuthViewModel,
    onBack: (() -> Unit)?,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val user = SubMakerPrefs.user
    val accountEntitlement = user.entitlement

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack) { Text("返回") }
        }
        Text(text = "账号与权益", style = MaterialTheme.typography.headlineSmall)

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "邮箱：${user.email}")
                Text(text = "账号：${user.username}")
                Text(text = "专业版：${if (accountEntitlement.proActive) "已激活" else "未激活"}")
                Text(text = "账号试用：${max(accountEntitlement.trialSecondsRemaining, 0)} 秒")
                Text(text = "设备试用：${vm.trialSecondsRemaining} 秒")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { vm.refreshMe(); vm.loadDeviceStatus() }, enabled = !vm.busy) {
                        Text("刷新")
                    }
                    TextButton(onClick = { vm.logout(); onLogout() }) {
                        Text("退出登录")
                    }
                }
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "服务端", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = SubMakerPrefs.serverBaseUrl,
                    onValueChange = { SubMakerPrefs.serverBaseUrl = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = SubMakerPrefs.apiPrefix,
                    onValueChange = { SubMakerPrefs.apiPrefix = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Prefix") },
                    singleLine = true,
                )
            }
        }

        if (vm.products.isNotEmpty()) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "购买专业版", style = MaterialTheme.typography.titleMedium)
                    vm.products.forEach { product ->
                        Button(
                            onClick = { vm.buy(product) },
                            enabled = !vm.paying,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("${product.name}（￥${product.priceCents / 100.0}）")
                        }
                    }
                }
            }
        }
    }
}
