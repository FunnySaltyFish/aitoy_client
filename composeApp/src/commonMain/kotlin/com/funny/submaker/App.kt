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
import com.funny.submaker.core.prefs.SubMakerPrefsInit
import com.funny.submaker.ui.theme.SubMakerTheme

@Composable
@Preview
fun App() {
    SubMakerTheme {
        remember { SubMakerPrefsInit.init() }
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
                            asrVm.startMockAsr()
                        },
                    )

                    RootScreen.Auth -> AuthScreen(
                        vm = authVm,
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
