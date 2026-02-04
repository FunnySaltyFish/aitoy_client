package com.funny.submaker.core.kmp

import androidx.compose.runtime.ProvidableCompositionLocal

expect abstract class KMPContext

expect val LocalKMPContext: ProvidableCompositionLocal<KMPContext>

expect val appCtx: KMPContext

expect fun KMPContext.openUrl(url: String)
