package com.funny.submaker.feature.auth.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.funny.submaker.feature.auth.AuthViewModel

@Composable
fun FindUsernameScreen(
    vm: AuthViewModel,
    onBack: () -> Unit,
    onCodeSent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack) { Text("返回登录") }
        Text(text = "找回账号", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "通过邮箱验证码查询用户名。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                Button(
                    onClick = { vm.sendFindUsernameCode(onSuccess = onCodeSent) },
                    enabled = !vm.busy && vm.email.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (vm.busy) "发送中..." else "发送验证码")
                }
            }
        }
    }
}
