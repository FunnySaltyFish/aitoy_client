package com.funny.submaker.feature.auth.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.funny.submaker.feature.auth.AuthViewModel
import com.funny.submaker.feature.auth.components.AuthPerkItem
import com.funny.submaker.feature.auth.components.AuthPanelShape
import com.funny.submaker.feature.auth.components.DefaultAuthPerks
import com.funny.submaker.feature.auth.components.rememberAuthUiTokens

@Composable
fun LoginScreen(
    vm: AuthViewModel,
    onBack: (() -> Unit)?,
    onCodeSent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        vm.bootstrap()
        if (vm.products.isEmpty()) vm.loadProducts()
    }
    val tokens = rememberAuthUiTokens()
    val emailError = vm.emailFormatError(vm.email)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.pageBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onBack?.invoke() }, enabled = onBack != null) {
                Text("✕")
            }
            Text(
                text = "创始内测",
                style = MaterialTheme.typography.labelMedium,
                color = tokens.subtleTextColor,
            )
        }

        Text(
            text = "登录后继续你的项目",
            style = MaterialTheme.typography.headlineMedium,
            color = tokens.titleColor,
        )
        Text(
            text = "登录后可保存当前工程，并锁定创始内测资格。",
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.subtitleColor,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, tokens.panelBorder, AuthPanelShape)
                .background(tokens.panelColor, AuthPanelShape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "邮箱地址",
                style = MaterialTheme.typography.labelLarge,
                color = tokens.subtitleColor,
            )
            OutlinedTextField(
                value = vm.email,
                onValueChange = {
                    vm.email = it.trim()
                    vm.clearMessages()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                placeholder = { Text("name@example.com") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                isError = emailError != null,
                supportingText = {
                    val text = emailError
                    if (text != null) {
                        Text(text = text)
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = tokens.otpActiveBorder,
                    unfocusedBorderColor = tokens.panelBorder,
                    errorBorderColor = MaterialTheme.colorScheme.error,
                    focusedTextColor = tokens.titleColor,
                    unfocusedTextColor = tokens.titleColor,
                    focusedPlaceholderColor = tokens.subtitleColor,
                    unfocusedPlaceholderColor = tokens.subtitleColor,
                    errorSupportingTextColor = MaterialTheme.colorScheme.error,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
            )
            Button(
                onClick = { vm.sendLoginCode(onSuccess = onCodeSent) },
                enabled = !vm.busy && emailError == null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContentColor = tokens.subtitleColor,
                ),
            ) {
                Text(if (vm.busy) "发送中..." else "发送验证码")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Divider(modifier = Modifier.weight(1f))
            Text(
                text = "登录权益",
                style = MaterialTheme.typography.labelMedium,
                color = tokens.subtleTextColor,
            )
            Divider(modifier = Modifier.weight(1f))
        }

        DefaultAuthPerks.forEach { perk ->
            AuthPerkItem(
                title = perk.title,
                desc = perk.desc,
                badge = perk.badge,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "继续即表示你同意《服务条款》与《隐私政策》。",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.subtleTextColor,
        )
    }
}
