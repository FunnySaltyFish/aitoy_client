package com.funny.submaker.core.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel

val LocalNavigator = compositionLocalOf<Navigator> {
    error("LocalNavigator 未提供，请在 NavigatorProvider 中使用")
}

@Stable
class Navigator internal constructor(
    val backStack: NavBackStack<NavKey>,
) {
    private val pendingRequests = ArrayDeque<PendingRequest>()

    val canGoBack: Boolean
        get() = backStack.size > 1

    fun navigate(route: NavKey, singleTop: Boolean = false) {
        if (singleTop && backStack.lastOrNull() == route) return
        backStack.add(route)
    }

    fun replaceTop(route: NavKey) {
        backStack.removeLastOrNull()
        backStack.add(route)
    }

    fun popBackStack(): Boolean {
        val oldSize = backStack.size
        val popped = backStack.removeLastOrNull() != null
        if (popped) resolveByPoppedDepth(oldSize, result = null)
        return popped
    }

    fun popWithResult(result: Any? = null): Boolean {
        val oldSize = backStack.size
        val popped = backStack.removeLastOrNull() != null
        if (popped) resolveByPoppedDepth(oldSize, result = result)
        return popped
    }

    suspend fun <T> navigateForResult(route: NavKey): T? {
        val requestId = "result_${resultSeed++}"
        val expectedDepth = backStack.size + 1
        pendingRequests.addLast(PendingRequest(requestId, expectedDepth))
        ResultStore.prepare(requestId)
        navigate(route)
        return ResultStore.await(requestId)
    }

    private fun resolveByPoppedDepth(poppedDepth: Int, result: Any?) {
        val request = pendingRequests.lastOrNull() ?: return
        if (request.expectedDepth != poppedDepth) return
        pendingRequests.removeLastOrNull()
        ResultStore.emit(request.requestId, result)
    }

    private data class PendingRequest(
        val requestId: String,
        val expectedDepth: Int,
    )

    private companion object {
        private var resultSeed: Long = 1L
    }
}

@Composable
fun rememberNavigator(
    vararg elements: NavKey,
): Navigator {
    val backStack = rememberKmpNavBackStack(*elements)
    return remember(backStack) { Navigator(backStack) }
}

@Composable
fun NavigatorProvider(
    navigator: Navigator,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalNavigator provides navigator) {
        content()
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun <T : Any> rememberListDetailStrategy(): ListDetailSceneStrategy<T> {
    val windowAdaptiveInfo = currentWindowAdaptiveInfo()
    val directive = remember(windowAdaptiveInfo) {
        calculatePaneScaffoldDirective(windowAdaptiveInfo)
            .copy(horizontalPartitionSpacerSize = 0.dp)
    }
    return rememberListDetailSceneStrategy(directive = directive)
}

private object ResultStore {
    private val channels = mutableMapOf<String, Channel<Any?>>()

    fun prepare(key: String) {
        channels.remove(key)?.close()
        channels[key] = Channel(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }

    suspend fun <T> await(key: String): T? {
        @Suppress("UNCHECKED_CAST")
        val result = channels[key]?.receiveCatching()?.getOrNull() as? T
        channels.remove(key)?.close()
        return result
    }

    fun emit(key: String, result: Any?) {
        val channel = channels[key] ?: Channel<Any?>(
            capacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        ).also { channels[key] = it }
        channel.trySend(result)
    }
}

/**
 * 跨平台的、无需 conf 参数的包装
 */
@Composable
expect fun rememberKmpNavBackStack(vararg elements: NavKey): NavBackStack<NavKey>