@file:OptIn(androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi::class)

package com.funny.submaker.navigation

import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.funny.submaker.feature.asr.AsrScreen

fun EntryProviderScope<NavKey>.addAsrRoutes(
    message: String?,
    onBack: () -> Boolean,
    onOpenAuth: () -> Unit,
) {
    entry<Routes.Asr>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { route ->
        AsrScreen(
            projectId = route.projectId,
            onOpenAuth = onOpenAuth,
            onCancel = { onBack() },
            message = message,
        )
    }
}
