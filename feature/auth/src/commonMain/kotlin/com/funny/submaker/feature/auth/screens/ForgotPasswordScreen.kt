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
fun ForgotPasswordScreen(
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
        Text(text = "重置密码", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "先设置新密码，再发送验证码进行确认。",
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
                OutlinedTextField(
                    value = vm.newPassword,
                    onValueChange = {
                        vm.newPassword = it
                        vm.clearMessages()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("新密码") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = vm.repeatPassword,
                    onValueChange = {
                        vm.repeatPassword = it
                        vm.clearMessages()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("重复新密码") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                )
                Button(
                    onClick = { vm.sendResetPasswordCode(onSuccess = onCodeSent) },
                    enabled = !vm.busy && vm.email.isNotBlank() && vm.newPassword.isNotBlank() && vm.repeatPassword.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (vm.busy) "发送中..." else "发送验证码")
                }
            }
        }
    }
}
