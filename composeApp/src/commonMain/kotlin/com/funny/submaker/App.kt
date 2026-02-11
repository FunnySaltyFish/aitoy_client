package com.funny.submaker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.funny.submaker.core.navigation.NavigatorProvider
import com.funny.submaker.core.navigation.rememberNavigator
import com.funny.submaker.feature.asr.AsrScreen
import com.funny.submaker.feature.asr.AsrViewModel
import com.funny.submaker.feature.auth.AuthViewModel
import com.funny.submaker.navigation.AuthResult
import com.funny.submaker.navigation.Routes
import com.funny.submaker.navigation.addAuthRoutes
import com.funny.submaker.ui.theme.SubMakerTheme
import com.funny.submaker.workspace.WorkspaceScreen
import com.funny.submaker.workspace.WorkspaceViewModel
import kotlinx.coroutines.launch

@Composable
@Preview
fun App() {
    SubMakerTheme {
        val workspaceVm = remember { WorkspaceViewModel() }
        val asrVm = remember { AsrViewModel() }
        val authVm = remember { AuthViewModel() }
        val navigator = rememberNavigator(Routes.Workspace)
        val scope = rememberCoroutineScope()
        val asrMessages = remember { mutableStateListOf<String>() }

        NavigatorProvider(navigator) {
            NavDisplay(
                backStack = navigator.backStack,
                onBack = navigator::popBackStack,
                entryProvider = entryProvider {
                    entry<Routes.Workspace> {
                        WorkspaceScreen(
                            vm = workspaceVm,
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
                        LaunchedEffect(route.projectId) {
                            asrVm.bindProject(route.projectId)
                        }
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
                },
            )
        }
    }
}
