package com.funny.submaker.navigation

import androidx.compose.runtime.Stable
import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable
@Stable
sealed interface AppRoute : NavKey

object Routes {
    @Serializable
    data object Workspace : AppRoute

    @Serializable
    data class Asr(
        val autoStart: Boolean = false,
    ) : AppRoute

    @Serializable
    data class Auth(
        val from: String? = null,
    ) : AppRoute

    @Serializable
    data class AuthLogin(
        val from: String? = null,
    ) : AppRoute

    @Serializable
    data class AuthVerify(
        val purpose: String,
        val from: String? = null,
    ) : AppRoute

    @Serializable
    data class AuthForgotPassword(
        val from: String? = null,
    ) : AppRoute

    @Serializable
    data class AuthFindUsername(
        val from: String? = null,
    ) : AppRoute

    @Serializable
    data class AuthBetaWelcome(
        val from: String? = null,
    ) : AppRoute

    @Serializable
    data class AuthAccount(
        val from: String? = null,
    ) : AppRoute
}

@Serializable
data class AuthResult(
    val email: String,
    val proActive: Boolean,
)