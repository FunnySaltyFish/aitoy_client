package com.funny.aitoy.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

@Composable
actual fun rememberKmpNavBackStack(vararg elements: NavKey): NavBackStack<NavKey> {
    return rememberNavBackStack(*elements)
}