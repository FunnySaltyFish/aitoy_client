package com.funny.submaker.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable
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
}

@Serializable
data class AuthResult(
    val email: String,
    val proActive: Boolean,
)

val AppNavConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Routes.Workspace::class)
            subclass(Routes.Asr::class)
            subclass(Routes.Auth::class)
        }
    }
}
