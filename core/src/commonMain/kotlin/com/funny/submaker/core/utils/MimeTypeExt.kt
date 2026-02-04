package com.funny.submaker.core.utils

/**
 * 尝试从 uri / filepath 推断 mimeType；失败返回 null
 */
expect fun String.resolveMimeType(): String?

