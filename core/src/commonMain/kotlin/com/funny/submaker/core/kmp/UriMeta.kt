package com.funny.submaker.core.kmp

import com.eygraber.uri.Uri

/**
 * Uri 元信息（文件名/大小/mimeType）
 */
expect fun Uri.displayName(): String?

expect fun Uri.sizeBytes(): Long?

expect fun Uri.mimeType(): String?

