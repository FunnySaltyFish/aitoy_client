package com.funny.submaker.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.funny.submaker.core.prefs.SubMakerPrefs
import kotlin.math.max

@Composable
fun AuthScreen(
    vm: AuthViewModel,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        vm.bootstrap()
        if (vm.products.isEmpty()) vm.loadProducts()
        if (vm.isLoggedIn) vm.page = AuthPage.Account
    }

    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "账号与权益",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "支持未登录试用；登录后自动同步到账号记录。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ServerConfigCard()
        TrialCard(vm)

        when (vm.page) {
            AuthPage.Account -> AccountCard(vm)
            AuthPage.Login -> LoginCard(vm)
            AuthPage.Register -> RegisterCard(vm)
            AuthPage.ForgotUsername -> ForgotUsernameCard(vm)
            AuthPage.ForgotPassword -> ForgotPasswordCard(vm)
        }

        val msg = vm.infoMessage
        if (msg != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = msg,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        val err = vm.errorMessage
        if (err != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = err,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ServerConfigCard() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "服务端", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = SubMakerPrefs.serverBaseUrl,
                onValueChange = { SubMakerPrefs.serverBaseUrl = it.trim() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Base URL") },
                supportingText = { Text("示例：http://127.0.0.1:5002（Android 真机请用局域网 IP）") },
                singleLine = true,
            )
            OutlinedTextField(
                value = SubMakerPrefs.apiPrefix,
                onValueChange = { SubMakerPrefs.apiPrefix = it.trim() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Prefix") },
                supportingText = { Text("默认：/api/v1") },
                singleLine = true,
            )
        }
    }
}

@Composable
private fun TrialCard(vm: AuthViewModel) {
    val ent = vm.device.entitlement
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "本机试用", style = MaterialTheme.typography.titleMedium)
            Text(text = "deviceId：${vm.device.deviceId.take(8)}…")
            Text(text = "剩余：${max(ent.trialSecondsRemaining, 0)} 秒")
            TextButton(onClick = vm::loadDeviceStatus, enabled = !vm.busy) { Text("刷新试用状态") }
        }
    }
}

@Composable
private fun AccountCard(vm: AuthViewModel) {
    val user = SubMakerPrefs.user
    val ent = user.entitlement
    val deviceEnt = vm.device.entitlement

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "已登录", style = MaterialTheme.typography.titleMedium)
            Text(text = "邮箱：${user.email}")
            Text(text = "账号：${user.username}")
            Text(text = "专业版：${if (ent.proActive) "已激活" else "未激活"}")
            Text(text = "账号试用剩余：${max(ent.trialSecondsRemaining, 0)} 秒")
            Text(text = "本机试用剩余：${max(deviceEnt.trialSecondsRemaining, 0)} 秒（deviceId=${vm.device.deviceId.take(8)}…）")

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { vm.refreshMe(); vm.loadDeviceStatus() },
                    enabled = !vm.busy,
                ) {
                    Text("刷新")
                }
                TextButton(onClick = vm::logout) { Text("退出登录") }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "购买（MVP）", style = MaterialTheme.typography.titleSmall)
            if (vm.products.isEmpty()) {
                TextButton(onClick = vm::loadProducts) { Text("加载商品") }
            } else {
                vm.products.forEach { p ->
                    Button(
                        onClick = {
                            vm.buy(p)
                        },
                        enabled = !vm.busy && !vm.paying,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (vm.paying) "支付中…" else "${p.name}（￥${p.priceCents / 100.0}）")
                    }
                }
                val orderNo = vm.payingOrderNo
                if (orderNo != null) {
                    Text(text = "订单：$orderNo（轮询中…）", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LoginCard(vm: AuthViewModel) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "登录", style = MaterialTheme.typography.titleMedium)
            EmailField(value = vm.email, onValueChange = { vm.email = it; vm.clearMessages() })
            PasswordField(value = vm.password, onValueChange = { vm.password = it; vm.clearMessages() })

            Button(
                onClick = vm::login,
                enabled = !vm.busy && vm.email.isNotBlank() && vm.password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (vm.busy) "登录中…" else "登录")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { vm.page = AuthPage.Register; vm.clearMessages() }) { Text("去注册") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { vm.page = AuthPage.ForgotUsername; vm.clearMessages() }) { Text("忘记账号") }
                    TextButton(onClick = { vm.page = AuthPage.ForgotPassword; vm.clearMessages() }) { Text("忘记密码") }
                }
            }
        }
    }
}

@Composable
private fun RegisterCard(vm: AuthViewModel) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "注册", style = MaterialTheme.typography.titleMedium)
            EmailField(value = vm.email, onValueChange = { vm.email = it; vm.clearMessages() })
            OutlinedTextField(
                value = vm.username,
                onValueChange = { vm.username = it; vm.clearMessages() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("账号（可选）") },
                supportingText = { Text("3-16 位：字母/数字/下划线/中文") },
                singleLine = true,
            )
            PasswordField(value = vm.password, onValueChange = { vm.password = it; vm.clearMessages() })
            VerifyCodeRow(
                code = vm.verifyCode,
                onCodeChange = { vm.verifyCode = it; vm.clearMessages() },
                onSend = vm::sendRegisterCode,
                enabled = !vm.busy && vm.email.isNotBlank(),
            )
            Button(
                onClick = vm::register,
                enabled = !vm.busy && vm.email.isNotBlank() && vm.password.isNotBlank() && vm.verifyCode.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (vm.busy) "注册中…" else "注册并登录")
            }
            TextButton(onClick = { vm.page = AuthPage.Login; vm.clearMessages() }) { Text("返回登录") }
        }
    }
}

@Composable
private fun ForgotUsernameCard(vm: AuthViewModel) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "忘记账号", style = MaterialTheme.typography.titleMedium)
            EmailField(value = vm.email, onValueChange = { vm.email = it; vm.clearMessages() })
            VerifyCodeRow(
                code = vm.verifyCode,
                onCodeChange = { vm.verifyCode = it; vm.clearMessages() },
                onSend = vm::sendFindUsernameCode,
                enabled = !vm.busy && vm.email.isNotBlank(),
            )
            Button(
                onClick = vm::findUsername,
                enabled = !vm.busy && vm.email.isNotBlank() && vm.verifyCode.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (vm.busy) "查询中…" else "查询账号")
            }
            TextButton(onClick = { vm.page = AuthPage.Login; vm.clearMessages() }) { Text("返回登录") }
        }
    }
}

@Composable
private fun ForgotPasswordCard(vm: AuthViewModel) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "重置密码", style = MaterialTheme.typography.titleMedium)
            EmailField(value = vm.email, onValueChange = { vm.email = it; vm.clearMessages() })
            VerifyCodeRow(
                code = vm.verifyCode,
                onCodeChange = { vm.verifyCode = it; vm.clearMessages() },
                onSend = vm::sendResetPasswordCode,
                enabled = !vm.busy && vm.email.isNotBlank(),
            )
            PasswordField(
                value = vm.newPassword,
                onValueChange = { vm.newPassword = it; vm.clearMessages() },
                label = "新密码",
            )
            PasswordField(
                value = vm.repeatPassword,
                onValueChange = { vm.repeatPassword = it; vm.clearMessages() },
                label = "重复密码",
            )
            Button(
                onClick = vm::resetPassword,
                enabled = !vm.busy && vm.email.isNotBlank() && vm.verifyCode.isNotBlank() && vm.newPassword.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (vm.busy) "提交中…" else "提交重置")
            }
            TextButton(onClick = { vm.page = AuthPage.Login; vm.clearMessages() }) { Text("返回登录") }
        }
    }
}

@Composable
private fun EmailField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("邮箱") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        singleLine = true,
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "密码",
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
    )
}

@Composable
private fun VerifyCodeRow(
    code: String,
    onCodeChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            modifier = Modifier.weight(1f),
            label = { Text("验证码") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
        )
        Button(onClick = onSend, enabled = enabled) {
            Text("发送")
        }
    }
}
