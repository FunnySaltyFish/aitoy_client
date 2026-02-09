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
import com.funny.submaker.feature.auth.screens.FindUsernameScreen
import com.funny.submaker.feature.auth.screens.ForgotPasswordScreen
import com.funny.submaker.feature.auth.screens.LoginScreen
import com.funny.submaker.feature.auth.screens.VerifyCodeScreen
import com.funny.submaker.feature.auth.screens.VerifyPurpose

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
            onLoggedIn = {
                if (route.from == "asr") {
                    onAuthResult(
                        AuthResult(
                            email = SubMakerPrefs.user.email,
                            proActive = SubMakerPrefs.user.entitlement.proActive,
                        ),
                    )
                } else {
                    onReplaceTop(Routes.AuthAccount(from = route.from))
                }
            },
            onCodeSent = {
                onNavigate(Routes.AuthVerify(purpose = VerifyPurpose.Register.name, from = route.from))
            },
            onOpenForgotPassword = {
                onNavigate(Routes.AuthForgotPassword(from = route.from))
            },
            onOpenFindUsername = {
                onNavigate(Routes.AuthFindUsername(from = route.from))
            },
        )
        vm.infoMessage?.let { AuthMessageCard(text = it, isError = false) }
        vm.errorMessage?.let { AuthMessageCard(text = it, isError = true) }
    }

    entry<Routes.AuthForgotPassword>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { route ->
        ForgotPasswordScreen(
            vm = vm,
            onBack = { onBack() },
            onCodeSent = {
                onNavigate(Routes.AuthVerify(purpose = VerifyPurpose.ResetPassword.name, from = route.from))
            },
        )
        vm.infoMessage?.let { AuthMessageCard(text = it, isError = false) }
        vm.errorMessage?.let { AuthMessageCard(text = it, isError = true) }
    }

    entry<Routes.AuthFindUsername>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { route ->
        FindUsernameScreen(
            vm = vm,
            onBack = { onBack() },
            onCodeSent = {
                onNavigate(Routes.AuthVerify(purpose = VerifyPurpose.FindUsername.name, from = route.from))
            },
        )
        vm.infoMessage?.let { AuthMessageCard(text = it, isError = false) }
        vm.errorMessage?.let { AuthMessageCard(text = it, isError = true) }
    }

    entry<Routes.AuthVerify>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { route ->
        val purpose = runCatching { VerifyPurpose.valueOf(route.purpose) }.getOrDefault(VerifyPurpose.Register)
        VerifyCodeScreen(
            vm = vm,
            purpose = purpose,
            onBack = { onBack() },
            onVerified = { username ->
                when (purpose) {
                    VerifyPurpose.Register -> onNavigate(Routes.AuthBetaWelcome(from = route.from))
                    VerifyPurpose.FindUsername -> {
                        vm.infoMessage = "账号：${username ?: ""}"
                        onReplaceTop(Routes.AuthLogin(from = route.from))
                    }
                    VerifyPurpose.ResetPassword -> onReplaceTop(Routes.AuthLogin(from = route.from))
                }
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
