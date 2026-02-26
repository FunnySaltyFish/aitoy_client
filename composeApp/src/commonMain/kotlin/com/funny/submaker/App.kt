package com.funny.submaker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.funny.submaker.core.navigation.NavigatorProvider
import com.funny.submaker.core.navigation.rememberNavigator
import com.funny.submaker.feature.asr.AsrScreen
import com.funny.submaker.feature.auth.AuthViewModel
import com.funny.submaker.navigation.AuthResult
import com.funny.submaker.navigation.Routes
import com.funny.submaker.navigation.addAuthRoutes
import com.funny.submaker.ui.theme.SubMakerTheme
import com.funny.submaker.ui.toast.GlobalToastOverlay
import com.funny.submaker.workspace.WorkspaceScreen
import kotlinx.coroutines.launch

@Composable
@Preview
fun App() {
    SubMakerTheme {
        val authVm = viewModel { AuthViewModel() }
        val navigator = rememberNavigator(Routes.Workspace)
        val scope = rememberCoroutineScope()
        val asrMessages = remember { mutableStateListOf<String>() }

        NavigatorProvider(navigator) {
            Box(modifier = Modifier.fillMaxSize()) {
                NavDisplay(
                    backStack = navigator.backStack,
                    onBack = navigator::popBackStack,
                    entryProvider = entryProvider {
                        entry<Routes.Workspace> {
                            WorkspaceScreen(
                                onOpenAsr = { navigator.navigate(Routes.Asr(), singleTop = true) },
                                onOpenProjectAsr = { projectId ->
                                    navigator.navigate(
                                        Routes.Asr(projectId = projectId),
                                        singleTop = true
                                    )
                                },
                                onOpenAccount = {
                                    navigator.navigate(
                                        Routes.AuthLogin(from = "workspace"),
                                        singleTop = true
                                    )
                                },
                            )
                        }
                        entry<Routes.Asr> { route ->
                            AsrScreen(
                                projectId = route.projectId,
                                onOpenAuth = {
                                    scope.launch {
                                        val result = navigator.navigateForResult<AuthResult>(
                                            Routes.AuthLogin(from = "asr")
                                        )
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
                    },
                )
                GlobalToastOverlay(
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
    }
}
