package com.funny.submaker.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun AuthScreen(
    vm: AuthViewModel,
    onSendCode: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "登录 / 注册",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "使用邮箱验证码登录。无需指纹、无需复杂绑定。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = vm.email,
                    onValueChange = {
                        vm.email = it
                        vm.clearError()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("邮箱") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                )

                Button(
                    onClick = onSendCode,
                    enabled = vm.email.isNotBlank() && !vm.sending,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (vm.sending) "发送中…" else "发送验证码")
                }

                OutlinedTextField(
                    value = vm.verifyCode,
                    onValueChange = {
                        vm.verifyCode = it
                        vm.clearError()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("验证码") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                )

                Button(
                    onClick = onLogin,
                    enabled = vm.email.isNotBlank() && vm.verifyCode.isNotBlank() && !vm.loggingIn,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (vm.loggingIn) "登录中…" else "登录")
                }

                val errorMessage = vm.errorMessage
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

