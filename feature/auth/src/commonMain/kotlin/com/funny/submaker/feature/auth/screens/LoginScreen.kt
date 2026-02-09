package com.funny.submaker.feature.auth.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.funny.submaker.feature.auth.AuthViewModel
import com.funny.submaker.feature.auth.components.AuthPerkItem
import com.funny.submaker.feature.auth.components.DefaultAuthPerks

@Composable
fun LoginScreen(
    vm: AuthViewModel,
    onBack: (() -> Unit)?,
    onLoggedIn: () -> Unit,
    onCodeSent: () -> Unit,
    onOpenForgotPassword: () -> Unit,
    onOpenFindUsername: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        vm.bootstrap()
        if (vm.products.isEmpty()) vm.loadProducts()
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onBack?.invoke() }, enabled = onBack != null) {
                Text("关闭")
            }
            Text(
                text = "Founding Beta",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(text = "登录以继续", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "保存当前项目并同步到云端，继续你的字幕工作流。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = vm.email,
                    onValueChange = {
                        vm.email = it.trim()
                        vm.clearMessages()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("邮箱") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = vm.password,
                    onValueChange = {
                        vm.password = it
                        vm.clearMessages()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("密码") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                )
                Button(
                    onClick = { vm.login(onSuccess = onLoggedIn) },
                    enabled = !vm.busy && vm.email.isNotBlank() && vm.password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (vm.busy) "登录中..." else "登录")
                }
                TextButton(
                    onClick = { vm.sendRegisterCode(onSuccess = onCodeSent) },
                    enabled = !vm.busy && vm.email.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("发送验证码")
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onOpenFindUsername) { Text("找回账号") }
                    TextButton(onClick = onOpenForgotPassword) { Text("忘记密码") }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))
        Divider()
        Text(
            text = "登录后可获得",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DefaultAuthPerks.forEach { perk ->
            AuthPerkItem(title = perk.title, desc = perk.desc, badge = perk.badge)
        }
    }
}
