package com.funny.aitoy.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.funny.aitoy.core.navigation.LocalNavigator

@Composable
internal fun AccountRedeemCodeScreen(
    initialCode: String = "",
) {
    val vm = viewModel { AccountRedeemViewModel() }
    val navigator = LocalNavigator.current
    LaunchedEffect(initialCode) {
        if (initialCode.isNotBlank()) vm.setInitialCode(initialCode)
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PageHeader(title = "兑换码", onBack = { navigator.popBackStack() })
        }
        item {
            Panel(title = "兑换码", action = "活动或赠送") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.CardGiftcard, null, tint = Honey)
                    Column(Modifier.padding(start = 10.dp)) {
                        Text("输入兑换码", color = TextMain, fontWeight = FontWeight.Black)
                        Text("兑换成功后，额度会加入当前账号。", color = TextSoft, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = vm.codeDraft,
                    onValueChange = vm::updateCode,
                    singleLine = true,
                    placeholder = { Text("输入兑换码", color = TextSoft) },
                    keyboardActions = KeyboardActions(onDone = { vm.redeem() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = vm::redeem,
                    enabled = !vm.loading && vm.codeDraft.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Honey,
                        contentColor = Ink,
                        disabledContainerColor = Line,
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("兑换", fontWeight = FontWeight.Black)
                }
            }
        }
        if (vm.loading) {
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Rose) }
        }
        item {
            Panel(title = "兑换说明", action = "使用前确认") {
                Text("兑换码只能使用一次，请确认当前账号无误后再兑换。", color = TextMain)
                Spacer(Modifier.height(8.dp))
                Text("月度额度会随会员周期使用，加量包会按页面展示的有效期保留。", color = TextSoft, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
