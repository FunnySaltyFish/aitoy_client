package com.funny.submaker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.funny.submaker.feature.asr.AsrScreen
import com.funny.submaker.feature.asr.AsrViewModel
import com.funny.submaker.feature.auth.AuthScreen
import com.funny.submaker.feature.auth.AuthViewModel
import com.funny.submaker.ui.theme.SubMakerTheme

@Composable
@Preview
fun App() {
    SubMakerTheme {
        val asrVm = remember { AsrViewModel() }
        val authVm = remember { AuthViewModel() }

        var screen by remember { mutableStateOf(RootScreen.Asr) }

        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail {
                Text(
                    text = "SubMaker",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
                NavigationRailItem(
                    selected = screen == RootScreen.Asr,
                    onClick = { screen = RootScreen.Asr },
                    icon = { Text("ASR") },
                    label = { Text("ASR") },
                )
                NavigationRailItem(
                    selected = screen == RootScreen.Auth,
                    onClick = { screen = RootScreen.Auth },
                    icon = { Text("ID") },
                    label = { Text("账号") },
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (screen) {
                    RootScreen.Asr -> AsrScreen(
                        vm = asrVm,
                        onStart = {
                            asrVm.lastResult = "这里会接入云端 ASR：导入媒体 → 上传/直连 → 获取时间轴 → 导出 SRT/VTT/ASS"
                        },
                    )

                    RootScreen.Auth -> AuthScreen(
                        vm = authVm,
                        onSendCode = {
                            authVm.sending = true
                            authVm.errorMessage = "MVP：稍后接入服务端发送验证码接口"
                            authVm.sending = false
                        },
                        onLogin = {
                            authVm.loggingIn = true
                            authVm.errorMessage = "MVP：稍后接入服务端登录接口"
                            authVm.loggingIn = false
                        },
                    )
                }
            }
        }
    }
}

private enum class RootScreen {
    Asr,
    Auth,
}
