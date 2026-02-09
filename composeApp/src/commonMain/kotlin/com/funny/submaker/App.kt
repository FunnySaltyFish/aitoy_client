package com.funny.submaker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.funny.submaker.core.navigation.LocalNavigator
import com.funny.submaker.core.navigation.NavigatorProvider
import com.funny.submaker.core.navigation.rememberListDetailStrategy
import com.funny.submaker.core.navigation.rememberNavigator
import com.funny.submaker.feature.asr.AsrScreen
import com.funny.submaker.feature.asr.AsrViewModel
import com.funny.submaker.feature.auth.AuthViewModel
import com.funny.submaker.navigation.AppRoute
import com.funny.submaker.navigation.AuthResult
import com.funny.submaker.navigation.Routes
import com.funny.submaker.navigation.addAuthRoutes
import com.funny.submaker.core.prefs.SubMakerPrefsInit
import com.funny.submaker.ui.theme.SubMakerTheme
import kotlinx.coroutines.launch

@Composable
@Preview
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun App() {
    SubMakerTheme {
        LaunchedEffect(Unit) { SubMakerPrefsInit.init() }
        val asrVm = remember { AsrViewModel() }
        val authVm = remember { AuthViewModel() }
        val navigator = rememberNavigator(Routes.Workspace, Routes.Asr())
        val scope = rememberCoroutineScope()
        val listDetailStrategy = rememberListDetailStrategy<NavKey>()
        val asrMessages = remember { mutableStateListOf<String>() }

        NavigatorProvider(navigator) {
            NavDisplay(
                backStack = navigator.backStack,
                onBack = navigator::popBackStack,
                sceneStrategy = listDetailStrategy,
                entryProvider = entryProvider {
                    entry<Routes.Workspace>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = {
                                Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                                    Text(
                                        text = "请选择一个工作台页面",
                                        modifier = Modifier.padding(24.dp),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                            },
                        ),
                    ) {
                        WorkspacePane()
                    }
                    entry<Routes.Asr>(
                        metadata = ListDetailSceneStrategy.detailPane(),
                    ) {
                        AsrScreen(
                            vm = asrVm,
                            onStart = asrVm::startAsr,
                            onOpenAuth = {
                                scope.launch {
                                    val result = navigator.navigateForResult<AuthResult>(Routes.AuthLogin(from = "asr"))
                                    result?.let { asrMessages.add("登录成功：${it.email}") }
                                }
                            },
                            message = asrMessages.lastOrNull(),
                        )
                    }
                    addAuthRoutes(
                        vm = authVm,
                        onBack = navigator::popBackStack,
                        onNavigate = { route -> navigator.navigate(route) },
                        onReplaceTop = { route -> navigator.replaceTop(route) },
                        onAuthResult = { result ->
                            navigator.popWithResult(result)
                        },
                    )
                }
            )
        }
    }
}

@Composable
private fun WorkspacePane(
) {
    val navigator = LocalNavigator.current
    val selected = navigator.backStack.lastOrNull() as? AppRoute

    Column(
        modifier = Modifier.fillMaxHeight().padding(vertical = 12.dp, horizontal = 8.dp),
    ) {
        Text(
            text = "SubMaker",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        Button(
            onClick = { navigator.navigate(Routes.Asr(), singleTop = true) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        ) {
            val text = if (selected is Routes.Asr) "ASR（当前）" else "ASR 工作台"
            Text(text)
        }
        Button(
            onClick = { navigator.navigate(Routes.AuthLogin(from = "workspace"), singleTop = true) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(top = 8.dp),
        ) {
            val text = if (
                selected is Routes.AuthLogin ||
                selected is Routes.AuthVerify ||
                selected is Routes.AuthForgotPassword ||
                selected is Routes.AuthFindUsername ||
                selected is Routes.AuthBetaWelcome ||
                selected is Routes.AuthAccount
            ) {
                "账号（当前）"
            } else {
                "账号与权益"
            }
            Text(text)
        }
    }
}
