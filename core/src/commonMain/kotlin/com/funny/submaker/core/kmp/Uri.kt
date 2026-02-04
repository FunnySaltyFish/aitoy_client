package com.funny.submaker.core.kmp

import com.eygraber.uri.Uri

expect fun Uri.readText(): String

expect fun Uri.readBytes(): ByteArray

expect fun Uri.writeText(text: String)

