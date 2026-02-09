package com.funny.submaker.feature.auth.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.funny.submaker.feature.auth.AuthViewModel
import com.funny.submaker.feature.auth.components.OtpCodeField

@Composable
fun VerifyCodeScreen(
    vm: AuthViewModel,
    purpose: VerifyPurpose,
    onBack: () -> Unit,
    onVerified: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var code by remember { mutableStateOf(vm.verifyCode.filter(Char::isDigit).take(6)) }
    var pendingVerify by remember { mutableStateOf(false) }
    var errorTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(code) {
        vm.verifyCode = code
        if (code.length == 6 && !vm.busy) {
            pendingVerify = true
            vm.clearMessages()
            when (purpose) {
                VerifyPurpose.Register -> vm.register(onSuccess = { onVerified(null) })
                VerifyPurpose.FindUsername -> vm.findUsername(onSuccess = { onVerified(it) })
                VerifyPurpose.ResetPassword -> vm.resetPassword(onSuccess = { onVerified(null) })
            }
        }
    }

    LaunchedEffect(vm.busy, pendingVerify, vm.errorMessage) {
        if (!vm.busy && pendingVerify) {
            pendingVerify = false
            if (vm.errorMessage != null) {
                errorTrigger += 1
                if (code.isNotEmpty()) code = ""
            }
        }
    }

    Column(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack) { Text("返回") }
        Text(text = "输入验证码", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "验证码已发送至 ${vm.email}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OtpCodeField(
            value = code,
            onValueChange = { next ->
                code = next
                vm.clearMessages()
            },
            errorTrigger = errorTrigger,
            modifier = Modifier.fillMaxWidth(),
            onErrorFeedback = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
        )

        Text(
            text = if (vm.busy) "校验中..." else "输入 6 位数字后将自动校验",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = {
                when (purpose) {
                    VerifyPurpose.Register -> vm.sendRegisterCode()
                    VerifyPurpose.FindUsername -> vm.sendFindUsernameCode()
                    VerifyPurpose.ResetPassword -> vm.sendResetPasswordCode()
                }
            },
            enabled = !vm.busy,
        ) {
            Text("重新发送验证码")
        }
    }
}

enum class VerifyPurpose {
    Register,
    FindUsername,
    ResetPassword,
}
