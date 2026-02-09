@file:OptIn(androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi::class)

package com.funny.submaker.navigation

import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.EntryProviderScope
import com.funny.submaker.core.prefs.SubMakerPrefs
import com.funny.submaker.feature.auth.AuthViewModel
import com.funny.submaker.feature.auth.components.AuthMessageCard
import com.funny.submaker.feature.auth.screens.AccountScreen
import com.funny.submaker.feature.auth.screens.BetaWelcomeScreen
import com.funny.submaker.feature.auth.screens.LoginScreen
import com.funny.submaker.feature.auth.screens.VerifyCodeScreen

fun EntryProviderScope<NavKey>.addAuthRoutes(
    vm: AuthViewModel,
    onBack: () -> Boolean,
    onNavigate: (AppRoute) -> Unit,
    onReplaceTop: (AppRoute) -> Unit,
    onAuthResult: (AuthResult?) -> Unit,
) {
    entry<Routes.Auth>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { route ->
        onReplaceTop(Routes.AuthLogin(from = route.from))
    }

    entry<Routes.AuthLogin>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { route ->
        LoginScreen(
            vm = vm,
            onBack = {
                if (route.from == "asr") {
                    onAuthResult(null)
                } else {
                    onBack()
                }
            },
            onCodeSent = {
                onNavigate(Routes.AuthVerify(from = route.from))
            },
        )
        vm.infoMessage?.let { AuthMessageCard(text = it, isError = false) }
        vm.errorMessage?.let { AuthMessageCard(text = it, isError = true) }
    }

    entry<Routes.AuthVerify>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { route ->
        VerifyCodeScreen(
            vm = vm,
            onBack = { onBack() },
            onVerified = {
                onNavigate(Routes.AuthBetaWelcome(from = route.from))
            },
        )
        vm.infoMessage?.let { AuthMessageCard(text = it, isError = false) }
        vm.errorMessage?.let { AuthMessageCard(text = it, isError = true) }
    }

    entry<Routes.AuthBetaWelcome>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { route ->
        BetaWelcomeScreen(
            onEnterWorkspace = {
                if (route.from == "asr") {
                    onAuthResult(
                        AuthResult(
                            email = SubMakerPrefs.user.email,
                            proActive = SubMakerPrefs.user.entitlement.proActive,
                        ),
                    )
                } else {
                    onNavigate(Routes.AuthAccount(from = route.from))
                }
            },
        )
    }

    entry<Routes.AuthAccount>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { route ->
        if (!vm.isLoggedIn) {
            onReplaceTop(Routes.AuthLogin(from = route.from))
            return@entry
        }
        AccountScreen(
            vm = vm,
            onBack = {
                if (route.from == "asr") {
                    onAuthResult(null)
                } else {
                    onBack()
                }
            },
            onLogout = {
                onReplaceTop(Routes.AuthLogin(from = route.from))
            },
        )
        vm.infoMessage?.let { AuthMessageCard(text = it, isError = false) }
        vm.errorMessage?.let { AuthMessageCard(text = it, isError = true) }
    }
}
