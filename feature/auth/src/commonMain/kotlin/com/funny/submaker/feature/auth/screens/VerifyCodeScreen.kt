package com.funny.submaker.feature.auth.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import com.funny.submaker.feature.auth.components.rememberAuthUiTokens
import kotlinx.coroutines.delay

@Composable
fun VerifyCodeScreen(
    vm: AuthViewModel,
    onBack: () -> Unit,
    onVerified: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val tokens = rememberAuthUiTokens()
    var code by remember { mutableStateOf(vm.verifyCode.filter(Char::isDigit).take(6)) }
    var pendingVerify by remember { mutableStateOf(false) }
    var errorTrigger by remember { mutableIntStateOf(0) }
    var resendCountdown by remember { mutableIntStateOf(59) }

    LaunchedEffect(code) {
        vm.verifyCode = code
        if (code.length == 6 && !vm.busy) {
            pendingVerify = true
            vm.clearMessages()
            vm.loginWithCode(onSuccess = onVerified)
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

    LaunchedEffect(resendCountdown) {
        if (resendCountdown > 0) {
            delay(1_000)
            resendCountdown -= 1
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.pageBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) {
            Text("←")
        }
        Text(
            text = "输入验证码",
            style = MaterialTheme.typography.headlineMedium,
            color = tokens.titleColor,
        )
        Text(
            text = "验证码已发送至 ${vm.email}",
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.subtitleColor,
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            Text(
                text = "没有收到验证码？",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.subtitleColor,
            )
            TextButton(
                onClick = {
                    vm.sendLoginCode()
                    resendCountdown = 59
                },
                enabled = !vm.busy && resendCountdown <= 0,
            ) {
                val label = if (resendCountdown > 0) "重新发送（${resendCountdown}s）" else "重新发送"
                Text(label)
            }
        }
    }
}
